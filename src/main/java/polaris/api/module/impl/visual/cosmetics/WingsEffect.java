package polaris.api.module.impl.visual.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.IMinecraft;
import java.util.List;
import polaris.api.settings.Setting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Map;


public final class WingsEffect implements IMinecraft {

    
    private static final float[][] SHAPE = {
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
            "Wings", "Animated translucent wings behind the back.", false);

    private final BooleanSetting self = new BooleanSetting(
            "Wing Self", "Render on yourself.", true);
    private final BooleanSetting players = new BooleanSetting(
            "Wing Players", "Render on other players.", false);
    private final NumberSetting size = new NumberSetting(
            "Wing Size", "Wing scale.", 1.0, 0.6, 1.6, 0.05);
    private final NumberSetting flapSpeed = new NumberSetting(
            "Wing Flap Speed", "Wing flap speed.", 1.0, 0.2, 2.5, 0.05);
    private final NumberSetting flapStrength = new NumberSetting(
            "Wing Flap Strength", "How wide the wings beat.", 1.0, 0.2, 2.0, 0.05);
    private final ColorSetting color = new ColorSetting(
            "Wing Color", "Wing color.", new Color(127, 242, 255, 220));

    private final Map<Player, WingState> states = new IdentityHashMap<>();

    public WingsEffect() {
        self.visibleWhen(enabled::getValue);
        players.visibleWhen(enabled::getValue);
        size.visibleWhen(enabled::getValue);
        flapSpeed.visibleWhen(enabled::getValue);
        flapStrength.visibleWhen(enabled::getValue);
        color.visibleWhen(enabled::getValue);

    }

    
    public List<Setting<?>> settings() {
        return List.of(enabled, self, players, size, flapSpeed, flapStrength, color);
    }

    public boolean isOn() {
        return enabled.getValue();
    }

    public void onDisable() {
        states.clear();
    }

    public void onWorldRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        float partial = event.getPartialTicks();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        if (self.getValue()
                && !mc.options.getCameraType().isFirstPerson()
                && mc.player.isAlive()
                && !hasElytra(mc.player)) {
            renderWings(stack, provider, mc.player, partial, cam);
        }

        if (players.getValue()) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive() || hasElytra(player)) {
                    continue;
                }
                renderWings(stack, provider, player, partial, cam);
            }
        }

        
        states.keySet().removeIf(p -> p == null || !p.isAlive() || p.isRemoved());

        provider.endBatch(ClientPipelines.CRYSTAL_GLOW);
        provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
    }

    private boolean hasElytra(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private void renderWings(PoseStack stack, MultiBufferSource.BufferSource provider, Player player, float partial, Vec3 cam) {
        WingState st = states.computeIfAbsent(player, p -> new WingState());
        st.tick(player, partial, flapSpeed.getFloat(), flapStrength.getFloat());

        double x = Mth.lerp(partial, player.xo, player.getX()) - cam.x;
        double y = Mth.lerp(partial, player.yo, player.getY()) - cam.y;
        double z = Mth.lerp(partial, player.zo, player.getZ()) - cam.z;

        int base = color.getValue().getRGB();
        int glow = ColorUtil.lerpColor(base, 0xFFFFFFFF, 0.35f);
        int core = ColorUtil.lerpColor(base, 0xFFFFFFFF, 0.60f);

        float scale = size.getFloat() * st.scaleMul;

        stack.pushPose();
        stack.translate(x, y, z);
        stack.mulPose(Axis.YP.rotationDegrees(180.0f - st.bodyYaw));
        if (st.pitch != 0f) {
            stack.mulPose(Axis.XP.rotationDegrees(st.pitch));
        }
        stack.translate(0.0f, st.anchorY, st.anchorZ);
        stack.scale(scale, scale, scale);

        renderSide(stack, provider, -1f, st, base, glow, core);
        renderSide(stack, provider, 1f, st, base, glow, core);

        stack.popPose();
    }

    private void renderSide(PoseStack stack, MultiBufferSource.BufferSource provider, float side,
                            WingState st, int base, int glow, int core) {
        stack.pushPose();
        stack.translate(side * st.sideOffset, 0.0f, 0.05f);
        stack.mulPose(Axis.YP.rotationDegrees(side * st.openAngle));
        stack.mulPose(Axis.ZP.rotationDegrees(side * st.roll));
        stack.mulPose(Axis.XP.rotationDegrees(st.sidePitch + st.flapPitch * side * 0.15f));

        
        VertexConsumer glowVc = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);
        membrane(stack, glowVc, side, 1.28f, ColorUtil.withAlpha(glow, 48), ColorUtil.withAlpha(glow, 0));
        membrane(stack, glowVc, side, 0.92f, ColorUtil.withAlpha(core, 58), ColorUtil.withAlpha(core, 0));

        VertexConsumer baseVc = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        membrane(stack, baseVc, side, 1.0f, ColorUtil.withAlpha(base, 210), ColorUtil.withAlpha(base, 16));
        
        stack.translate(0f, 0.02f, -0.015f);
        membrane(stack, baseVc, side, 0.86f, ColorUtil.withAlpha(base, 120), ColorUtil.withAlpha(base, 8));

        stack.popPose();
    }

    private void membrane(PoseStack stack, VertexConsumer vc, float side, float scale, int rootColor, int edgeColor) {
        PoseStack.Pose pose = stack.last();
        for (int i = 0; i < SHAPE.length; i++) {
            float[] cur = SHAPE[i];
            float[] next = SHAPE[(i + 1) % SHAPE.length];
            int cCur = applyPointAlpha(edgeColor, cur[2]);
            int cNext = applyPointAlpha(edgeColor, next[2]);

            vc.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(rootColor);
            vc.addVertex(pose, side * cur[0] * scale, cur[1] * scale, 0.0f).setColor(cCur);
            vc.addVertex(pose, side * next[0] * scale, next[1] * scale, 0.0f).setColor(cNext);
            vc.addVertex(pose, side * next[0] * scale, next[1] * scale, 0.0f).setColor(cNext);
        }
    }

    private int applyPointAlpha(int color, float multiplier) {
        return ColorUtil.withAlpha(color, Mth.clamp((int) (ColorUtil.getAlpha(color) * multiplier), 0, 255));
    }

    private static final class WingState {
        float bodyYaw;
        boolean yawInit;
        float openAngle;
        float flapPhase;
        float flapPitch;
        float roll;
        float sidePitch;
        float sideOffset = 0.07f;
        float anchorY = 1.35f;
        float anchorZ = 0.10f;
        float pitch;
        float scaleMul = 1f;
        long lastNs = System.nanoTime();

        void tick(Player player, float partial, float speedMul, float strengthMul) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
            lastNs = now;

            float targetYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            if (!yawInit) {
                bodyYaw = targetYaw;
                yawInit = true;
            } else {
                float d = Mth.wrapDegrees(targetYaw - bodyYaw);
                bodyYaw += Mth.clamp(d, -18f, 18f) * Math.min(1f, dt * 14f);
            }

            float walk = Mth.clamp(player.walkAnimation.speed(partial), 0f, 1f);
            boolean flying = player.isFallFlying();
            boolean crouch = player.isCrouching();
            boolean airborne = !player.onGround() && !flying;

            
            float phaseSpeed = (0.9f + walk * 2.4f + (airborne ? 1.6f : 0f) + (flying ? 0.4f : 0f)) * speedMul;
            flapPhase += dt * phaseSpeed * 6.5f;

            float flapWave = Mth.sin(flapPhase);
            float flapWave2 = Mth.sin(flapPhase * 0.5f + 0.4f);

            float baseOpen = 10f + walk * 8f;
            if (flying) {
                float flightTicks = player.getFallFlyingTicks() + partial;
                float flightProgress = Mth.clamp(flightTicks * flightTicks / 100f, 0f, 1f);
                float lookPitch = Mth.lerp(partial, player.xRotO, player.getXRot());
                pitch = flightProgress * (-90f - lookPitch);
                baseOpen = 28f + flapWave * 4f * strengthMul;
                flapPitch = flapWave * 6f * strengthMul;
                roll = -4f;
                sidePitch = -8f;
                anchorY = 0.95f;
                anchorZ = 0.08f;
                scaleMul = 0.95f;
            } else if (crouch) {
                pitch = 16f;
                baseOpen = 6f + flapWave * 2f;
                flapPitch = flapWave * 3f;
                roll = -14f;
                sidePitch = -6f;
                anchorY = 1.05f;
                anchorZ = 0.12f;
                scaleMul = 0.96f;
            } else if (airborne) {
                pitch = 0f;
                baseOpen = 18f + flapWave * 12f * strengthMul;
                flapPitch = flapWave * 14f * strengthMul + flapWave2 * 3f;
                roll = -9f + flapWave * 2f;
                sidePitch = -5f + flapWave * 4f;
                anchorY = 1.32f;
                anchorZ = 0.10f;
                scaleMul = 1.0f;
            } else {
                pitch = 0f;
                baseOpen = 9f + walk * 10f + flapWave * (5f + walk * 6f) * strengthMul;
                flapPitch = flapWave * (5f + walk * 8f) * strengthMul;
                roll = -11f + walk * 2f;
                sidePitch = -4f;
                anchorY = 1.36f;
                anchorZ = 0.10f;
                scaleMul = 1.0f;
            }

            
            openAngle += (baseOpen - openAngle) * Math.min(1f, dt * 10f);
            sideOffset = 0.065f + walk * 0.01f;
        }
    }
}
