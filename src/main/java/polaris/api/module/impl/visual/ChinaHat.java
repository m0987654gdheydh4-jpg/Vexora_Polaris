package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import polaris.api.drag.impl.HudTheme;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;


public final class ChinaHat extends Module {
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";


    private static final int SEGMENTS = 96;

    private static final float HEAD_PIVOT = 1.501f;

    private static final float BRIM_ABOVE_PIVOT = 0.503f;
    private static final float CROUCH_DROP = 0.28f;


    private static final float[][] RING_BANDS = {
            {0.00f, 0.07f},
            {-0.13f, -0.08f}
    };
    private static final float[] RING_ALPHA = {0.68f, 0.42f};


    private static final float TIP_ALPHA = 0.58f;

    private final BooleanSetting self = register(new BooleanSetting("Self", "Render on yourself.", true));
    private final BooleanSetting players = register(new BooleanSetting("Players", "Render on other players.", true));
    private final NumberSetting radius = register(new NumberSetting("Radius", "Brim radius.", 0.56, 0.3, 1.0, 0.01));
    private final NumberSetting height = register(new NumberSetting("Height", "Cone height.", 0.27, 0.1, 0.7, 0.01));
    private final NumberSetting lift = register(new NumberSetting("Lift", "Extra height above the head.", 0.0, -0.2, 0.5, 0.01));
    private final NumberSetting opacity = register(new NumberSetting("Opacity", "Brim opacity (%).", 92.0, 10.0, 100.0, 1.0));
    private final BooleanSetting rings = register(new BooleanSetting("Rings", "Decorative rings around the cone.", true));
    private final NumberSetting ringPosition = register(new NumberSetting(
            "Ring Position", "Where the rings sit: 0 = tip, 1 = brim.", 0.86, 0.15, 1.0, 0.01));
    private final BooleanSetting underside = register(new BooleanSetting("Underside", "Fill the hat from below.", true));
    private final NumberSetting spinSpeed = register(new NumberSetting("Spin", "Rotation speed (0 = static).", 0.0, 0.0, 120.0, 1.0));
    private final NumberSetting bob = register(new NumberSetting("Bob", "Floating amplitude (0 = off).", 0.0, 0.0, 0.12, 0.005));
    private final ModeSetting colorMode = register(new ModeSetting("Color", "Where the hat takes its colour from.",
            COLOR_THEME, COLOR_THEME, COLOR_CUSTOM));
    private final ColorSetting customColor = register(new ColorSetting("Custom Color", "Hat colour.", new Color(255, 120, 90)));

    public ChinaHat() {
        super("ChinaHat", "Conical hat above the head, in the theme colour.", ModuleCategory.VISUAL);
        customColor.visibleWhen(() -> colorMode.is(COLOR_CUSTOM));
        ringPosition.visibleWhen(rings::getValue);
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

        if (self.getValue() && !mc.options.getCameraType().isFirstPerson() && isRenderable(mc.player)) {
            renderHat(stack, provider, mc.player, partial, cam);
        }

        if (players.getValue()) {
            for (Player player : mc.level.players()) {
                if (player == mc.player || !isRenderable(player)) {
                    continue;
                }
                renderHat(stack, provider, player, partial, cam);
            }
        }

        provider.endBatch(ClientPipelines.CRYSTAL_GLOW);
        provider.endBatch(ClientPipelines.CRYSTAL_FILLED);
    }

    private boolean isRenderable(Player player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && !player.isInvisible();
    }

    private void renderHat(PoseStack stack, MultiBufferSource.BufferSource provider, Player player, float partial, Vec3 cam) {
        double x = Mth.lerp(partial, player.xo, player.getX()) - cam.x;
        double y = Mth.lerp(partial, player.yo, player.getY()) - cam.y;
        double z = Mth.lerp(partial, player.zo, player.getZ()) - cam.z;

        float pivot = HEAD_PIVOT + (player.isCrouching() ? -CROUCH_DROP : 0.0f);
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000.0f;
        float floatY = bob.getFloat() <= 0.0001f ? 0.0f : (float) Math.sin(time * 1.7f) * bob.getFloat();

        stack.pushPose();

        stack.translate(x, y + pivot, z);

        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        stack.mulPose(Axis.YP.rotationDegrees(-headYaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));

        stack.translate(0.0f, BRIM_ABOVE_PIVOT + lift.getFloat() + floatY, 0.0f);
        if (spinSpeed.getFloat() > 0.01f) {
            stack.mulPose(Axis.YP.rotationDegrees(time * spinSpeed.getFloat() % 360.0f));
        }

        int base = resolveColor();
        int brimAlpha = Math.round(255 * Mth.clamp(opacity.getFloat() / 100.0f, 0.0f, 1.0f));
        int brim = ColorUtil.withAlpha(base, brimAlpha);
        int tip = ColorUtil.withAlpha(ColorUtil.lerpColor(base, ColorUtil.WHITE, 0.35f),
                Math.round(brimAlpha * TIP_ALPHA));

        float r = radius.getFloat();
        float h = height.getFloat();

        VertexConsumer filled = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED);
        cone(stack, filled, r, h, tip, brim, false);
        if (underside.getValue()) {

            cone(stack, filled, r * 0.995f, h * 0.98f,
                    ColorUtil.withAlpha(tip, Math.round(brimAlpha * 0.35f)),
                    ColorUtil.withAlpha(brim, Math.round(brimAlpha * 0.6f)), true);
        }

        if (rings.getValue()) {
            VertexConsumer glow = provider.getBuffer(ClientPipelines.CRYSTAL_GLOW);
            int ringColor = ColorUtil.lerpColor(base, ColorUtil.WHITE, 0.55f);
            float anchor = ringPosition.getFloat();
            for (int i = 0; i < RING_BANDS.length; i++) {
                float innerT = Mth.clamp(anchor + RING_BANDS[i][0], 0.05f, 1.0f);
                float outerT = Mth.clamp(anchor + RING_BANDS[i][1], 0.05f, 1.0f);
                if (outerT - innerT < 0.005f) {
                    continue;
                }
                ring(stack, glow, r, h, innerT, outerT,
                        ColorUtil.withAlpha(ringColor, Math.round(brimAlpha * RING_ALPHA[i])));
            }
        }

        stack.popPose();
    }


    private void cone(PoseStack stack, VertexConsumer buffer, float coneRadius, float coneHeight,
                      int tipColor, int brimColor, boolean flip) {
        PoseStack.Pose pose = stack.last();
        float step = (float) (Math.PI * 2.0) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (flip ? SEGMENTS - i : i) * step;
            float a1 = (flip ? SEGMENTS - i - 1 : i + 1) * step;
            float x0 = Mth.cos(a0) * coneRadius;
            float z0 = Mth.sin(a0) * coneRadius;
            float x1 = Mth.cos(a1) * coneRadius;
            float z1 = Mth.sin(a1) * coneRadius;

            buffer.addVertex(pose, 0.0f, coneHeight, 0.0f).setColor(tipColor);
            buffer.addVertex(pose, x0, 0.0f, z0).setColor(brimColor);
            buffer.addVertex(pose, x1, 0.0f, z1).setColor(brimColor);
            buffer.addVertex(pose, x1, 0.0f, z1).setColor(brimColor);
        }
    }


    private void ring(PoseStack stack, VertexConsumer buffer, float coneRadius, float coneHeight,
                      float innerT, float outerT, int color) {
        PoseStack.Pose pose = stack.last();
        float step = (float) (Math.PI * 2.0) / SEGMENTS;
        float rInner = coneRadius * innerT;
        float rOuter = coneRadius * outerT;

        float yInner = coneHeight * (1.0f - innerT) + 0.002f;
        float yOuter = coneHeight * (1.0f - outerT) + 0.002f;

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);

            buffer.addVertex(pose, c0 * rInner, yInner, s0 * rInner).setColor(color);
            buffer.addVertex(pose, c0 * rOuter, yOuter, s0 * rOuter).setColor(color);
            buffer.addVertex(pose, c1 * rOuter, yOuter, s1 * rOuter).setColor(color);
            buffer.addVertex(pose, c1 * rInner, yInner, s1 * rInner).setColor(color);
        }
    }

    private int resolveColor() {
        if (colorMode.is(COLOR_CUSTOM)) {
            return customColor.getValue().getRGB();
        }
        return HudTheme.current().accentColor;
    }
}
