package polaris.api.module.impl.visual.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
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
import polaris.utils.repository.friend.FriendUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public final class DashTrailEffect implements IMinecraft {
    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath("cataclysm", "textures/masks/circle.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("cataclysm", "textures/masks/glow.png");
    private static final Identifier BLOOM = Identifier.fromNamespaceAndPath("cataclysm", "textures/features/dashtrail/dashbloom.png");

    private static final double MIN_SPEED = 0.06D;
    private static final int MAX_POINTS = 480;
    private static final int CLIENT_A = ColorUtil.rgba(127, 242, 255, 255);
    private static final int CLIENT_B = ColorUtil.rgba(255, 50, 150, 255);

    private final Random random = new Random();
    private final List<TrailPoint> points = new ArrayList<>();
    private final Map<Integer, Vec3> lastPositions = new HashMap<>();
    private final Set<Integer> seenIds = new HashSet<>();
    private final Set<RenderType> usedTypes = new HashSet<>();

    
    private final BooleanSetting enabled = new BooleanSetting(
            "Dash Trail", "3D circular trail behind moving players.", false);

    private final BooleanSetting self = new BooleanSetting(
            "Dash Self", "Draw trail for you.", true);
    private final BooleanSetting players = new BooleanSetting(
            "Dash Players", "Draw trail for other players.", false);
    private final BooleanSetting friends = new BooleanSetting(
            "Dash Friends", "Draw trail for friends.", true);

    private final ModeSetting colorMode = new ModeSetting(
            "Dash Color Mode", "Trail color mode.",
            "Client", "Client", "Custom", "Rainbow", "Random"
    );
    private final ColorSetting customColor = new ColorSetting(
            "Dash Color", "Custom trail color.", new Color(140, 200, 255, 255));

    private final NumberSetting lifetimeMs = new NumberSetting(
            "Dash Lifetime", "How long each circle lives (ms).", 900.0, 200.0, 2500.0, 50.0);
    private final NumberSetting startSize = new NumberSetting(
            "Dash Start Size", "Circle size at spawn.", 0.38, 0.12, 1.2, 0.02);
    private final NumberSetting endSize = new NumberSetting(
            "Dash End Size", "Circle size at death (smaller = taper).", 0.06, 0.01, 0.6, 0.01);
    private final NumberSetting density = new NumberSetting(
            "Dash Density", "How many circles spawn while moving.", 1.0, 0.3, 3.0, 0.1);
    private final NumberSetting spread = new NumberSetting(
            "Dash Spread", "Random offset around the body.", 0.12, 0.0, 0.45, 0.01);
    private final NumberSetting height = new NumberSetting(
            "Dash Height", "Vertical spawn bias (0 = feet, 1 = head).", 0.45, 0.0, 1.0, 0.05);
    private final NumberSetting maxDistance = new NumberSetting(
            "Dash Max Distance", "Max distance to render others.", 30.0, 10.0, 100.0, 1.0);

    private final BooleanSetting softGlow = new BooleanSetting(
            "Dash Soft Glow", "Extra bloom around circles.", true);
    private final BooleanSetting doubleLayer = new BooleanSetting(
            "Dash Double Layer", "Core circle + outer glow.", true);
    private final NumberSetting opacity = new NumberSetting(
            "Dash Opacity", "Overall trail opacity.", 0.9, 0.2, 1.0, 0.05);

    public DashTrailEffect() {
        colorMode.visibleWhen(() -> enabled.getValue() && anyTarget());
        customColor.visibleWhen(() -> enabled.getValue() && (colorMode.is("Custom") && anyTarget()));
        lifetimeMs.visibleWhen(() -> enabled.getValue() && anyTarget());
        startSize.visibleWhen(() -> enabled.getValue() && anyTarget());
        endSize.visibleWhen(() -> enabled.getValue() && anyTarget());
        density.visibleWhen(() -> enabled.getValue() && anyTarget());
        spread.visibleWhen(() -> enabled.getValue() && anyTarget());
        height.visibleWhen(() -> enabled.getValue() && anyTarget());
        maxDistance.visibleWhen(() -> enabled.getValue() && (players.getValue() || friends.getValue()));
        softGlow.visibleWhen(() -> enabled.getValue() && anyTarget());
        doubleLayer.visibleWhen(() -> enabled.getValue() && anyTarget());
        opacity.visibleWhen(() -> enabled.getValue() && anyTarget());
        self.visibleWhen(enabled::getValue);
        players.visibleWhen(enabled::getValue);
        friends.visibleWhen(enabled::getValue);

    }

    
    public List<Setting<?>> settings() {
        return List.of(enabled, self, players, friends, colorMode, customColor, lifetimeMs, startSize, endSize, density, spread, height, maxDistance, softGlow, doubleLayer, opacity);
    }

    public boolean isOn() {
        return enabled.getValue();
    }

    public void onDisable() {
        points.clear();
        lastPositions.clear();
        usedTypes.clear();
    }

    public void onTick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            points.clear();
            lastPositions.clear();
            return;
        }

        long now = System.currentTimeMillis();
        points.removeIf(p -> p.dead(now));

        if (!anyTarget()) {
            lastPositions.clear();
            return;
        }

        seenIds.clear();
        for (Player player : client.level.players()) {
            if (!isTarget(player, client)) {
                continue;
            }
            seenIds.add(player.getId());
            track(player, now);
        }
        lastPositions.keySet().removeIf(id -> !seenIds.contains(id));

        
        for (TrailPoint p : points) {
            p.tick();
        }
        trim();
    }

    public void onWorldRender(WorldRenderEvent event) {
        long now = System.currentTimeMillis();
        points.removeIf(p -> p.dead(now));
        if (points.isEmpty() || mc.gameRenderer == null) {
            return;
        }

        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Quaternionf camRot = mc.gameRenderer.getMainCamera().rotation();
        float pt = event.getPartialTicks();
        float globalAlpha = opacity.getFloat();

        usedTypes.clear();

        for (TrailPoint p : points) {
            float progress = p.progress(now); 
            float lifeAlpha = lifeAlpha(progress) * globalAlpha;
            if (lifeAlpha <= 0.02f) {
                continue;
            }

            
            float size = Mth.lerp(progress, startSize.getFloat(), endSize.getFloat());
            
            size *= p.scale;
            if (size <= 0.005f) {
                continue;
            }

            Vec3 pos = p.renderPos(pt);
            int color = ColorUtil.multAlpha(p.color, lifeAlpha);

            
            if (softGlow.getValue()) {
                float glowSize = size * (1.85f + 0.35f * (1f - progress));
                int glowCol = ColorUtil.multAlpha(ColorUtil.lerpColor(p.color, ColorUtil.WHITE, 0.15f), lifeAlpha * 0.28f);
                drawCircle(stack, provider, BLOOM, pos, cam, camRot, glowSize, glowCol);
            }

            
            if (doubleLayer.getValue()) {
                int outer = ColorUtil.multAlpha(p.color, lifeAlpha * 0.55f);
                drawCircle(stack, provider, GLOW, pos, cam, camRot, size * 1.15f, outer);
                int core = ColorUtil.multAlpha(ColorUtil.lerpColor(p.color, ColorUtil.WHITE, 0.35f), lifeAlpha);
                drawCircle(stack, provider, CIRCLE, pos, cam, camRot, size * 0.72f, core);
            } else {
                drawCircle(stack, provider, GLOW, pos, cam, camRot, size, color);
            }
        }

        for (RenderType type : usedTypes) {
            provider.endBatch(type);
        }
        usedTypes.clear();
    }

    private void track(Player player, long now) {
        Vec3 current = player.position();
        Vec3 previous = lastPositions.put(player.getId(), current);
        if (previous == null) {
            return;
        }

        Vec3 delta = current.subtract(previous);
        double speedXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (speedXZ < MIN_SPEED || delta.lengthSqr() > 36.0D) {
            return;
        }

        float dens = density.getFloat();
        int count = Mth.clamp((int) Math.ceil(delta.length() / MIN_SPEED * dens), 1, 20);
        int life = Math.max(100, Math.round(lifetimeMs.getFloat()));
        double spr = spread.getValue();
        double hFrac = height.getValue();
        double bodyH = player.getBbHeight();

        for (int i = 0; i < count; i++) {
            float t = count == 1 ? 0.5f : (float) i / (float) count;
            double x = current.x - delta.x * t + (random.nextDouble() - 0.5) * spr * 2.0;
            double y = current.y + bodyH * hFrac + (random.nextDouble() - 0.5) * spr * 0.8;
            double z = current.z - delta.z * t + (random.nextDouble() - 0.5) * spr * 2.0;

            
            Vec3 motion = delta.scale(0.02).add(
                    (random.nextDouble() - 0.5) * 0.01,
                    (random.nextDouble() - 0.3) * 0.008,
                    (random.nextDouble() - 0.5) * 0.01
            );

            float scale = 0.85f + random.nextFloat() * 0.3f;
            
            int pointLife = Math.round(life * (0.85f + random.nextFloat() * 0.3f));
            points.add(new TrailPoint(new Vec3(x, y, z), motion, nextColor(), pointLife, scale));
        }
        trim();
    }

    private void drawCircle(
            PoseStack stack,
            MultiBufferSource.BufferSource provider,
            Identifier texture,
            Vec3 pos,
            Vec3 cam,
            Quaternionf camRot,
            float diameter,
            int color
    ) {
        if (diameter <= 0.001f || ColorUtil.getAlpha(color) <= 0) {
            return;
        }
        RenderType type = ClientPipelines.WORLD_PARTICLES_GLOW.apply(texture);
        usedTypes.add(type);
        VertexConsumer vc = provider.getBuffer(type);

        float half = diameter * 0.5f;
        stack.pushPose();
        stack.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        
        stack.mulPose(camRot);
        PoseStack.Pose pose = stack.last();
        
        WorldVertex.textured(vc, pose, -half, -half, 0f, 0f, 0f, color);
        WorldVertex.textured(vc, pose, half, -half, 0f, 1f, 0f, color);
        WorldVertex.textured(vc, pose, half, half, 0f, 1f, 1f, color);
        WorldVertex.textured(vc, pose, -half, half, 0f, 0f, 1f, color);
        
        WorldVertex.textured(vc, pose, -half, half, 0f, 0f, 1f, color);
        WorldVertex.textured(vc, pose, half, half, 0f, 1f, 1f, color);
        WorldVertex.textured(vc, pose, half, -half, 0f, 1f, 0f, color);
        WorldVertex.textured(vc, pose, -half, -half, 0f, 0f, 0f, color);
        stack.popPose();
    }

    private boolean isTarget(Player player, Minecraft client) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (player == client.player) {
            return self.getValue();
        }
        double max = maxDistance.getValue();
        if (client.player.distanceToSqr(player) > max * max) {
            return false;
        }
        return FriendUtils.isFriend(player) ? friends.getValue() : players.getValue();
    }

    private boolean anyTarget() {
        return self.getValue() || players.getValue() || friends.getValue();
    }

    private int nextColor() {
        if (colorMode.is("Custom")) {
            return customColor.getValue().getRGB() | 0xFF000000;
        }
        if (colorMode.is("Rainbow")) {
            float hue = (System.currentTimeMillis() % 2000L) / 2000.0f;
            return Color.HSBtoRGB(hue, 0.85f, 1.0f) | 0xFF000000;
        }
        if (colorMode.is("Random")) {
            return Color.getHSBColor(random.nextFloat(), 0.9f, 1.0f).getRGB() | 0xFF000000;
        }
        
        float wave = (Mth.sin(System.currentTimeMillis() / 520.0f) + 1.0f) * 0.5f;
        return ColorUtil.lerpColor(CLIENT_A, CLIENT_B, wave);
    }

    private void trim() {
        int overflow = points.size() - MAX_POINTS;
        if (overflow > 0) {
            points.subList(0, overflow).clear();
        }
    }

    
    private static float lifeAlpha(float progress) {
        float p = Mth.clamp(progress, 0f, 1f);
        if (p < 0.08f) {
            return p / 0.08f;
        }
        if (p > 0.65f) {
            return 1f - (p - 0.65f) / 0.35f;
        }
        return 1f;
    }

    private static final class TrailPoint {
        private final long born = System.currentTimeMillis();
        private final int lifetime;
        private final int color;
        private final float scale;
        private Vec3 pos;
        private Vec3 prev;
        private Vec3 motion;

        private TrailPoint(Vec3 pos, Vec3 motion, int color, int lifetime, float scale) {
            this.pos = pos;
            this.prev = pos;
            this.motion = motion;
            this.color = color;
            this.lifetime = Math.max(50, lifetime);
            this.scale = scale;
        }

        private void tick() {
            prev = pos;
            
            motion = motion.scale(0.88);
            pos = pos.add(motion);
        }

        private Vec3 renderPos(float pt) {
            return prev.lerp(pos, Mth.clamp(pt, 0f, 1f));
        }

        private float progress(long now) {
            return Mth.clamp((now - born) / (float) lifetime, 0f, 1f);
        }

        private boolean dead(long now) {
            return progress(now) >= 1f;
        }
    }
}
