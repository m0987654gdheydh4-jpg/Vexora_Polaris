package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.AttackEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.Render3D;
import polaris.utils.render.WorldVertex;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.particles.WireMesh;
import polaris.utils.render.pipeline.ClientPipelines;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class Particles extends Module {

    private static final Identifier GLOWBOOST =
            Identifier.fromNamespaceAndPath("cataclysm", "textures/features/jumpcircle/glowboost.png");
    private static final Identifier DASH_BLOOM =
            Identifier.fromNamespaceAndPath("cataclysm", "textures/world/dashbloom.png");

    private static final int[] CUBE_EDGES = {
            0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 1, 5, 2, 6, 3, 7, 4, 5, 5, 6, 6, 7, 7, 4
    };

    private enum MeshEffect {
        CUBES("Cubes", 0.60f, 0.60f, 0.60f, 6f, 6f, 6f, WireMesh::unitCube),
        PYRAMIDS("Pyramids", 0.75f, 0.65f, 0.55f, 7f, 7f, 7f, WireMesh::unitPyramid),
        TOTEM("Totem", 0.78f, 0.75f, 0.35f, 5f, 8f, 5f,
                () -> WireMesh.loadClasspath("/assets/cataclysm/models/particles/totem_undying.obj", false, -0.75f)),
        CRESCENT("Crescent", 0.72f, 0.70f, 0.45f, 6f, 6f, 6f, WireMesh::proceduralCrescent),
        HEART("Heart", 0.70f, 0.70f, 0.45f, 6f, 6f, 6f, MeshEffect::loadHeart),
        STARS("Stars", 0.90f, 0.70f, 0.45f, 6f, 6f, 6f,
                () -> WireMesh.loadClasspath("/assets/cataclysm/models/particles/fire_nether_star.obj", true, 0f)),
        ROCK("Rock", 0.85f, 0.70f, 0.45f, 6f, 6f, 6f,
                () -> WireMesh.loadClasspath("/assets/cataclysm/models/particles/meteor.obj", true, 0f)),
        OCTAHEDRON("Octahedron", 0.70f, 0.70f, 0.45f, 6f, 6f, 6f, WireMesh::proceduralOctahedron),
        TETRAHEDRON("Tetrahedron", 0.72f, 0.65f, 0.55f, 7f, 7f, 7f, WireMesh::proceduralTetrahedron),
        ICOSAHEDRON("Icosahedron", 0.72f, 0.70f, 0.45f, 6f, 6f, 6f, WireMesh::proceduralIcosahedron),
        TORUS("Torus", 0.80f, 0.70f, 0.45f, 6f, 6f, 6f, () -> WireMesh.proceduralTorus(16, 6)),
        HELIX("Helix", 0.80f, 0.70f, 0.45f, 3f, 8f, 3f, WireMesh::proceduralHelix),
        GYRO("Gyro", 0.80f, 0.70f, 0.45f, 8f, 8f, 8f, WireMesh::proceduralGyro),
        GEM("Gem", 0.75f, 0.70f, 0.45f, 4f, 9f, 4f, WireMesh::proceduralGem);

        private static final MeshEffect[] VALUES = values();

        private final String displayName;
        private final float base;
        private final float growBase;
        private final float growAge;
        private final float rx;
        private final float ry;
        private final float rz;
        private final Supplier<WireMesh> loader;

        MeshEffect(String displayName, float base, float growBase, float growAge,
                   float rx, float ry, float rz, Supplier<WireMesh> loader) {
            this.displayName = displayName;
            this.base = base;
            this.growBase = growBase;
            this.growAge = growAge;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.loader = loader;
        }

        private static WireMesh loadHeart() {
            WireMesh mesh = WireMesh.loadClasspath("/assets/cataclysm/models/particles/heart.gltf", true, 0f);
            return mesh == null || mesh.isEmpty() ? WireMesh.proceduralHeart() : mesh;
        }

        static String[] displayNames() {
            String[] names = new String[VALUES.length];
            for (int i = 0; i < VALUES.length; i++) {
                names[i] = VALUES[i].displayName;
            }
            return names;
        }
    }

    private static Particles instance;

    private final MultiModeSetting effects = register(new MultiModeSetting(
            "Effects", "",
            effectOptions(),
            "Meteor", "Cubes", "Stars"));

    private final NumberSetting count = register(new NumberSetting("Count", "", 14.0, 1.0, 80.0, 1.0));
    private final NumberSetting lifetime = register(new NumberSetting("Lifetime", "", 1600.0, 500.0, 5000.0, 100.0));
    private final NumberSetting size = register(new NumberSetting("Size", "", 0.45, 0.1, 1.5, 0.05));
    private final NumberSetting radius = register(new NumberSetting("Radius", "", 8.0, 0.5, 40.0, 0.5));
    private final NumberSetting tailLength = register(new NumberSetting("Tail Length", "", 0.3, 0.05, 0.7, 0.05));
    private final NumberSetting glow = register(new NumberSetting("Glow", "", 1.0, 0.0, 3.0, 0.1));

    private final BooleanSetting hit = register(new BooleanSetting("Hit Particles", "", true));
    private final MultiModeSetting hitTypes = register(new MultiModeSetting(
            "Hit Types", "",
            new String[]{"Cubes", "Diamond", "Triangle"}, "Cubes", "Diamond"));
    private final ModeSetting hitPhysics = register(new ModeSetting("Hit Physics", "", "Both", "Drop", "Fly", "Both"));
    private final NumberSetting hitCount = register(new NumberSetting("Hit Count", "", 10.0, 1.0, 20.0, 1.0));
    private final NumberSetting hitLifetime = register(new NumberSetting("Hit Lifetime", "", 1000.0, 100.0, 3000.0, 50.0));
    private final NumberSetting hitSpeed = register(new NumberSetting("Hit Speed", "", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting hitScale = register(new NumberSetting("Hit Scale", "", 1.0, 0.5, 1.5, 0.1));
    private final BooleanSetting hitOnlyCrit = register(new BooleanSetting("Hit Only Crit", "", false));
    private final BooleanSetting hitBounce = register(new BooleanSetting("Hit Bounce", "", false));

    private final ModeSetting colorMode = register(new ModeSetting("Color", "", "Rainbow", "Rainbow", "Custom"));
    private final ColorSetting customColor = register(new ColorSetting("Custom Color", "", new Color(120, 200, 255, 255)));

    private final List<MeteorP> meteors = new ArrayList<>();
    private final Map<MeshEffect, List<OrbitP>> orbits = new EnumMap<>(MeshEffect.class);
    private final Map<MeshEffect, WireMesh> meshes = new EnumMap<>(MeshEffect.class);
    private final List<HitP> hits = new ArrayList<>();
    private final Set<RenderType> used = new HashSet<>();
    private final Random rng = new Random();
    private final Matrix3f rot = new Matrix3f();
    private final Vector3f tmpA = new Vector3f();
    private final Vector3f tmpB = new Vector3f();
    private final Vector3f[] cubeVerts = new Vector3f[8];

    private boolean meshesLoaded;

    public Particles() {
        super("Particles", "", ModuleCategory.VISUAL);
        hitTypes.visibleWhen(hit::getValue);
        hitPhysics.visibleWhen(hit::getValue);
        hitCount.visibleWhen(hit::getValue);
        hitLifetime.visibleWhen(hit::getValue);
        hitSpeed.visibleWhen(hit::getValue);
        hitScale.visibleWhen(hit::getValue);
        hitOnlyCrit.visibleWhen(hit::getValue);
        hitBounce.visibleWhen(() -> hit.getValue() && !hitPhysics.is("Fly"));
        tailLength.visibleWhen(() -> effects.isSelected("Meteor"));
        customColor.visibleWhen(() -> colorMode.is("Custom"));
        for (int i = 0; i < 8; i++) {
            cubeVerts[i] = new Vector3f();
        }
        instance = this;
    }

    public static Particles getInstance() {
        return instance;
    }

    private static String[] effectOptions() {
        String[] meshNames = MeshEffect.displayNames();
        String[] all = new String[meshNames.length + 1];
        all[0] = "Meteor";
        System.arraycopy(meshNames, 0, all, 1, meshNames.length);
        return all;
    }

    private WireMesh meshFor(MeshEffect effect) {
        return meshes.computeIfAbsent(effect, e -> {
            WireMesh mesh = e.loader.get();
            return mesh == null ? WireMesh.unitCube() : mesh;
        });
    }

    private List<OrbitP> orbitsFor(MeshEffect effect) {
        return orbits.computeIfAbsent(effect, e -> new ArrayList<>());
    }

    private boolean isSelected(MeshEffect effect) {
        return effects.isSelected(effect.displayName);
    }

    private void ensureMeshes() {
        if (meshesLoaded) {
            return;
        }
        meshesLoaded = true;
        for (MeshEffect effect : MeshEffect.VALUES) {
            if (isSelected(effect)) {
                meshFor(effect);
            }
        }
    }

    @Override
    protected void onDisable() {
        clearAll();
    }

    private void clearAll() {
        meteors.clear();
        orbits.clear();
        hits.clear();
    }

    @SubscribeEvent
    private void onAttack(AttackEvent e) {
        if (!hit.getValue() || mc.player == null) {
            return;
        }
        if (hitOnlyCrit.getValue() && (mc.player.fallDistance <= 0 || mc.player.onGround())) {
            return;
        }
        Entity target = e.getTarget();
        if (!(target instanceof LivingEntity) || target == mc.player) {
            return;
        }
        Vec3 hitPos = target.getBoundingBox().getCenter();
        List<HitKind> kinds = new ArrayList<>();
        if (hitTypes.isSelected("Cubes")) kinds.add(HitKind.CUBE);
        if (hitTypes.isSelected("Diamond")) kinds.add(HitKind.DIAMOND);
        if (hitTypes.isSelected("Triangle")) kinds.add(HitKind.TRIANGLE);
        if (kinds.isEmpty()) {
            return;
        }
        int n = hitCount.getValue().intValue();
        long life = hitLifetime.getValue().longValue();
        float speed = hitSpeed.getFloat() * 2f;
        float scale = 0.1f + size.getFloat() * 0.2f * hitScale.getFloat();
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            HitKind kind = kinds.get(i % kinds.size());
            boolean drop = hitPhysics.is("Drop") || (hitPhysics.is("Both") && rng.nextBoolean());
            Vec3 vel;
            if (drop) {
                vel = new Vec3(rand(-1, 1), 0.2 + rng.nextDouble() * 0.8, rand(-1, 1)).normalize().scale(speed * 0.08);
            } else {
                vel = new Vec3(rand(-1, 1), rand(-0.75, 0.75), rand(-1, 1)).normalize().scale(speed * 0.08);
            }
            hits.add(new HitP(hitPos, vel, kind, color(i * 17), scale, now, life, drop && hitBounce.getValue(),
                    rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1, rng.nextFloat() * 2 - 1));
        }
    }

    @SubscribeEvent
    private void onPacket(PacketEvent e) {
        if (!e.isReceive() || mc.level == null || !isSelected(MeshEffect.TOTEM)) {
            return;
        }
        if (e.getPacket() instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 35) {
            Entity ent = packet.getEntity(mc.level);
            if (ent instanceof LivingEntity living) {
                long now = System.currentTimeMillis();
                Vec3 c = living.position().add(0, living.getBbHeight() * 0.5, 0);
                List<OrbitP> list = orbitsFor(MeshEffect.TOTEM);
                for (int i = 0; i < 12; i++) {
                    list.add(spawnOrbit(c, now, MeshEffect.TOTEM.rx, MeshEffect.TOTEM.ry, MeshEffect.TOTEM.rz));
                }
            }
        }
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent e) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            return;
        }
        ensureMeshes();
        long now = System.currentTimeMillis();
        maintainAmbient(now);
        updateHits(now);

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = Render3D.lastCameraPos;
        PoseStack stack = e.getStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        if (glow.getFloat() > 0.01f) {
            renderWireframeGlows(stack, buf, camera, cam, now);
        }

        if (effects.isSelected("Meteor") && !meteors.isEmpty()) {
            renderMeteors(stack, buf, camera, cam, now);
        }

        for (MeshEffect effect : MeshEffect.VALUES) {
            if (!isSelected(effect)) {
                continue;
            }
            WireMesh mesh = meshFor(effect);
            if (!mesh.isEmpty()) {
                renderMesh(orbitsFor(effect), mesh, now, size.getFloat() * effect.base,
                        effect.growBase, effect.growAge);
            }
        }
        if (hit.getValue() && !hits.isEmpty()) {
            renderHits(stack, buf, camera, cam, now);
        }
    }

    private void renderWireframeGlows(PoseStack stack, MultiBufferSource.BufferSource buf,
                                       Camera camera, Vec3 cam, long now) {
        if (glow.getFloat() <= 0.01f) {
            return;
        }
        float s = size.getFloat();
        float intensity = Mth.clamp(glow.getFloat(), 0f, 3f);

        RenderType bloomType = ClientPipelines.BLOOM_ESP.apply(DASH_BLOOM);
        VertexConsumer bloom = buf.getBuffer(bloomType);
        for (MeshEffect effect : MeshEffect.VALUES) {
            if (isSelected(effect)) {
                glowOrbitList(stack, bloom, camera, cam, orbitsFor(effect), now, s * effect.base, intensity);
            }
        }
        buf.endBatch(bloomType);
    }

    private void glowOrbitList(PoseStack stack, VertexConsumer vc,
                                Camera camera, Vec3 cam, List<OrbitP> list, long now,
                                float baseScale, float intensity) {
        for (OrbitP p : list) {
            float age = (now - p.born) / (float) p.life;
            if (age >= 1f) continue;
            float fade = ageFade(age);
            if (fade <= 0.01f) continue;
            Vec3 pos = aboveGround(p.posAt(age), 0.25);
            float particleSize = baseScale * (0.55f + age * 0.15f);
            float bloomSize = 4f * particleSize;
            int alphaI = clampByte(Math.round(255f * fade * 0.4f * Math.min(1f, intensity)));
            if (alphaI == 0) continue;
            int bloomCol = ColorUtil.withAlpha(p.color, alphaI);
            drawCamBillboard(stack, vc, camera, cam, pos, bloomSize, bloomCol);
        }
    }

    private static void drawCamBillboard(PoseStack stack, VertexConsumer vc, Camera camera, Vec3 cam,
                                          Vec3 world, float size, int color) {
        if (ColorUtil.getAlpha(color) <= 0 || size <= 0.001f) {
            return;
        }
        stack.pushPose();
        stack.translate(
                (float) (world.x - cam.x),
                (float) (world.y - cam.y),
                (float) (world.z - cam.z));
        stack.mulPose(camera.rotation());
        PoseStack.Pose pose = stack.last();
        float h = size * 0.5f;
        vc.addVertex(pose, -h, -h, 0).setUv(0, 0).setColor(color);
        vc.addVertex(pose, h, -h, 0).setUv(1, 0).setColor(color);
        vc.addVertex(pose, h, h, 0).setUv(1, 1).setColor(color);
        vc.addVertex(pose, -h, h, 0).setUv(0, 1).setColor(color);
        stack.popPose();
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void maintainAmbient(long now) {
        Vec3 player = mc.player.position();
        double r = Math.max(1.5, radius.getFloat());
        int n = Math.max(1, count.getValue().intValue());
        long lifeBase = lifetime.getValue().longValue();

        if (effects.isSelected("Meteor")) {
            cullMeteors(now);
            while (meteors.size() < n) {
                meteors.add(spawnMeteor(player, r, now, lifeBase));
            }
        } else {
            meteors.clear();
        }

        for (MeshEffect effect : MeshEffect.VALUES) {
            maintainOrbit(orbitsFor(effect), isSelected(effect), player, r, n, now,
                    effect.rx, effect.ry, effect.rz);
        }
    }

    private void maintainOrbit(List<OrbitP> list, boolean on, Vec3 center, double r, int n, long now, float rx, float ry, float rz) {
        if (!on) {
            list.clear();
            return;
        }
        list.removeIf(p -> now - p.born > p.life);
        while (list.size() > n) {
            list.remove(0);
        }
        while (list.size() < n) {
            list.add(spawnOrbit(center, now, rx, ry, rz));
        }
    }

    private void cullMeteors(long now) {
        meteors.removeIf(m -> now - m.born > m.life);
        while (meteors.size() > count.getValue().intValue()) {
            meteors.remove(0);
        }
    }

    private MeteorP spawnMeteor(Vec3 center, double rMax, long now, long lifeBase) {
        double a = rng.nextDouble() * Math.PI * 2;
        double p = Math.acos(2 * rng.nextDouble() - 1);
        double rr = 1.5 + rng.nextDouble() * Math.max(0.5, rMax - 1.5);
        double sp = Math.sin(p);
        Vec3 start = aboveGround(new Vec3(
                center.x + sp * Math.cos(a) * rr,
                center.y + 1.0 + Math.cos(p) * rr,
                center.z + sp * Math.sin(a) * rr), 0.35);
        double va = rng.nextDouble() * Math.PI * 2;
        double vp = Math.acos(2 * rng.nextDouble() - 1);
        double speed = 4.0 + rng.nextDouble() * 7.0;
        double sv = Math.sin(vp);
        Vec3 vel = new Vec3(
                sv * Math.cos(va) * speed,
                Math.cos(vp) * speed * 0.55 - 1.2,
                sv * Math.sin(va) * speed);
        long life = Math.max(80L, Math.round(lifeBase * (0.75 + rng.nextFloat() * 0.5)));
        return new MeteorP(start, vel, now, life, color(rng.nextInt(180)));
    }

    private OrbitP spawnOrbit(Vec3 center, long now, float rx, float ry, float rz) {
        double a = rng.nextDouble() * Math.PI * 2;
        double p = Math.acos(2 * rng.nextDouble() - 1);
        double rMin = 1.5;
        double rMax = Math.max(1.6, radius.getFloat());
        double rr = rMin + rng.nextDouble() * (rMax - rMin);
        double sp = Math.sin(p);
        Vec3 start = aboveGround(new Vec3(
                center.x + sp * Math.cos(a) * rr,
                center.y + 1.0 + Math.cos(p) * rr,
                center.z + sp * Math.sin(a) * rr), 0.35);
        double va = rng.nextDouble() * Math.PI * 2;
        double vp = Math.acos(2 * rng.nextDouble() - 1);
        double speed = 1.6 + rng.nextDouble() * 1.8;
        double sv = Math.sin(vp);
        double vy = Math.abs(Math.cos(vp) * speed) * 0.85 + 0.4;
        Vec3 vel = new Vec3(sv * Math.cos(va) * speed, vy, sv * Math.sin(va) * speed);
        long life = Math.max(50L, Math.round(lifetime.getFloat() * (0.75 + rng.nextFloat() * 0.5)));
        return new OrbitP(start, vel, now, life, color(rng.nextInt(180)),
                (rng.nextFloat() - 0.5f) * rx,
                (rng.nextFloat() - 0.5f) * ry,
                (rng.nextFloat() - 0.5f) * rz);
    }

    private double groundY(double x, double z) {
        if (mc.level == null || mc.player == null) {
            return 0.0;
        }
        try {
            BlockPos top = mc.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(x, 0, z));
            return top.getY();
        } catch (Throwable t) {
            return mc.player.getY();
        }
    }

    private Vec3 aboveGround(Vec3 p, double pad) {
        double minY = groundY(p.x, p.z) + pad;
        if (p.y < minY) {
            return new Vec3(p.x, minY, p.z);
        }
        return p;
    }

    private void renderMeteors(PoseStack stack, MultiBufferSource.BufferSource buf,
                                Camera camera, Vec3 cam, long now) {
        float baseSize = size.getFloat();
        float step = Math.max(0.025f, baseSize * 0.24f);
        float tailFactor = Math.max(0.05f, tailLength.getFloat());

        RenderType type = ClientPipelines.BLOOM_ESP.apply(GLOWBOOST);
        VertexConsumer vc = buf.getBuffer(type);

        for (MeteorP m : meteors) {
            float age = (now - m.born) / (float) m.life;
            if (age >= 1f) {
                continue;
            }
            Vec3 pos = m.start.add(m.vel.scale(age));
            m.pushHistory(pos, now);

            float fade = ageFade(age);
            long trailMs = Math.max(1L, (long) (m.life * tailFactor));

            int last = m.histCount - 1;
            while (last > 0 && now - m.histTime[last] > trailMs) {
                last--;
            }
            for (int i = last; i > 0; i--) {
                double x0 = m.histX[i], y0 = m.histY[i], z0 = m.histZ[i];
                double x1 = m.histX[i - 1], y1 = m.histY[i - 1], z1 = m.histZ[i - 1];
                double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int steps = Math.min(20, Math.max(1, (int) Math.ceil(dist / step)));
                for (int s = 0; s < steps; s++) {
                    float t = (float) s / steps;
                    long ts = m.histTime[i]
                            + (long) ((m.histTime[i - 1] - m.histTime[i]) * (double) t);
                    float trailAge = Mth.clamp((now - ts) / (float) trailMs, 0f, 1f);
                    float ta = (1f - trailAge) * (1f - trailAge);
                    if (ta <= 0f) {
                        continue;
                    }
                    float sz = baseSize * (0.34f + (float) Math.sqrt(ta) * 0.86f);
                    int col = ColorUtil.withAlpha(m.color,
                            clampByte(Math.round(255f * fade * ta * 0.5f)));
                    drawMeteorQuad(stack, vc, camera, cam,
                            new Vec3(x0 + dx * t, y0 + dy * t, z0 + dz * t), sz, col, false);
                }
            }
            if (m.histCount > 0) {
                Vec3 head = new Vec3(m.histX[0], m.histY[0], m.histZ[0]);
                int col = ColorUtil.withAlpha(m.color, clampByte(Math.round(255f * fade)));
                drawMeteorQuad(stack, vc, camera, cam, head, baseSize, col, true);
            }
        }
        buf.endBatch(type);
    }

    private static void drawMeteorQuad(PoseStack stack, VertexConsumer vc, Camera camera, Vec3 cam,
                                        Vec3 world, float size, int color, boolean head) {
        int outerColor = head ? ColorUtil.multAlpha(color, 0.45f) : color;
        drawCamBillboard(stack, vc, camera, cam, world, size * 1.9f, outerColor);
        if (head) {
            drawCamBillboard(stack, vc, camera, cam, world, size, color);
        }
    }

    private void renderMesh(List<OrbitP> list, WireMesh mesh, long now, float baseScale,
                             float growBase, float growAge) {
        float[] edges = mesh.edges();
        if (edges.length < 6) {
            return;
        }
        for (OrbitP p : list) {
            float age = (now - p.born) / (float) p.life;
            if (age >= 1f) continue;
            Vec3 pos = aboveGround(p.posAt(age), 0.25);
            float fade = ageFade(age);
            float s = baseScale * (growBase + age * growAge);
            rot.identity().rotateY(p.ry * age).rotateX(p.rx * age).rotateZ(p.rz * age);
            int col = ColorUtil.multAlpha(p.color, fade);
            float g = Math.max(0.5f, glow.getFloat());
            float width = 1.2f + g * 0.4f;
            for (int i = 0; i + 5 < edges.length; i += 6) {
                tmpA.set(edges[i] * s, edges[i + 1] * s, edges[i + 2] * s);
                tmpB.set(edges[i + 3] * s, edges[i + 4] * s, edges[i + 5] * s);
                rot.transform(tmpA);
                rot.transform(tmpB);
                Render3D.drawLine(
                        new Vec3(pos.x + tmpA.x, pos.y + tmpA.y, pos.z + tmpA.z),
                        new Vec3(pos.x + tmpB.x, pos.y + tmpB.y, pos.z + tmpB.z),
                        col, width, true);
            }
        }
    }

    private void line(Vector3f a, Vector3f b, Vec3 origin, int color) {
        Render3D.drawLine(
                new Vec3(origin.x + a.x, origin.y + a.y, origin.z + a.z),
                new Vec3(origin.x + b.x, origin.y + b.y, origin.z + b.z),
                color, 1.6f, true);
    }

    private void updateHits(long now) {
        Iterator<HitP> it = hits.iterator();
        while (it.hasNext()) {
            HitP h = it.next();
            if (now - h.born >= h.life) {
                it.remove();
                continue;
            }
            h.prev = h.pos;
            if (h.bounce) {
                if (solid(h.pos.x, h.pos.y, h.pos.z + h.vel.z)) {
                    h.vel = new Vec3(h.vel.x, h.vel.y, -h.vel.z * 0.7);
                }
                if (solid(h.pos.x, h.pos.y + h.vel.y, h.pos.z)) {
                    h.vel = new Vec3(h.vel.x * 0.95, -h.vel.y * 0.55, h.vel.z * 0.95);
                }
                if (solid(h.pos.x + h.vel.x, h.pos.y, h.pos.z)) {
                    h.vel = new Vec3(-h.vel.x * 0.7, h.vel.y, h.vel.z);
                }
                h.vel = h.vel.scale(0.98).add(0, -0.003, 0);
            } else {
                h.vel = h.vel.scale(0.985).add(0, -0.0015, 0);
            }
            h.pos = h.pos.add(h.vel);
            double minY = groundY(h.pos.x, h.pos.z) + 0.2;
            if (h.pos.y < minY) {
                h.pos = new Vec3(h.pos.x, minY, h.pos.z);
                if (h.vel.y < 0) {
                    h.vel = new Vec3(h.vel.x * 0.9, h.bounce ? -h.vel.y * 0.45 : 0.0, h.vel.z * 0.9);
                }
            }
            h.spin += 0.12f;
        }
    }

    private void renderHits(PoseStack stack, MultiBufferSource.BufferSource buf,
                             Camera camera, Vec3 cam, long now) {
        float g = glow.getFloat();
        if (g > 0.01f) {
            RenderType bloomType = ClientPipelines.BLOOM_ESP.apply(DASH_BLOOM);
            VertexConsumer bloom = buf.getBuffer(bloomType);
            for (HitP h : hits) {
                float age = (now - h.born) / (float) h.life;
                float fade = ageFade(age);
                if (fade <= 0.01f) continue;
                float s = h.scale * (0.8f + age * 0.2f);
                int col = ColorUtil.withAlpha(h.color, clampByte(Math.round(255f * fade * 0.4f * Math.min(1f, g))));
                drawCamBillboard(stack, bloom, camera, cam, h.pos, 4f * s, col);
            }
            buf.endBatch(bloomType);
        }
        for (HitP h : hits) {
            float age = (now - h.born) / (float) h.life;
            float fade = ageFade(age);
            int col = ColorUtil.multAlpha(h.color, fade);
            float s = h.scale * (0.8f + age * 0.4f);
            rot.identity().rotateX(h.rx * h.spin).rotateY(h.ry * h.spin).rotateZ(h.rz * h.spin);
            switch (h.kind) {
                case CUBE -> {
                    float half = s * 0.5f;
                    for (int j = 0; j < 8; j++) {
                        cubeVerts[j].set((j & 1) == 0 ? -half : half, (j & 2) == 0 ? -half : half, (j & 4) == 0 ? -half : half);
                        rot.transform(cubeVerts[j]);
                    }
                    for (int e = 0; e < CUBE_EDGES.length; e += 2) {
                        line(cubeVerts[CUBE_EDGES[e]], cubeVerts[CUBE_EDGES[e + 1]], h.pos, col);
                    }
                }
                case DIAMOND -> {
                    Vector3f top = new Vector3f(0, s * 0.56f, 0); rot.transform(top);
                    Vector3f bot = new Vector3f(0, -s * 0.56f, 0); rot.transform(bot);
                    Vector3f px = new Vector3f(s * 0.4f, 0, 0); rot.transform(px);
                    Vector3f nx = new Vector3f(-s * 0.4f, 0, 0); rot.transform(nx);
                    Vector3f pz = new Vector3f(0, 0, s * 0.4f); rot.transform(pz);
                    Vector3f nz = new Vector3f(0, 0, -s * 0.4f); rot.transform(nz);
                    line(top, px, h.pos, col); line(top, nx, h.pos, col); line(top, pz, h.pos, col); line(top, nz, h.pos, col);
                    line(bot, px, h.pos, col); line(bot, nx, h.pos, col); line(bot, pz, h.pos, col); line(bot, nz, h.pos, col);
                    line(px, nz, h.pos, col); line(nz, nx, h.pos, col); line(nx, pz, h.pos, col); line(pz, px, h.pos, col);
                }
                case TRIANGLE -> {
                    Vector3f a = new Vector3f(0, s * 0.62f, 0); rot.transform(a);
                    Vector3f b = new Vector3f(s * 0.56f, -s * 0.24f, 0); rot.transform(b);
                    Vector3f c = new Vector3f(-s * 0.28f, -s * 0.24f, s * 0.4816f); rot.transform(c);
                    Vector3f d = new Vector3f(-s * 0.28f, -s * 0.24f, -s * 0.4816f); rot.transform(d);
                    line(a, b, h.pos, col); line(a, c, h.pos, col); line(a, d, h.pos, col);
                    line(b, c, h.pos, col); line(c, d, h.pos, col); line(d, b, h.pos, col);
                }
            }
        }
    }

    private boolean solid(double x, double y, double z) {
        if (mc.level == null) return false;
        BlockPos p = BlockPos.containing(x, y, z);
        return !mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty();
    }

    private static float ageFade(float age) {
        if (age < 0.15f) return age / 0.15f;
        if (age > 0.75f) return Math.max(0f, (1f - age) / 0.25f);
        return 1f;
    }

    private int color(int offset) {
        if (colorMode.is("Custom")) {
            return customColor.getValue().getRGB() | 0xFF000000;
        }
        float hue = ((System.currentTimeMillis() + offset * 20L) % 3000L) / 3000f;
        return Color.HSBtoRGB(hue, 0.75f, 1f) | 0xFF000000;
    }

    private double rand(double a, double b) {
        return a + (b - a) * rng.nextDouble();
    }

    private enum HitKind { CUBE, DIAMOND, TRIANGLE }

    private static final class MeteorP {
        final Vec3 start;
        final Vec3 vel;
        final long born;
        final long life;
        final int color;
        static final int MAX = 48;
        final double[] histX = new double[MAX];
        final double[] histY = new double[MAX];
        final double[] histZ = new double[MAX];
        final long[] histTime = new long[MAX];
        int histCount;

        MeteorP(Vec3 start, Vec3 vel, long born, long life, int color) {
            this.start = start;
            this.vel = vel;
            this.born = born;
            this.life = life;
            this.color = color;
        }

        void pushHistory(Vec3 p, long t) {
            for (int i = Math.min(histCount, MAX - 1); i > 0; i--) {
                histX[i] = histX[i - 1];
                histY[i] = histY[i - 1];
                histZ[i] = histZ[i - 1];
                histTime[i] = histTime[i - 1];
            }
            histX[0] = p.x;
            histY[0] = p.y;
            histZ[0] = p.z;
            histTime[0] = t;
            if (histCount < MAX) histCount++;
        }
    }

    private static final class OrbitP {
        final Vec3 start;
        final Vec3 vel;
        final long born;
        final long life;
        final int color;
        final float rx, ry, rz;

        OrbitP(Vec3 start, Vec3 vel, long born, long life, int color, float rx, float ry, float rz) {
            this.start = start;
            this.vel = vel;
            this.born = born;
            this.life = life;
            this.color = color;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        Vec3 posAt(float age) {
            return start.add(vel.scale(age));
        }
    }

    private static final class HitP {
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        final HitKind kind;
        final int color;
        final float scale;
        final long born;
        final long life;
        final boolean bounce;
        final float rx, ry, rz;
        float spin;

        HitP(Vec3 pos, Vec3 vel, HitKind kind, int color, float scale, long born, long life, boolean bounce,
             float rx, float ry, float rz) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.kind = kind;
            this.color = color;
            this.scale = scale;
            this.born = born;
            this.life = life;
            this.bounce = bounce;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }
    }
}
