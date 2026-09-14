package polaris.api.module.impl.combat.aura.rotations;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.attack.StrikeManager;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;
import polaris.api.module.impl.combat.aura.util.MathUtils;


public final class FtNewAngle extends RotateConstructor {
    private long idleSinceMs = -1L;

    public FtNewAngle() {
        super("FT-New");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
        AuraModule aura = AuraModule.getInstance();
        StrikeManager handler = aura != null ? aura.getAttackHandlerRaw() : null;
        boolean canHit = entity != null && handler != null && handler.canAttack(aura.getConfig(), 2);

        Angle delta = MathAngle.calculateDelta(currentAngle, targetAngle);
        float yawDelta = delta.getYaw();
        float pitchDelta = delta.getPitch();
        float hypot = Math.max((float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta)), 1.0E-4F);

        if (canHit) {
            idleSinceMs = -1L;
            float yawCap = Math.abs(yawDelta / hypot) * 130.0F;
            float pitchCap = Math.abs(pitchDelta / hypot) * 130.0F;
            float yaw = lerp(0.85F, currentAngle.getYaw(),
                    currentAngle.getYaw() + Mth.clamp(yawDelta, -yawCap, yawCap));
            float pitch = lerp(0.85F, currentAngle.getPitch(),
                    currentAngle.getPitch() + Mth.clamp(pitchDelta, -pitchCap, pitchCap));
            return new Angle(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
        }

        if (idleSinceMs < 0L) {
            idleSinceMs = System.currentTimeMillis();
        }
        float fade = 1.0F - Mth.clamp((float) (System.currentTimeMillis() - idleSinceMs) / 1000.0F, 0.0F, 1.0F);
        float noiseYaw = (float) (MathUtils.getRandom(4.0F, 15.0F) * Math.sin(System.currentTimeMillis() / 95.0)) * fade;
        float noisePitch = (float) (MathUtils.getRandom(6.0F, 7.0F) * Math.cos(System.currentTimeMillis() / 45.0)) * fade;

        boolean recentHit = handler != null && !handler.getAttackTimer().finished(535L);
        float yawCap = Math.abs(yawDelta / hypot) * (recentHit ? 45.0F : 0.0F);
        float pitchCap = Math.abs(pitchDelta / hypot) * (recentHit ? 45.0F : 0.0F);

        float yaw = lerp(0.85F, currentAngle.getYaw(),
                currentAngle.getYaw() + Mth.clamp(yawDelta, -yawCap, yawCap) + noiseYaw);
        float pitch = lerp(0.85F, currentAngle.getPitch(),
                currentAngle.getPitch() + Mth.clamp(pitchDelta, -pitchCap, pitchCap) + noisePitch);
        return new Angle(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    @Override
    public Vec3 randomValue() {
        return Vec3.ZERO;
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    public void reset() {
        idleSinceMs = -1L;
    }
}