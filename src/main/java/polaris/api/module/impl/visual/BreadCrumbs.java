package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.module.impl.visual.Hud;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BreadCrumbs extends Module {

    private final BooleanSetting throughWalls = register(new BooleanSetting("ThroughWalls", "Отрисовывать сквозь стены.", true));
    private final NumberSetting limit = register(new NumberSetting("ListLimit", "Лимит точек в истории.", 1000.0, 10.0, 99999.0, 10.0));
    private final ModeSetting lmode = register(new ModeSetting("ColorMode", "Режим цвета.", "Sync", "Custom", "Sync"));
    private final ColorSetting color = register(new ColorSetting("Color", "Кастомный цвет линии.", new Color(3649978)));

    private final List<Vec3> positions = new CopyOnWriteArrayList<>();

    public BreadCrumbs() {
        super("BreadCrumbs", "Оставляет след из линий за игроком.", ModuleCategory.VISUAL);
        color.visibleWhen(() -> lmode.is("Custom"));
    }

    @Override
    protected void onDisable() {
        positions.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            positions.clear();
            return;
        }

        if (positions.size() > limit.getValue().intValue()) {
            positions.remove(0);
        }

        // Корректно запоминаем текущую позицию ног игрока в мире
        positions.add(new Vec3(mc.player.getX(), mc.player.getBoundingBox().minY, mc.player.getZ()));
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (positions.size() < 2 || mc.player == null) {
            return;
        }

        float partial = event.getPartialTicks();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        // Берем пайплайн твоего клиента (GLOW идеально подходит для красивых неоновых линий)
        VertexConsumer glowBuffer = provider.getBuffer(throughWalls.getValue() ? ClientPipelines.CRYSTAL_GLOW : ClientPipelines.CRYSTAL_FILLED);
        if (glowBuffer == null) return;

        stack.pushPose();

        for (int i = 1; i < positions.size(); i++) {
            Vec3 vec1 = positions.get(i - 1);
            Vec3 vec2 = positions.get(i);

            if (vec1 != null && vec2 != null) {
                Color c = lmode.is("Sync") ? Hud.themeAccent() : color.getRawValue();

                int alpha = c.getAlpha();
                // Делаем плавное затухание начала следа, как в ThunderHack
                if (i < 10) {
                    alpha = (int) (alpha * (i / 10f));
                }

                int finalColor = ColorUtil.withAlpha(c.getRGB(), alpha);
                // Переводим глобальные координаты точек в локальные относительно камеры
                float x1 = (float) (vec1.x - cam.x);
                float y1 = (float) (vec1.y - cam.y);
                float z1 = (float) (vec1.z - cam.z);

                float x2 = (float) (vec2.x - cam.x);
                float y2 = (float) (vec2.y - cam.y);
                float z2 = (float) (vec2.z - cam.z);

                // Отрисовываем 3D-линии через вершины твоего VertexConsumer без единой ошибки
                glowBuffer.addVertex(stack.last().pose(), x1, y1, z1)
                        .setColor(finalColor);
                glowBuffer.addVertex(stack.last().pose(), x2, y2, z2)
                        .setColor(finalColor);
            }
        }

        stack.popPose();

        // Вызываем закрытие батча рендеринга Polaris, как в примере ChinaHat
        provider.endBatch(ClientPipelines.CRYSTAL_GLOW);
        provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
    }
}
