package polaris.api.module.impl.visual;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.Font;
import org.joml.Matrix4f;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.chat.ChatMessage;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakHighLight extends Module {

    private final ModeSetting mode = register(new ModeSetting("Режим", "Анимация анимации ломания.", "Shrink", "Grow", "Shrink", "Static"));

    private final ColorSetting color = register(new ColorSetting("Цвет Заливки 1", "Начальный цвет заливки.", new Color(253, 0, 0, 144)));
    private final ColorSetting color2 = register(new ColorSetting("Цвет Заливки 2", "Конечный цвет заливки.", new Color(253, 0, 0, 255)));
    private final ColorSetting ocolor = register(new ColorSetting("Цвет Обводки 1", "Начальный цвет обводки.", new Color(63, 253, 0, 144)));
    private final ColorSetting ocolor2 = register(new ColorSetting("Цвет Обводки 2", "Конечный цвет обводки.", new Color(46, 253, 0, 255)));
    private final ColorSetting textColor = register(new ColorSetting("Цвет Текста", "Цвет никнейма над блоком.", new Color(255, 255, 255, 255)));

    private final NumberSetting lineWidth = register(new NumberSetting("Толщина линий", "Ширина обводки.", 2.0, 0.1, 5.0, 0.1));
    private final BooleanSetting otherPlayer = register(new BooleanSetting("Другие игроки", "Показывать ломание других игроков.", true));
    private final BooleanSetting chatDebug = register(new BooleanSetting("Debug to Chat", "Выводить логи ломания в чат игры.", false));

    // Наш собственный выделенный кэш пакетов ломания, работающий в обход ванильного LevelRenderer
    private final Map<Integer, BreakingInfo> otherBreaking = new ConcurrentHashMap<>();

    private record BreakingInfo(BlockPos pos, int stage, long lastUpdate) {}

    public BreakHighLight() {
        super("BreakHighLight", "Подсвечивает и анимирует ломаемые blocks.", ModuleCategory.VISUAL);
    }

    @Override
    protected void onDisable() {
        otherBreaking.clear();
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        if (mc.level == null || !otherPlayer.getValue()) return;

        if (event.isReceive() && event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            int id = packet.getId();
            BlockPos pos = packet.getPos();
            int stage = packet.getProgress();

            // Если стадия сбросилась или превысила лимит, удаляем игрока из кэша
            if (stage >= 10 || stage < 0) {
                otherBreaking.remove(id);
            } else {
                // Стабильный вывод логов в чат при перехвате пакета самого первого удара
                if (chatDebug.getValue() && !otherBreaking.containsKey(id)) {
                    Entity entity = mc.level.getEntity(id);
                    String name = (entity instanceof Player p) ? p.getName().getString() : "ID " + id;
                    ChatMessage.brandmessage("Игрок " + name + " начал ломать блок на " + pos);
                }
                otherBreaking.put(id, new BreakingInfo(pos, stage, System.currentTimeMillis()));
            }
        }
    }
    @SubscribeEvent
    private void onRender3D(WorldRenderEvent event) {
        if (mc.level == null || mc.player == null) return;

        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().entity().getPosition(event.getPartialTicks());

        VertexConsumer filledBuffer = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        VertexConsumer glowBuffer = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);

        // 1. Ломание других игроков: рендерим строго из нашего сохраненного кэша пакетов
        if (otherPlayer.getValue() && !otherBreaking.isEmpty()) {
            long now = System.currentTimeMillis();
            // Чистим кэш от старых пакетов, если обновлений от сервера не было дольше 5 секунд
            otherBreaking.entrySet().removeIf(entry -> now - entry.getValue().lastUpdate() > 5000L);

            otherBreaking.forEach((id, info) -> {
                Entity entity = mc.level.getEntity(id);
                if (entity instanceof Player switcher && switcher != mc.player) {
                    BlockPos pos = info.pos();
                    int stage = info.stage();

                    float noom;
                    switch (mode.getValue()) {
                        case "Grow" -> noom = Mth.clamp(stage / 9f, 0.05f, 1f);
                        case "Shrink" -> noom = 1f - Mth.clamp(stage / 9f, 0f, 0.95f);
                        default -> noom = 1f;
                    }

                    int cFill = ColorUtil.lerpColor(color.getRawValue().getRGB(), color2.getRawValue().getRGB(), noom);
                    int cGlow = ColorUtil.lerpColor(ocolor.getRawValue().getRGB(), ocolor2.getRawValue().getRGB(), noom);

                    renderBreakingBox(stack, filledBuffer, glowBuffer, pos, noom, cFill, cGlow, cam);
                    render3DText(stack, provider, switcher.getName().getString(), pos, cam);
                }
            });
        }

        if (mc.gameMode != null && mc.gameMode.isDestroying() && mc.hitResult != null && mc.hitResult instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            if (!mc.level.getBlockState(pos).isAir()) {
                float progress = mc.gameMode.getDestroyStage() / 9f;

                float noom;
                switch (mode.getValue()) {
                    case "Grow" -> noom = Mth.clamp(progress, 0.05f, 1f);
                    case "Shrink" -> noom = 1f - Mth.clamp(progress, 0f, 0.95f);
                    default -> noom = 1f;
                }

                int cFill = ColorUtil.lerpColor(color.getRawValue().getRGB(), color2.getRawValue().getRGB(), noom);
                int cGlow = ColorUtil.lerpColor(ocolor.getRawValue().getRGB(), ocolor2.getRawValue().getRGB(), noom);

                renderBreakingBox(stack, filledBuffer, glowBuffer, pos, noom, cFill, cGlow, cam);
            }
        }

        // Выталкиваем вершины в кастомные кристал-пайплайны Polaris
        provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
        provider.endBatch(ClientPipelines.CRYSTAL_GLOW);
        provider.endBatch();
    }
    private void renderBreakingBox(PoseStack stack, VertexConsumer filled, VertexConsumer glow, BlockPos pos, float size, int cFill, int cGlow, Vec3 cam) {
        double bx = pos.getX() - cam.x;
        double by = pos.getY() - cam.y;
        double bz = pos.getZ() - cam.z;

        double offset = (1.0 - size) * 0.5;
        float minX = (float) (bx + offset);
        float minY = (float) (by + offset);
        float minZ = (float) (bz + offset);
        float maxX = (float) (minX + size);
        float maxY = (float) (minY + size);
        float maxZ = (float) (minZ + size);

        stack.pushPose();
        Matrix4f matrix = stack.last().pose();

        // Заливка граней куба
        filled.addVertex(matrix, minX, minY, minZ).setColor(cFill); filled.addVertex(matrix, maxX, minY, minZ).setColor(cFill);
        filled.addVertex(matrix, maxX, minY, maxZ).setColor(cFill); filled.addVertex(matrix, minX, minY, maxZ).setColor(cFill);
        filled.addVertex(matrix, minX, maxY, minZ).setColor(cFill); filled.addVertex(matrix, minX, maxY, maxZ).setColor(cFill);
        filled.addVertex(matrix, maxX, maxY, maxZ).setColor(cFill); filled.addVertex(matrix, maxX, maxY, minZ).setColor(cFill);
        filled.addVertex(matrix, minX, minY, minZ).setColor(cFill); filled.addVertex(matrix, minX, maxY, minZ).setColor(cFill);
        filled.addVertex(matrix, maxX, maxY, minZ).setColor(cFill); filled.addVertex(matrix, maxX, minY, minZ).setColor(cFill);
        filled.addVertex(matrix, minX, minY, maxZ).setColor(cFill); filled.addVertex(matrix, maxX, minY, maxZ).setColor(cFill);
        filled.addVertex(matrix, maxX, maxY, maxZ).setColor(cFill); filled.addVertex(matrix, minX, maxY, maxZ).setColor(cFill);
        filled.addVertex(matrix, minX, minY, minZ).setColor(cFill); filled.addVertex(matrix, minX, minY, maxZ).setColor(cFill);
        filled.addVertex(matrix, minX, maxY, maxZ).setColor(cFill); filled.addVertex(matrix, minX, maxY, minZ).setColor(cFill);
        filled.addVertex(matrix, maxX, minY, minZ).setColor(cFill); filled.addVertex(matrix, maxX, maxY, minZ).setColor(cFill);
        filled.addVertex(matrix, maxX, maxY, maxZ).setColor(cFill); filled.addVertex(matrix, maxX, minY, maxZ).setColor(cFill);

        // Обводка ребер куба
        glow.addVertex(matrix, minX, minY, minZ).setColor(cGlow); glow.addVertex(matrix, maxX, minY, minZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, minY, minZ).setColor(cGlow); glow.addVertex(matrix, maxX, minY, maxZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, minY, maxZ).setColor(cGlow); glow.addVertex(matrix, minX, minY, maxZ).setColor(cGlow);
        glow.addVertex(matrix, minX, minY, maxZ).setColor(cGlow); glow.addVertex(matrix, minX, minY, minZ).setColor(cGlow);
        glow.addVertex(matrix, minX, maxY, minZ).setColor(cGlow); glow.addVertex(matrix, maxX, maxY, minZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, maxY, minZ).setColor(cGlow); glow.addVertex(matrix, maxX, maxY, maxZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, maxY, maxZ).setColor(cGlow); glow.addVertex(matrix, minX, maxY, maxZ).setColor(cGlow);
        glow.addVertex(matrix, minX, maxY, maxZ).setColor(cGlow); glow.addVertex(matrix, minX, maxY, minZ).setColor(cGlow);
        glow.addVertex(matrix, minX, minY, minZ).setColor(cGlow); glow.addVertex(matrix, minX, maxY, minZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, minY, minZ).setColor(cGlow); glow.addVertex(matrix, maxX, maxY, minZ).setColor(cGlow);
        glow.addVertex(matrix, maxX, minY, maxZ).setColor(cGlow); glow.addVertex(matrix, maxX, maxY, maxZ).setColor(cGlow);
        glow.addVertex(matrix, minX, minY, maxZ).setColor(cGlow); glow.addVertex(matrix, minX, maxY, maxZ).setColor(cGlow);

        stack.popPose();
    }
    private void render3DText(PoseStack stack, MultiBufferSource.BufferSource provider, String text, BlockPos pos, Vec3 cam) {
        stack.pushPose();
        stack.translate(pos.getX() - cam.x + 0.5, pos.getY() - cam.y + 1.2, pos.getZ() - cam.z + 0.5);
        stack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        stack.scale(-0.02f, -0.02f, 0.02f);

        var font = mc.font;
        float textW = font.width(text);
        font.drawInBatch(
                text,
                -textW / 2f,
                0,
                textColor.getRawValue().getRGB(),
                false,
                stack.last().pose(),
                provider,
                Font.DisplayMode.NORMAL,
                0,
                15728880
        );

        stack.popPose();
    }
}
