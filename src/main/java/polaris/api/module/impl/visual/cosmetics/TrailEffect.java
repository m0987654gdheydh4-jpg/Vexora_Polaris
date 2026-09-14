package polaris.api.module.impl.visual.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


public final class TrailEffect implements IMinecraft {
    private static final Identifier FIREFLY = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/particles/firefly.png");
    private static final Identifier STAR = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/particles/star.png");
    private static final Identifier HEART = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/particles/heart.png");
    private static final Identifier SNOW = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/particles/snowflake.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/jumpcircle/glowboost.png");

    
    private final BooleanSetting enabled = new BooleanSetting(
            "Trail", "Glowing trail behind you while moving.", false);

    private final ModeSetting mode = new ModeSetting(
            "Trail Mode", "Trail style.", "Trails", "Particles", "Trails");
    private final ModeSetting particleTexture = new ModeSetting(
            "Trail Particle Texture", "Sprite for particle mode.", "Firefly", "Firefly", "Star", "Heart", "Snow");
    private final BooleanSetting hideFirstPerson = new BooleanSetting(
            "Trail Hide First Person", "Hide trail in first person.", false);

    private final NumberSetting length = new NumberSetting(
            "Trail Length", "Trail point capacity.", 150.0, 50.0, 300.0, 10.0);
    private final NumberSetting pointSize = new NumberSetting(
            "Trail Point Size", "Trail sprite size.", 0.5, 0.1, 2.0, 0.1);
    private final NumberSetting opacity = new NumberSetting(
            "Trail Opacity", "Trail opacity %.", 100.0, 10.0, 100.0, 5.0);

    private final NumberSetting particleCount = new NumberSetting(
            "Trail Particle Count", "Spawn count while moving.", 5.0, 1.0, 10.0, 1.0);
    private final NumberSetting particleSize = new NumberSetting(
            "Trail Particle Size", "Particle scale.", 0.2, 0.1, 3.0, 0.1);
    private final NumberSetting particleSpeed = new NumberSetting(
            "Trail Particle Speed", "Particle velocity.", 1.3, 0.1, 3.0, 0.1);
    private final NumberSetting particleLifetime = new NumberSetting(
            "Trail Particle Lifetime", "Lifetime in ticks.", 40.0, 10.0, 100.0, 5.0);

    private final ModeSetting colorMode = new ModeSetting(
            "Trail Color", "Color mode.", "Rainbow", "Rainbow", "Custom");
    private final ColorSetting customColor = new ColorSetting(
            "Trail Custom Color", "Trail color.", new Color(120, 200, 255, 255));

    private final Deque<Vec3> trail = new ArrayDeque<>();
    private final List<TrailParticle> particles = new ArrayList<>();
    private final Set<RenderType> used = new HashSet<>();
    private Vec3 lastPoint;
    private int idleTicks;
    private int moveTicks;

    public TrailEffect() {
        particleTexture.visibleWhen(() -> enabled.getValue() && (mode.is("Particles")));
        length.visibleWhen(() -> enabled.getValue() && (mode.is("Trails")));
        pointSize.visibleWhen(() -> enabled.getValue() && (mode.is("Trails")));
        opacity.visibleWhen(() -> enabled.getValue() && (mode.is("Trails")));
        particleCount.visibleWhen(() -> enabled.getValue() && (mode.is("Particles")));
        particleSize.visibleWhen(() -> enabled.getValue() && (mode.is("Particles")));
        particleSpeed.visibleWhen(() -> enabled.getValue() && (mode.is("Particles")));
        particleLifetime.visibleWhen(() -> enabled.getValue() && (mode.is("Particles")));
        customColor.visibleWhen(() -> enabled.getValue() && (colorMode.is("Custom")));
        mode.visibleWhen(enabled::getValue);
        hideFirstPerson.visibleWhen(enabled::getValue);
        colorMode.visibleWhen(enabled::getValue);

    }

    
    public List<Setting<?>> settings() {
        return List.of(enabled, mode, particleTexture, hideFirstPerson, length, pointSize, opacity, particleCount, particleSize, particleSpeed, particleLifetime, colorMode, customColor);
    }

    public boolean isOn() {
        return enabled.getValue();
    }

    public void onDisable() {
        trail.clear();
        particles.clear();
        lastPoint = null;
        idleTicks = 0;
        moveTicks = 0;
    }

    public void onTick(TickEvent.Post e) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mode.is("Particles")) {
            tickParticles();
        } else {
            tickTrail();
        }
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (!enabled.getValue() || mc.player == null) {
            return;
        }
        if (hideFirstPerson.getValue() && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        used.clear();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        if (mode.is("Particles")) {
            renderParticles(e.getStack(), buf, e.getPartialTicks());
        } else if (trail.size() >= 2) {
            renderTrail(e.getStack(), buf);
        }
        for (RenderType type : used) {
            buf.endBatch(type);
        }
        used.clear();
    }

    private void tickTrail() {
        int max = length.getValue().intValue();
        Vec3 now = mc.player.position().add(0, mc.player.getBbHeight() * 0.5, 0);
        if (lastPoint == null) {
            trail.addLast(now);
            lastPoint = now;
            idleTicks = 0;
            return;
        }
        double distSq = lastPoint.distanceToSqr(now);
        if (distSq > 9.0E-6) {
            int steps = Math.max(1, (int) (distSq / 0.02));
            if (steps > 1000) {
                steps = 0;
            }
            for (int i = 1; i <= steps; i++) {
                trail.addLast(lastPoint.lerp(now, (double) i / steps));
            }
            lastPoint = now;
            idleTicks = 0;
            while (trail.size() > max) {
                trail.pollFirst();
            }
        } else {
            idleTicks++;
            if (idleTicks > 2) {
                for (int i = 0; i < 3 && !trail.isEmpty(); i++) {
                    trail.pollFirst();
                }
            }
        }
    }

    private void tickParticles() {
        Vec3 center = mc.player.getBoundingBox().getCenter();
        Vec3 motion = mc.player.getDeltaMovement();
        boolean moving = Math.abs(motion.x) > 0.05 || Math.abs(motion.z) > 0.05 || Math.abs(motion.y) > 0.15;
        if (moving) {
            moveTicks++;
            if (moveTicks >= 2) {
                moveTicks = 0;
                spawnParticles(center, motion);
            }
        }
        Iterator<TrailParticle> it = particles.iterator();
        while (it.hasNext()) {
            TrailParticle p = it.next();
            p.prev = p.pos;
            p.life--;
            if (p.life <= 0 || p.pos.y <= mc.level.getMinY()) {
                it.remove();
                continue;
            }
            float age = 1f - (float) p.life / p.maxLife;
            Vec3 vel = p.vel;
            if (age > 0.6f) {
                float t = (age - 0.6f) / 0.4f;
                vel = new Vec3(vel.x * 0.98, vel.y - 0.003 * t, vel.z * 0.98);
            } else {
                vel = vel.scale(0.98);
            }
            p.vel = vel;
            p.pos = p.pos.add(vel);
            p.spin += p.spinSpeed;
        }
    }

    private void spawnParticles(Vec3 center, Vec3 motion) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = particleCount.getValue().intValue();
        float speed = particleSpeed.getFloat();
        Vec3 back = motion.lengthSqr() > 1.0E-6 ? motion.normalize().scale(-1) : Vec3.ZERO;
        Identifier tex = particleTex();
        int lifeBase = particleLifetime.getValue().intValue();
        for (int i = 0; i < n; i++) {
            double ox = r.nextDouble(-0.3, 0.3);
            double oy = r.nextDouble(-mc.player.getBbHeight() / 2.1, mc.player.getBbHeight() / 2.0);
            double oz = r.nextDouble(-0.3, 0.3);
            Vec3 pos = center.add(back.x * 0.3 + ox, oy, back.z * 0.3 + oz);
            double a = r.nextDouble(0, Math.PI * 2);
            double p = r.nextDouble(-Math.PI / 4, Math.PI / 4);
            double cp = Math.cos(p), sp = Math.sin(p);
            Vec3 vel = new Vec3(
                    Math.cos(a) * cp * r.nextDouble(0.02, 0.06) * speed,
                    sp * r.nextDouble(0.02, 0.06) * speed,
                    Math.sin(a) * cp * r.nextDouble(0.02, 0.06) * speed);
            int life = r.nextInt(Math.max(1, lifeBase / 2), Math.max(2, lifeBase));
            float size = particleSize.getFloat() * r.nextFloat(0.7f, 1.3f);
            particles.add(new TrailParticle(pos, vel, life, size, color(i * 10), tex, r.nextFloat(360f), r.nextFloat(-2f, 2f)));
        }
    }

    private void renderTrail(PoseStack stack, MultiBufferSource.BufferSource buf) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Quaternionf rot = camera.rotation();
        float size = pointSize.getFloat();
        float op = opacity.getFloat() / 100f * 0.85f;
        int i = 0;
        int total = trail.size();
        for (Vec3 point : trail) {
            float t = (float) i / Math.max(1, total);
            float a = t * t * op;
            if (a >= 0.02f) {
                float s = size * (0.2f + t * 0.8f);
                int col = ColorUtil.multAlpha(color(i * 2), a);
                billboard(stack, buf, FIREFLY, point.subtract(cam), rot, s * 1.8f, ColorUtil.multAlpha(col, 0.4f));
                billboard(stack, buf, FIREFLY, point.subtract(cam), rot, s, col);
            }
            i++;
        }
    }

    private void renderParticles(PoseStack stack, MultiBufferSource.BufferSource buf, float pt) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Quaternionf rot = camera.rotation();
        for (TrailParticle p : particles) {
            float age = 1f - (float) p.life / p.maxLife;
            float a = age < 0.2f ? age / 0.2f : (age > 0.75f ? (1f - age) / 0.25f : 1f);
            Vec3 pos = p.prev.lerp(p.pos, pt).subtract(cam);
            int col = ColorUtil.multAlpha(p.color, Math.max(0f, Math.min(1f, a)));
            billboard(stack, buf, GLOW, pos, rot, p.size * 1.7f, ColorUtil.multAlpha(col, 0.25f));
            billboardSpin(stack, buf, p.tex, pos, rot, p.size, col, p.spin);
        }
    }

    private void billboard(PoseStack stack, MultiBufferSource.BufferSource buf, Identifier tex, Vec3 rel, Quaternionf rot, float half, int color) {
        stack.pushPose();
        stack.translate(rel.x, rel.y, rel.z);
        stack.mulPose(rot);
        quad(buf, stack.last(), tex, half, color);
        stack.popPose();
    }

    private void billboardSpin(PoseStack stack, MultiBufferSource.BufferSource buf, Identifier tex, Vec3 rel, Quaternionf rot, float half, int color, float spin) {
        stack.pushPose();
        stack.translate(rel.x, rel.y, rel.z);
        stack.mulPose(rot);
        stack.mulPose(Axis.ZP.rotationDegrees(spin));
        quad(buf, stack.last(), tex, half, color);
        stack.popPose();
    }

    private void quad(MultiBufferSource.BufferSource buf, PoseStack.Pose pose, Identifier tex, float half, int color) {
        if (half <= 0.0001f || ColorUtil.getAlpha(color) <= 0) {
            return;
        }
        RenderType type = ClientPipelines.WORLD_PARTICLES_GLOW.apply(tex);
        used.add(type);
        VertexConsumer c = buf.getBuffer(type);
        WorldVertex.textured(c, pose, -half, -half, 0, 0, 0, color);
        WorldVertex.textured(c, pose, half, -half, 0, 1, 0, color);
        WorldVertex.textured(c, pose, half, half, 0, 1, 1, color);
        WorldVertex.textured(c, pose, -half, half, 0, 0, 1, color);
    }

    private Identifier particleTex() {
        return switch (particleTexture.getValue()) {
            case "Star" -> STAR;
            case "Heart" -> HEART;
            case "Snow" -> SNOW;
            default -> FIREFLY;
        };
    }

    private int color(int offset) {
        if (colorMode.is("Custom")) {
            return customColor.getValue().getRGB();
        }
        float hue = ((System.currentTimeMillis() + offset * 20L) % 3000L) / 3000f;
        return Color.HSBtoRGB(hue, 0.7f, 1f) | 0xFF000000;
    }

    private static final class TrailParticle {
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        int life;
        final int maxLife;
        final float size;
        final int color;
        final Identifier tex;
        float spin;
        final float spinSpeed;

        TrailParticle(Vec3 pos, Vec3 vel, int life, float size, int color, Identifier tex, float spin, float spinSpeed) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
            this.tex = tex;
            this.spin = spin;
            this.spinSpeed = spinSpeed;
        }
    }
}
