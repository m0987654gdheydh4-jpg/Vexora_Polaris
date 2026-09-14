package polaris.api.module.impl.visual.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.IMinecraft;
import polaris.api.settings.Setting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.WorldVertex;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public final class JumpCircleEffect implements IMinecraft {
    private static final Identifier[][] TEXTURES = {
            {
                    id("textures/features/jumpcircle/shtorm1.png"),
                    id("textures/features/jumpcircle/shtorm2.png"),
                    id("textures/features/jumpcircle/glow.png")
            },
            {
                    id("textures/features/jumpcircle/glow.png"),
                    id("textures/features/jumpcircle/omut2.png"),
                    id("textures/features/jumpcircle/omut3.png")
            },
            {
                    id("textures/features/jumpcircle/colso1.png"),
                    id("textures/features/jumpcircle/omut2.png"),
                    id("textures/features/jumpcircle/colso3.png")
            },
            {
                    id("textures/features/jumpcircle/boom1.png"),
                    id("textures/features/jumpcircle/boom2.png"),
                    id("textures/features/jumpcircle/glow.png")
            }
    };

    
    private final BooleanSetting enabled = new BooleanSetting(
            "Jump Circle", "Expanding textured ring when you jump.", false);

    private final ModeSetting texture = new ModeSetting(
            "Circle Texture", "Jump circle style.", "Storm", "Storm", "Pool", "Rings", "Explosion");
    private final NumberSetting size = new NumberSetting(
            "Circle Size", "Max circle size.", 1.4, 0.5, 3.0, 0.1);
    private final NumberSetting speed = new NumberSetting(
            "Circle Speed", "Rotation speed.", 4.8, 0.0, 8.0, 0.1);
    private final NumberSetting lifetime = new NumberSetting(
            "Circle Lifetime", "Circle lifetime (ms).", 650.0, 250.0, 1500.0, 50.0);
    private final ModeSetting colorMode = new ModeSetting(
            "Circle Color", "Color mode.", "Rainbow", "Rainbow", "Custom");
    private final ColorSetting customColor = new ColorSetting(
            "Circle Custom Color", "Circle color.", new Color(120, 200, 255, 255));

    private final List<Circle> circles = new ArrayList<>();
    private boolean wasOnGround;
    private Vec3 lastGroundPos;

    public JumpCircleEffect() {
        customColor.visibleWhen(() -> enabled.getValue() && (colorMode.is("Custom")));
        texture.visibleWhen(enabled::getValue);
        size.visibleWhen(enabled::getValue);
        speed.visibleWhen(enabled::getValue);
        lifetime.visibleWhen(enabled::getValue);
        colorMode.visibleWhen(enabled::getValue);

    }

    
    public List<Setting<?>> settings() {
        return List.of(enabled, texture, size, speed, lifetime, colorMode, customColor);
    }

    public boolean isOn() {
        return enabled.getValue();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("cataclysm", path);
    }

    public void onEnable() {
        circles.clear();
        wasOnGround = mc.player != null && mc.player.onGround();
        lastGroundPos = mc.player == null ? null : mc.player.position();
    }

    public void onDisable() {
        circles.clear();
        lastGroundPos = null;
    }

    public void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        boolean onGround = mc.player.onGround();
        if (onGround) {
            lastGroundPos = mc.player.position();
        }
        if (wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0.05) {
            Vec3 spawn = lastGroundPos == null ? mc.player.position() : lastGroundPos;
            circles.add(new Circle(spawn, System.currentTimeMillis()));
        }
        wasOnGround = onGround;
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (!enabled.getValue() || circles.isEmpty() || mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float life = lifetime.getFloat();
        Iterator<Circle> it = circles.iterator();
        while (it.hasNext()) {
            if (now - it.next().born > life) {
                it.remove();
            }
        }
        if (circles.isEmpty()) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Identifier[] layers = TEXTURES[textureIndex()];
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PoseStack stack = e.getStack();

        for (Identifier tex : layers) {
            RenderType type = ClientPipelines.WORLD_PARTICLES_GLOW.apply(tex);
            VertexConsumer vc = buf.getBuffer(type);
            for (Circle c : circles) {
                drawOne(stack, vc, cam, c, now, life);
            }
            buf.endBatch(type);
        }
    }

    private void drawOne(PoseStack stack, VertexConsumer vc, Vec3 cam, Circle c, long now, float life) {
        float t = Mth.clamp((now - c.born) / life, 0f, 1f);
        float fade = 1f - t;
        float radius = Mth.lerp(t, 0.15f, size.getFloat());
        float angleDeg = (now - c.born) / 1000f * 360f * speed.getFloat();

        int base = baseColor((int) ((now - c.born) / 4));
        int outer = ColorUtil.multAlpha(base, fade * fade * 0.45f);
        int inner = ColorUtil.multAlpha(shiftHue(base, 0.5f), fade * fade);

        double x = c.pos.x - cam.x;
        double y = c.pos.y + 0.04 - cam.y;
        double z = c.pos.z - cam.z;

        stack.pushPose();
        stack.translate(x, y, z);
        stack.mulPose(Axis.XP.rotationDegrees(90f));
        stack.mulPose(Axis.ZP.rotationDegrees(angleDeg));

        float h = radius * 1.22f;
        PoseStack.Pose pose = stack.last();
        WorldVertex.textured(vc, pose, -h, -h, 0f, 0f, 0f, outer);
        WorldVertex.textured(vc, pose, h, -h, 0f, 1f, 0f, outer);
        WorldVertex.textured(vc, pose, h, h, 0f, 1f, 1f, outer);
        WorldVertex.textured(vc, pose, -h, h, 0f, 0f, 1f, outer);

        float h2 = radius;
        stack.translate(0, 0, -0.002f);
        pose = stack.last();
        WorldVertex.textured(vc, pose, -h2, -h2, 0f, 0f, 0f, inner);
        WorldVertex.textured(vc, pose, h2, -h2, 0f, 1f, 0f, inner);
        WorldVertex.textured(vc, pose, h2, h2, 0f, 1f, 1f, inner);
        WorldVertex.textured(vc, pose, -h2, h2, 0f, 0f, 1f, inner);
        stack.popPose();
    }

    private int textureIndex() {
        return switch (texture.getValue()) {
            case "Pool" -> 1;
            case "Rings" -> 2;
            case "Explosion" -> 3;
            default -> 0;
        };
    }

    private int baseColor(int offset) {
        if (colorMode.is("Custom")) {
            return customColor.getValue().getRGB();
        }
        float hue = ((System.currentTimeMillis() + offset) % 3000L) / 3000f;
        return Color.HSBtoRGB(hue, 0.7f, 1f) | 0xFF000000;
    }

    private static int shiftHue(int color, float amount) {
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        hsb[0] = (hsb[0] + amount) % 1f;
        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) | 0xFF000000;
    }

    private static final class Circle {
        final Vec3 pos;
        final long born;

        Circle(Vec3 pos, long born) {
            this.pos = pos;
            this.born = born;
        }
    }
}
