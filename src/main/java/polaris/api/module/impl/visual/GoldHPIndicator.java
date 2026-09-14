package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.WorldVertex;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class GoldHPIndicator extends Module {
    private static final Identifier HEART_TEXTURE = Identifier.fromNamespaceAndPath("cataclysm", "textures/particles/heart1.png");
    private static final Random RANDOM = new Random();
    private static GoldHPIndicator instance;

    private final NumberSetting heartSize = register(new NumberSetting("Size", "Heart particle size.", 0.25, 0.05, 0.8, 0.05));
    private final NumberSetting spawnRate = register(new NumberSetting("Spawn Rate", "Hearts per second per player.", 3.0, 0.5, 10.0, 0.5));
    private final NumberSetting lifetime = register(new NumberSetting("Lifetime", "Heart lifetime ms.", 1500, 500, 4000, 100));
    private final NumberSetting spread = register(new NumberSetting("Spread", "Horizontal spread.", 0.4, 0.0, 1.0, 0.05));
    private final NumberSetting riseSpeed = register(new NumberSetting("Rise Speed", "Upward speed.", 0.03, 0.005, 0.1, 0.005));

    private final List<HeartParticle> particles = new ArrayList<>();
    private long lastTickTime = System.currentTimeMillis();

    public GoldHPIndicator() {
        super("GoldHPIndicator", "Golden hearts on players with absorption.", ModuleCategory.VISUAL);
        instance = this;
    }

    public static GoldHPIndicator getInstance() {
        return instance;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastTickTime) / 1000.0f);
        lastTickTime = now;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player != mc.player && player.isAlive()) {
                float absorption = player.getAbsorptionAmount();
                if (absorption > 0) {
                    float rate = spawnRate.getFloat();
                    float chance = rate * dt;
                    if (RANDOM.nextFloat() < chance) {
                        spawnHeart(player);
                    }
                }
            }
        }

        Iterator<HeartParticle> it = particles.iterator();
        while (it.hasNext()) {
            HeartParticle p = it.next();
            p.age += (long) (dt * 1000);
            p.y += riseSpeed.getFloat() * dt * 60;
            p.x += p.motionX * dt;
            p.z += p.motionZ * dt;
            if (p.age >= p.lifetime) {
                it.remove();
            }
        }
    }

    private void spawnHeart(LivingEntity entity) {
        double x = entity.getX() + (RANDOM.nextDouble() - 0.5) * spread.getValue() * 2;
        double y = entity.getY() + entity.getBbHeight() * 0.8 + RANDOM.nextDouble() * 0.3;
        double z = entity.getZ() + (RANDOM.nextDouble() - 0.5) * spread.getValue() * 2;
        double motionX = (RANDOM.nextDouble() - 0.5) * 0.008;
        double motionZ = (RANDOM.nextDouble() - 0.5) * 0.008;
        long lt = lifetime.getValue().longValue() + RANDOM.nextLong(500);
        particles.add(new HeartParticle(x, y, z, motionX, motionZ, lt, heartSize.getFloat()));
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (particles.isEmpty() || mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        Quaternionf camRot = camera.rotation();

        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        for (HeartParticle p : particles) {
            float progress = Math.min(1.0f, p.age / (float) p.lifetime);
            int alpha = (int) (255 * (1.0f - progress));
            if (alpha <= 0) continue;
            int color = ColorUtil.rgba(255, 215, 0, alpha);

            float scale = p.size * (1.0f - progress * 0.3f);

            stack.pushPose();
            stack.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            stack.mulPose(camRot);
            stack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            PoseStack.Pose pose = stack.last();

            RenderType type = ClientPipelines.WORLD_PARTICLES_GLOW.apply(HEART_TEXTURE);
            VertexConsumer consumer = provider.getBuffer(type);

            WorldVertex.textured(consumer, pose, -scale, -scale, 0, 0, 0, color);
            WorldVertex.textured(consumer, pose, scale, -scale, 0, 1, 0, color);
            WorldVertex.textured(consumer, pose, scale, scale, 0, 1, 1, color);
            WorldVertex.textured(consumer, pose, -scale, scale, 0, 0, 1, color);

            stack.popPose();
        }
    }

    private static final class HeartParticle {
        double x, y, z;
        double motionX, motionZ;
        long age, lifetime;
        float size;

        HeartParticle(double x, double y, double z, double motionX, double motionZ, long lifetime, float size) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.motionX = motionX;
            this.motionZ = motionZ;
            this.lifetime = lifetime;
            this.size = size;
        }
    }
}

