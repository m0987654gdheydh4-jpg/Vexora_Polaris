package polaris.api.module.impl.combat.aura.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.attack.StrikeManager;
import polaris.api.module.impl.combat.aura.util.MathUtils;

import java.util.concurrent.ThreadLocalRandom;


public final class AimPointMode {
    private static int attackHold;
    private static int targetId = -1;
    private static int pointIndex;
    private static long nextSwitchMs;
    private static boolean lookingAway;
    private static long lookAwayStart;
    private static int lookAwayHoldMs;
    private static long nextLookAwayAt;

    private AimPointMode() {
    }

    public static Angle aim(LivingEntity living, Angle current) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || living == null) {
            return current;
        }
        RotationPresetManager pm = RotationPresetManager.resolve();
        
        
        long now = System.currentTimeMillis();
        if (targetId != living.getId()) {
            targetId = living.getId();
            attackHold = 0;
            pointIndex = 0;
            nextSwitchMs = 0L;
        }

        Vec3 point = resolveAimPoint(living, pm, now);
        Vec3 dir = point.subtract(mc.player.getEyePosition());
        if (dir.lengthSqr() < 1.0E-8) {
            return current;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -90.0, 90.0);

        if ("Static".equals(pm.smooth)) {
            pitch *= 0.4F;
        } else if ("Locked".equals(pm.smooth)) {
            pitch = 0.0F;
        }
        if (pm.flag2 && lookAwayActive(now, pm)) {
            pitch = -pm.floatValue23;
        }

        float jy = (float) (Math.cos(now / 40.0) * pm.floatValue7) + MathUtils.getRandom(-pm.floatValue7, pm.floatValue7) * 0.5F;
        float jp = (float) (Math.sin(now / 70.0) * pm.floatValue8) + MathUtils.getRandom(-pm.floatValue8, pm.floatValue8) * 0.5F;
        float aimYaw = yaw + pm.floatValue15 + jy;
        float aimPitch = Mth.clamp(pitch + pm.floatValue16 + jp, pm.floatValue17, pm.floatValue18);
        aimPitch = Mth.clamp(aimPitch, -90.0F, 90.0F);

        
        boolean attack = isAttacking(living);
        float[] speeds = speeds(pm, attack);
        Angle from = current != null
                ? current
                : new Angle(mc.player.getYRot(), mc.player.getXRot());
        float dy = Mth.wrapDegrees(aimYaw - from.getYaw());
        float dp = aimPitch - from.getPitch();
        float stepYaw = Mth.clamp(dy, -speeds[0], speeds[0]);
        float stepPitch = Mth.clamp(dp, -speeds[1], speeds[1]);
        return new Angle(
                from.getYaw() + stepYaw,
                Mth.clamp(from.getPitch() + stepPitch, -90.0F, 90.0F)
        );
    }

    private static boolean isAttacking(LivingEntity living) {
        AuraModule aura = AuraModule.getInstance();
        if (aura == null) {
            return false;
        }
        StrikeManager handler = aura.getAttackHandlerRaw();
        boolean can = handler != null && handler.canAttack(aura.getConfig(), 0)
                && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceTo(living) <= aura.attackDistance();
        if (can) {
            attackHold = 2;
        }
        if (attackHold <= 0) {
            return false;
        }
        attackHold--;
        return true;
    }

    private static float[] speeds(RotationPresetManager pm, boolean attack) {
        float yaw = attack ? pm.floatValue5 : rand(pm.floatValue, pm.floatValue2);
        float pitch = attack ? pm.floatValue6 : rand(pm.floatValue3, pm.floatValue4);
        if ("Static".equals(pm.smooth)) {
            pitch *= 0.3F;
        }
        return new float[]{yaw, pitch};
    }

    private static float rand(float a, float b) {
        if (b < a) {
            float t = a;
            a = b;
            b = t;
        }
        return a == b ? a : a + ThreadLocalRandom.current().nextFloat() * (b - a);
    }

    private static boolean lookAwayActive(long now, RotationPresetManager pm) {
        long interval = (long) (pm.floatValue24 * 1000.0F);
        if (!lookingAway && now - nextLookAwayAt >= interval) {
            lookingAway = true;
            lookAwayStart = now;
            lookAwayHoldMs = ThreadLocalRandom.current().nextInt(200, 320);
            nextLookAwayAt = now;
        }
        if (lookingAway && now - lookAwayStart >= lookAwayHoldMs) {
            lookingAway = false;
        }
        return lookingAway;
    }

    private static Vec3 resolveAimPoint(LivingEntity living, RotationPresetManager pm, long now) {
        AABB box = living.getBoundingBox();
        Vec3 base;
        if (pm.items != null && !pm.items.isEmpty()) {
            base = multipoint(living, box, pm, now);
        } else {
            base = switch (pm.multipoint == null ? "Multipoint" : pm.multipoint) {
                case "Center" -> box.getCenter();
                case "Eyes" -> new Vec3(living.getX(), living.getEyeY(), living.getZ());
                case "Closest" -> closest(box, Minecraft.getInstance().player.getEyePosition());
                default -> softBody(box);
            };
        }
        double oscScale = Math.max(0.2, (double) pm.floatValue11);
        double ox = pm.floatValue9 * Math.sin(now / (250.0 / oscScale));
        double oy = pm.floatValue10 * Math.cos(now / (520.0 / oscScale));
        double sx = 0.0, sz = 0.0;
        if (pm.floatValue12 > 0.001F) {
            Vec3 side = sideDir(living);
            double amp = pm.floatValue12 * Math.sin(now / 300.0);
            sx = side.x * amp;
            sz = side.z * amp;
        }
        Vec3 p = base.add(ox + sx, oy, sz);
        if (pm.floatValue19 > 0.001F) {
            Vec3 vel = living.getDeltaMovement();
            p = p.add(vel.x * pm.floatValue19 * 3.0, 0.0, vel.z * pm.floatValue19 * 3.0);
        }
        return p;
    }

    private static Vec3 softBody(AABB box) {
        Minecraft mc = Minecraft.getInstance();
        double h = Mth.clamp(mc.player.getEyeY() - box.minY, 0.15, box.getYsize() * 0.85);
        Vec3 c = box.getCenter();
        return new Vec3(c.x, box.minY + h, c.z);
    }

    private static Vec3 closest(AABB box, Vec3 eye) {
        return new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ));
    }

    private static Vec3 multipoint(LivingEntity living, AABB box, RotationPresetManager pm, long now) {
        int n = pm.items.size();
        long dwell = (long) (pm.floatValue14 * 1000.0F / Math.max(0.1F, pm.floatValue22));
        if ("Cycle".equals(pm.cycle)) {
            if (now >= nextSwitchMs) {
                pointIndex = (pointIndex + 1) % n;
                nextSwitchMs = now + dwell;
            }
            return pointFrom(living, box, pm.items.get(Math.min(pointIndex, n - 1)));
        }
        if ("Random".equals(pm.cycle)) {
            if (now >= nextSwitchMs) {
                pointIndex = ThreadLocalRandom.current().nextInt(n);
                nextSwitchMs = now + dwell;
            }
            return pointFrom(living, box, pm.items.get(Math.min(pointIndex, n - 1)));
        }
        
        Vec3 eye = Minecraft.getInstance().player.getEyePosition();
        Vec3 look = Minecraft.getInstance().player.getViewVector(1.0F).normalize();
        Vec3 best = null;
        double bestAng = Double.MAX_VALUE;
        for (RotationPresetManager.RotationPresetManagerState st : pm.items) {
            Vec3 p = pointFrom(living, box, st);
            Vec3 d = p.subtract(eye).normalize();
            double ang = Math.acos(Mth.clamp(look.dot(d), -1.0, 1.0));
            if (ang < bestAng) {
                bestAng = ang;
                best = p;
            }
        }
        return best != null ? best : box.getCenter();
    }

    private static Vec3 pointFrom(LivingEntity living, AABB box, RotationPresetManager.RotationPresetManagerState st) {
        Vec3 side = sideDir(living);
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double w = box.maxX - box.minX;
        double h = box.maxY - box.minY;
        return new Vec3(cx + side.x * (st.floatValue * w), box.minY + st.floatValue2 * h, cz + side.z * (st.floatValue * w));
    }

    private static Vec3 sideDir(LivingEntity living) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 d = living.position().subtract(mc.player.position());
        double len = Math.hypot(d.x, d.z);
        return len < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-d.z / len, 0.0, d.x / len);
    }

    public static void reset() {
        attackHold = 0;
        targetId = -1;
        pointIndex = 0;
        nextSwitchMs = 0L;
        lookingAway = false;
    }
}
