package polaris.api.module.impl.visual.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.IMinecraft;
import polaris.api.settings.Setting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public final class TotemAngelEffect implements IMinecraft {

    private static final int TOTEM_EVENT_ID = 35;
    private static final float UNIT = 0.0625f;
    private static final int MAX_GHOSTS = 8;

    private static final float[][] WING_SHAPE = {
            {0.06f, 0.08f, 0.95f},
            {0.22f, 0.38f, 0.88f},
            {0.48f, 0.78f, 0.72f},
            {0.78f, 0.42f, 0.62f},
            {1.06f, 0.58f, 0.50f},
            {1.28f, 0.22f, 0.38f},
            {1.18f, -0.08f, 0.34f},
            {1.32f, -0.48f, 0.26f},
            {1.02f, -0.38f, 0.28f},
            {0.96f, -0.86f, 0.18f},
            {0.66f, -0.62f, 0.22f},
            {0.42f, -1.08f, 0.14f},
            {0.18f, -0.48f, 0.22f},
            {0.08f, -0.12f, 0.55f}
    };

    
    private final BooleanSetting enabled = new BooleanSetting(
            "Totem Angel", "Angel ghost with wings on totem pop.", false);

    private final NumberSetting riseHeight = new NumberSetting(
            "Angel Rise Height", "How high the ghost floats.", 3.2, 0.5, 6.0, 0.1);
    private final NumberSetting lifetime = new NumberSetting(
            "Angel Lifetime", "Ghost lifetime in seconds.", 3.2, 0.5, 7.0, 0.1);
    private final NumberSetting wingSize = new NumberSetting(
            "Angel Wing Size", "Wing scale.", 1.15, 0.5, 2.0, 0.05);
    private final ColorSetting color = new ColorSetting(
            "Angel Color", "Ghost color.", new Color(127, 242, 255, 200));

    private final List<Ghost> ghosts = new ArrayList<>();

    public TotemAngelEffect() {
        riseHeight.visibleWhen(enabled::getValue);
        lifetime.visibleWhen(enabled::getValue);
        wingSize.visibleWhen(enabled::getValue);
        color.visibleWhen(enabled::getValue);

    }

    
    public List<Setting<?>> settings() {
        return List.of(enabled, riseHeight, lifetime, wingSize, color);
    }

    public boolean isOn() {
        return enabled.getValue();
    }

    public void onDisable() {
        ghosts.clear();
    }

    public void onPacket(PacketEvent event) {
        try {
            if (!event.isReceive() || mc.level == null) {
                return;
            }
            if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet)) {
                return;
            }
            if (packet.getEventId() != TOTEM_EVENT_ID) {
                return;
            }
            Entity entity = packet.getEntity(mc.level);
            if (!(entity instanceof Player player) || !player.isAlive()) {
                return;
            }
            
            if (ghosts.size() >= MAX_GHOSTS) {
                ghosts.remove(0);
            }
            float bodyYaw = Mth.rotLerp(1.0f, player.yBodyRotO, player.yBodyRot);
            ghosts.add(new Ghost(
                    player.getX(), player.getY(), player.getZ(),
                    bodyYaw,
                    System.currentTimeMillis()
            ));
        } catch (Throwable ignored) {
            
        }
    }

    public void onWorldRender(WorldRenderEvent event) {
        if (ghosts.isEmpty() || mc.player == null || mc.level == null) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            float lifeMs = lifetime.getFloat() * 1000.0f;

            Iterator<Ghost> it = ghosts.iterator();
            while (it.hasNext()) {
                if ((now - it.next().startTime) >= lifeMs) {
                    it.remove();
                }
            }
            if (ghosts.isEmpty()) {
                return;
            }

            Vec3 cam = mc.gameRenderer.getMainCamera().position();
            PoseStack stack = event.getStack();
            MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();
            int baseColor = color.getValue().getRGB();

            for (Ghost ghost : ghosts) {
                float progress = (now - ghost.startTime) / lifeMs;
                if (progress < 0f || progress >= 1f) {
                    continue;
                }
                renderGhost(stack, provider, ghost, cam, baseColor, progress);
            }

            
            provider.endBatch(ClientPipelines.CRYSTAL_GLOW);
            provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
        } catch (Throwable t) {
            
            ghosts.clear();
        }
    }

    private void renderGhost(PoseStack stack, MultiBufferSource.BufferSource provider,
                             Ghost ghost, Vec3 cam, int baseColor, float progress) {
        double rise = riseHeight.getValue() * easeOutCubic(Math.min(progress, 0.75f) / 0.75f);
        float alpha = ghostAlpha(progress);
        if (alpha <= 0.01f) {
            return;
        }

        int col = ColorUtil.withAlpha(baseColor, (int) (alpha * 255.0f));
        int wingCol = ColorUtil.withAlpha(baseColor, (int) (alpha * 210.0f));
        float flap = Mth.sin(progress * 14f) * 16f * (1f - progress * 0.4f);
        float open = 18f + flap + progress * 22f;
        float spin = progress * 12f;
        float wScale = wingSize.getFloat() * (0.85f + 0.2f * (1f - progress));

        stack.pushPose();
        stack.translate(ghost.x - cam.x, ghost.y - cam.y + rise, ghost.z - cam.z);
        stack.mulPose(Axis.YP.rotationDegrees(180.0f - ghost.bodyYaw + spin));
        stack.scale(-1.0f, -1.0f, 1.0f);
        stack.translate(0.0, -1.501, 0.0);

        float split = splitProgress(progress);
        float headRise = -7.0f * split;
        float armLift = -4.0f * split;
        float armSpread = 6.0f * split;
        float legDrop = 4.0f * split;
        float legSpread = 2.5f * split;

        
        VertexConsumer body = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        part(stack, body, col, 0.0f, 0.0f, 0.0f, 0.0f, headRise, 0.0f, -4, -8, -4, 4, 0, 4);
        part(stack, body, col, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -4, 0, -2, 4, 12, 2);
        part(stack, body, col, -5.0f, 2.0f, 0.0f, -armSpread, armLift, 0.0f, -3, -2, -2, 1, 10, 2);
        part(stack, body, col, 5.0f, 2.0f, 0.0f, armSpread, armLift, 0.0f, -1, -2, -2, 3, 10, 2);
        part(stack, body, col, -1.9f, 12.0f, 0.0f, -legSpread, legDrop, 0.0f, -2, 0, -2, 2, 12, 2);
        part(stack, body, col, 1.9f, 12.0f, 0.0f, legSpread, legDrop, 0.0f, -2, 0, -2, 2, 12, 2);

        
        stack.pushPose();
        stack.translate(0.0f, 4.0f * UNIT, 2.5f * UNIT);
        stack.scale(-1f, -1f, 1f);
        stack.scale(wScale, wScale, wScale);

        int glow = ColorUtil.lerpColor(wingCol, 0xFFFFFFFF, 0.4f);
        int core = ColorUtil.lerpColor(wingCol, 0xFFFFFFFF, 0.6f);
        wingSide(stack, provider, -1f, open, flap, wingCol, glow, core, alpha);
        wingSide(stack, provider, 1f, open, flap, wingCol, glow, core, alpha);

        stack.popPose();
        stack.popPose();
    }

    private void wingSide(PoseStack stack, MultiBufferSource.BufferSource provider, float side,
                          float open, float flap, int base, int glow, int core, float alpha) {
        stack.pushPose();
        stack.translate(side * 0.07f, 0.0f, 0.04f);
        stack.mulPose(Axis.YP.rotationDegrees(side * open));
        stack.mulPose(Axis.ZP.rotationDegrees(side * (-10f + flap * 0.15f)));
        stack.mulPose(Axis.XP.rotationDegrees(-4f + flap * 0.2f * side));

        VertexConsumer glowVc = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);
        membrane(stack, glowVc, side, 1.26f,
                ColorUtil.withAlpha(glow, (int) (55 * alpha)),
                ColorUtil.withAlpha(glow, 0));
        glowVc = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);
        membrane(stack, glowVc, side, 0.90f,
                ColorUtil.withAlpha(core, (int) (65 * alpha)),
                ColorUtil.withAlpha(core, 0));

        VertexConsumer fill = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        membrane(stack, fill, side, 1.0f,
                ColorUtil.withAlpha(base, (int) (200 * alpha)),
                ColorUtil.withAlpha(base, (int) (12 * alpha)));

        stack.popPose();
    }

    private void membrane(PoseStack stack, VertexConsumer vc, float side, float scale, int rootColor, int edgeColor) {
        PoseStack.Pose pose = stack.last();
        for (int i = 0; i < WING_SHAPE.length; i++) {
            float[] cur = WING_SHAPE[i];
            float[] next = WING_SHAPE[(i + 1) % WING_SHAPE.length];
            int cCur = ColorUtil.withAlpha(edgeColor, Mth.clamp((int) (ColorUtil.getAlpha(edgeColor) * cur[2]), 0, 255));
            int cNext = ColorUtil.withAlpha(edgeColor, Mth.clamp((int) (ColorUtil.getAlpha(edgeColor) * next[2]), 0, 255));

            vc.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(rootColor);
            vc.addVertex(pose, side * cur[0] * scale, cur[1] * scale, 0.0f).setColor(cCur);
            vc.addVertex(pose, side * next[0] * scale, next[1] * scale, 0.0f).setColor(cNext);
            vc.addVertex(pose, side * next[0] * scale, next[1] * scale, 0.0f).setColor(cNext);
        }
    }

    private void part(PoseStack stack, VertexConsumer vc, int color,
                      float pivotX, float pivotY, float pivotZ,
                      float offX, float offY, float offZ,
                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        stack.pushPose();
        stack.translate((pivotX + offX) * UNIT, (pivotY + offY) * UNIT, (pivotZ + offZ) * UNIT);
        box(stack.last(), vc, color,
                minX * UNIT, minY * UNIT, minZ * UNIT,
                maxX * UNIT, maxY * UNIT, maxZ * UNIT);
        stack.popPose();
    }

    private void box(PoseStack.Pose pose, VertexConsumer vc, int c,
                     float x1, float y1, float z1, float x2, float y2, float z2) {
        face(pose, vc, c, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        face(pose, vc, c, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1);
        face(pose, vc, c, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        face(pose, vc, c, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2);
        face(pose, vc, c, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2);
        face(pose, vc, c, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
    }

    private void face(PoseStack.Pose pose, VertexConsumer vc, int c,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4) {
        vc.addVertex(pose, x1, y1, z1).setColor(c);
        vc.addVertex(pose, x2, y2, z2).setColor(c);
        vc.addVertex(pose, x3, y3, z3).setColor(c);
        vc.addVertex(pose, x4, y4, z4).setColor(c);
    }

    private double easeOutCubic(double v) {
        double c = Mth.clamp((float) v, 0.0f, 1.0f);
        return 1.0 - Math.pow(1.0 - c, 3);
    }

    private float ghostAlpha(float progress) {
        float c = Mth.clamp(progress, 0.0f, 1.0f);
        float fadeIn = easeOut(Mth.clamp(c / 0.12f, 0.0f, 1.0f));
        float fadeOut = 1.0f - easeOut(Mth.clamp((c - 0.40f) / 0.60f, 0.0f, 1.0f));
        return 0.78f * fadeIn * fadeOut;
    }

    private float splitProgress(float progress) {
        return easeOut(Mth.clamp((progress - 0.28f) / 0.55f, 0.0f, 1.0f));
    }

    private float easeOut(float v) {
        return 1.0f - (float) Math.pow(1.0f - v, 3.0f);
    }

    private static final class Ghost {
        final double x, y, z;
        final float bodyYaw;
        final long startTime;

        Ghost(double x, double y, double z, float bodyYaw, long startTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bodyYaw = bodyYaw;
            this.startTime = startTime;
        }
    }
}
