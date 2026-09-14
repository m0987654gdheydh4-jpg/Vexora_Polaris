package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;
import polaris.utils.render.world.glow.Glow3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public final class PrismESP extends Module {
    private static PrismESP instance;

    private static final float[][] OCT = {
            {0, 1.15f, 0}, {0, -1.15f, 0},
            {0, 0, 1}, {0, 0, -1},
            {-1, 0, 0}, {1, 0, 0}
    };

    private static final int[][] EDGES = {
            {0, 2}, {0, 3}, {0, 4}, {0, 5},
            {1, 2}, {1, 3}, {1, 4}, {1, 5},
            {2, 5}, {5, 3}, {3, 4}, {4, 2}
    };

    private static final int[][] FACES = {
            {0, 2, 5}, {0, 5, 3}, {0, 3, 4}, {0, 4, 2},
            {1, 5, 2}, {1, 3, 5}, {1, 4, 3}, {1, 2, 4}
    };

    
    private enum Limb { HEAD, BODY, ARM_R, ARM_L, LEG_R, LEG_L }

    private static final class CrystalDef {
        final float ox, oy, oz;
        final float sx, sy, sz;
        final Limb limb;
        final int phase;

        CrystalDef(float ox, float oy, float oz, float sx, float sy, float sz, Limb limb, int phase) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.limb = limb;
            this.phase = phase;
        }
    }

    private static final List<CrystalDef> BODY_CRYSTALS = buildBodyMap();

    private static List<CrystalDef> buildBodyMap() {
        List<CrystalDef> list = new ArrayList<>();
        int p = 0;

        
        list.add(new CrystalDef(0f, 1.72f, 0f, 0.20f, 0.22f, 0.20f, Limb.HEAD, p++));
        list.add(new CrystalDef(0.12f, 1.62f, 0.06f, 0.10f, 0.11f, 0.10f, Limb.HEAD, p++));
        list.add(new CrystalDef(-0.12f, 1.62f, 0.06f, 0.10f, 0.11f, 0.10f, Limb.HEAD, p++));
        list.add(new CrystalDef(0f, 1.58f, -0.10f, 0.11f, 0.12f, 0.11f, Limb.HEAD, p++));

        
        list.add(new CrystalDef(0f, 1.28f, 0.08f, 0.16f, 0.14f, 0.12f, Limb.BODY, p++));
        list.add(new CrystalDef(0f, 1.10f, 0.10f, 0.18f, 0.16f, 0.13f, Limb.BODY, p++));
        list.add(new CrystalDef(0f, 0.92f, 0.08f, 0.17f, 0.14f, 0.12f, Limb.BODY, p++));
        list.add(new CrystalDef(0f, 0.76f, 0.04f, 0.15f, 0.12f, 0.11f, Limb.BODY, p++));
        list.add(new CrystalDef(0.16f, 1.15f, 0.02f, 0.10f, 0.12f, 0.09f, Limb.BODY, p++));
        list.add(new CrystalDef(-0.16f, 1.15f, 0.02f, 0.10f, 0.12f, 0.09f, Limb.BODY, p++));
        list.add(new CrystalDef(0.14f, 0.95f, 0.02f, 0.09f, 0.11f, 0.08f, Limb.BODY, p++));
        list.add(new CrystalDef(-0.14f, 0.95f, 0.02f, 0.09f, 0.11f, 0.08f, Limb.BODY, p++));
        list.add(new CrystalDef(0f, 1.18f, -0.10f, 0.12f, 0.14f, 0.10f, Limb.BODY, p++));
        list.add(new CrystalDef(0f, 0.98f, -0.09f, 0.12f, 0.13f, 0.10f, Limb.BODY, p++));

        
        list.add(new CrystalDef(-0.36f, 1.38f, 0f, 0.11f, 0.12f, 0.11f, Limb.ARM_R, p++));
        list.add(new CrystalDef(-0.38f, 1.22f, 0f, 0.10f, 0.13f, 0.10f, Limb.ARM_R, p++));
        list.add(new CrystalDef(-0.40f, 1.05f, 0f, 0.09f, 0.12f, 0.09f, Limb.ARM_R, p++));
        list.add(new CrystalDef(-0.42f, 0.88f, 0f, 0.09f, 0.12f, 0.09f, Limb.ARM_R, p++));
        list.add(new CrystalDef(-0.43f, 0.72f, 0f, 0.08f, 0.10f, 0.08f, Limb.ARM_R, p++));

        
        list.add(new CrystalDef(0.36f, 1.38f, 0f, 0.11f, 0.12f, 0.11f, Limb.ARM_L, p++));
        list.add(new CrystalDef(0.38f, 1.22f, 0f, 0.10f, 0.13f, 0.10f, Limb.ARM_L, p++));
        list.add(new CrystalDef(0.40f, 1.05f, 0f, 0.09f, 0.12f, 0.09f, Limb.ARM_L, p++));
        list.add(new CrystalDef(0.42f, 0.88f, 0f, 0.09f, 0.12f, 0.09f, Limb.ARM_L, p++));
        list.add(new CrystalDef(0.43f, 0.72f, 0f, 0.08f, 0.10f, 0.08f, Limb.ARM_L, p++));

        
        list.add(new CrystalDef(-0.14f, 0.62f, 0f, 0.12f, 0.14f, 0.11f, Limb.LEG_R, p++));
        list.add(new CrystalDef(-0.14f, 0.42f, 0f, 0.11f, 0.14f, 0.10f, Limb.LEG_R, p++));
        list.add(new CrystalDef(-0.14f, 0.24f, 0f, 0.10f, 0.13f, 0.10f, Limb.LEG_R, p++));
        list.add(new CrystalDef(-0.14f, 0.08f, 0.02f, 0.11f, 0.10f, 0.12f, Limb.LEG_R, p++));

        
        list.add(new CrystalDef(0.14f, 0.62f, 0f, 0.12f, 0.14f, 0.11f, Limb.LEG_L, p++));
        list.add(new CrystalDef(0.14f, 0.42f, 0f, 0.11f, 0.14f, 0.10f, Limb.LEG_L, p++));
        list.add(new CrystalDef(0.14f, 0.24f, 0f, 0.10f, 0.13f, 0.10f, Limb.LEG_L, p++));
        list.add(new CrystalDef(0.14f, 0.08f, 0.02f, 0.11f, 0.10f, 0.12f, Limb.LEG_L, p++));

        return List.copyOf(list);
    }

    private final ColorSetting color = register(new ColorSetting("Color", "Crystal color.", new Color(80, 180, 255)));
    private final NumberSetting size = register(new NumberSetting("Size", "Crystal size multiplier.", 1.0, 0.4, 2.2, 0.05));
    private final NumberSetting range = register(new NumberSetting("Range", "Render range.", 64.0, 8.0, 256.0, 1.0));
    private final NumberSetting spinSpeed = register(new NumberSetting("Spin", "Crystal spin speed.", 1.0, 0.0, 5.0, 0.1));
    private final NumberSetting limbFollow = register(new NumberSetting("Limb Follow", "How strongly limbs follow walk cycle.", 1.0, 0.0, 2.0, 0.05));
    private final BooleanSetting hidePlayers = register(new BooleanSetting("Hide Players", "Hide original player models.", true));
    private final BooleanSetting filled = register(new BooleanSetting("Filled", "Draw filled crystal faces.", true));
    private final BooleanSetting lines = register(new BooleanSetting("Edges", "Draw crystal edges.", true));
    private final BooleanSetting showGlow = register(new BooleanSetting("Glow", "Show body glow.", true));
    private final BooleanSetting self = register(new BooleanSetting("Self", "Render on yourself (F5).", false));

    public PrismESP() {
        super("PrismESP", "Full-body animated crystal suit on players.", ModuleCategory.VISUAL);
        instance = this;
    }

    public static PrismESP getInstance() {
        return instance;
    }

    public static boolean shouldHidePlayers() {
        return instance != null && instance.isEnabled() && instance.hidePlayers.getValue();
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        float partial = event.getPartialTicks();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        float rangeSq = range.getFloat() * range.getFloat();
        int baseColor = color.getValue().getRGB();
        float scaleMul = size.getFloat();
        float spin = spinSpeed.getFloat();
        float limbMul = limbFollow.getFloat();
        long time = System.currentTimeMillis();

        List<AbstractClientPlayer> targets = new ArrayList<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player && !self.getValue()) continue;
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (!player.isAlive()) continue;
            double dx = player.getX() - mc.player.getX();
            double dy = player.getY() - mc.player.getY();
            double dz = player.getZ() - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            targets.add(player);
        }

        if (targets.isEmpty()) {
            return;
        }

        
        if (filled.getValue()) {
            VertexConsumer fill = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
            for (AbstractClientPlayer player : targets) {
                drawPlayer(stack, fill, null, null, player, cam, partial, baseColor, scaleMul, spin, limbMul, time, true, false, false);
            }
            provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
        }

        if (lines.getValue()) {
            VertexConsumer lineVc = provider.getBuffer(ClientPipelines.WORLD_PARTICLES_LINES);
            for (AbstractClientPlayer player : targets) {
                drawPlayer(stack, null, lineVc, null, player, cam, partial, baseColor, scaleMul, spin, limbMul, time, false, true, false);
            }
            provider.endBatch(ClientPipelines.WORLD_PARTICLES_LINES);
        }

        if (showGlow.getValue()) {
            for (AbstractClientPlayer player : targets) {
                double wx = Mth.lerp(partial, player.xo, player.getX());
                double wy = Mth.lerp(partial, player.yo, player.getY()) + 1.0;
                double wz = Mth.lerp(partial, player.zo, player.getZ());
                Glow3D.begin();
                Glow3D.glow(wx, wy + 0.2, wz, 0.55f * scaleMul, baseColor, 0.40f);
                Glow3D.glow(wx, wy - 0.3, wz, 0.40f * scaleMul, baseColor, 0.25f);
                Glow3D.end();
            }
        }
    }

    private void drawPlayer(PoseStack stack,
                            VertexConsumer fill, VertexConsumer lines, VertexConsumer glow,
                            AbstractClientPlayer player, Vec3 cam, float partial,
                            int baseColor, float scaleMul, float spin, float limbMul, long time,
                            boolean doFill, boolean doLines, boolean doGlow) {
        double px = Mth.lerp(partial, player.xo, player.getX()) - cam.x;
        double py = Mth.lerp(partial, player.yo, player.getY()) - cam.y;
        double pz = Mth.lerp(partial, player.zo, player.getZ()) - cam.z;

        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float headPitch = Mth.lerp(partial, player.xRotO, player.getXRot());

        float walkPos = player.walkAnimation.position(partial);
        float walkSpeed = Mth.clamp(player.walkAnimation.speed(partial), 0f, 1f);
        float swing = walkPos * 0.6662f;
        float swingAmt = walkSpeed * 1.4f * limbMul;
        boolean crouch = player.isCrouching();
        float crouchOff = crouch ? -0.18f : 0f;

        
        float headRelYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float armR = Mth.cos(swing) * swingAmt * 48f;
        float armL = Mth.cos(swing + (float) Math.PI) * swingAmt * 48f;
        float legR = Mth.cos(swing + (float) Math.PI) * swingAmt * 42f;
        float legL = Mth.cos(swing) * swingAmt * 42f;
        if (player.swinging) {
            float sp = player.getAttackAnim(partial);
            armR += -Mth.sin(sp * (float) Math.PI) * 55f;
        }

        stack.pushPose();
        stack.translate(px, py, pz);
        stack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        for (CrystalDef def : BODY_CRYSTALS) {
            float limbPitch = 0f;
            float limbRoll = 0f;
            float partYaw = 0f;
            float rootX = 0f, rootY = 0f, rootZ = 0f;

            switch (def.limb) {
                case HEAD -> {
                    rootY = crouch ? 1.38f : 1.52f;
                    partYaw = headRelYaw;
                    limbPitch = headPitch * 0.55f;
                }
                case BODY -> {
                    rootY = crouchOff;
                    limbPitch = crouch ? 16f : 0f;
                }
                case ARM_R -> {
                    rootX = -0.32f;
                    rootY = (crouch ? 1.20f : 1.35f);
                    limbPitch = armR;
                    limbRoll = walkSpeed * 5f;
                }
                case ARM_L -> {
                    rootX = 0.32f;
                    rootY = (crouch ? 1.20f : 1.35f);
                    limbPitch = armL;
                    limbRoll = -walkSpeed * 5f;
                }
                case LEG_R -> {
                    rootX = -0.12f;
                    rootY = crouch ? 0.55f : 0.70f;
                    limbPitch = legR;
                }
                case LEG_L -> {
                    rootX = 0.12f;
                    rootY = crouch ? 0.55f : 0.70f;
                    limbPitch = legL;
                }
            }

            
            float localX = def.ox - rootX;
            float localY = def.oy - rootY + (def.limb == Limb.BODY || def.limb == Limb.HEAD ? crouchOff : 0f);
            float localZ = def.oz - rootZ;
            if (def.limb == Limb.HEAD) {
                localX = def.ox;
                localY = def.oy - (crouch ? 1.38f : 1.52f) + crouchOff * 0.3f;
                localZ = def.oz;
            }
            if (def.limb == Limb.BODY) {
                localX = def.ox;
                localY = def.oy + crouchOff;
                localZ = def.oz;
            }

            float bob = Mth.sin(time * 0.0038f + def.phase * 0.7f) * 0.012f;
            float pulse = 1f + Mth.sin(time * 0.0045f + def.phase * 0.55f) * 0.05f;
            float spinY = ((time % 9000L) / 9000f) * 360f * spin + def.phase * 17f;
            float spinX = Mth.sin(time * 0.0022f + def.phase * 0.4f) * 10f;

            stack.pushPose();
            if (def.limb != Limb.BODY) {
                stack.translate(rootX, rootY, rootZ);
                if (def.limb == Limb.HEAD) {
                    stack.mulPose(Axis.YP.rotationDegrees(-partYaw));
                }
                stack.mulPose(Axis.XP.rotationDegrees(limbPitch));
                stack.mulPose(Axis.ZP.rotationDegrees(limbRoll));
                stack.translate(localX, localY + bob, localZ);
            } else {
                stack.translate(localX, localY + bob, localZ);
                stack.mulPose(Axis.XP.rotationDegrees(limbPitch));
            }

            stack.mulPose(Axis.YP.rotationDegrees(spinY));
            stack.mulPose(Axis.XP.rotationDegrees(spinX));
            stack.scale(def.sx * scaleMul * pulse, def.sy * scaleMul * pulse, def.sz * scaleMul * pulse);

            PoseStack.Pose entry = stack.last();
            boolean isHead = def.limb == Limb.HEAD;
            int fillCol = ColorUtil.withAlpha(baseColor, isHead ? 100 : 70);
            int lineCol = ColorUtil.withAlpha(baseColor, isHead ? 255 : 200);

            if (doFill && fill != null) {
                for (int[] face : FACES) {
                    float[] a = OCT[face[0]];
                    float[] b = OCT[face[1]];
                    float[] c = OCT[face[2]];
                    
                    fill.addVertex(entry, a[0], a[1], a[2]).setColor(fillCol);
                    fill.addVertex(entry, b[0], b[1], b[2]).setColor(fillCol);
                    fill.addVertex(entry, c[0], c[1], c[2]).setColor(fillCol);
                    fill.addVertex(entry, c[0], c[1], c[2]).setColor(fillCol);
                }
            }

            if (doLines && lines != null) {
                for (int[] edge : EDGES) {
                    float[] from = OCT[edge[0]];
                    float[] to = OCT[edge[1]];
                    lines.addVertex(entry, from[0], from[1], from[2]).setColor(lineCol);
                    lines.addVertex(entry, to[0], to[1], to[2]).setColor(lineCol);
                }
            }

            stack.popPose();
        }

        stack.popPose();
    }
}
