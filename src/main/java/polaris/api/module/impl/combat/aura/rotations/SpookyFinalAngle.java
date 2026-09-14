package polaris.api.module.impl.combat.aura.rotations;


import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;
import polaris.api.module.impl.combat.aura.target.MultiPoint;
import polaris.api.module.impl.combat.aura.target.RaycastAngle;
import polaris.api.module.impl.combat.aura.util.MathUtils;

public final class SpookyFinalAngle extends RotateConstructor {

    private static final Minecraft mc = Minecraft.getInstance();
    private float animatedYawStep;
    private float animatedPitchStep;
    private float animProgress = 1.0F;
    private float startYaw;
    private float startPitch;
    private float lastTargetYaw;
    private float lastTargetPitch;
    public SpookyFinalAngle() {
        super("SpookyFinalAngle");
    }
    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
      AuraModule aura = AuraModule.getInstance();
        Angle angleDelta = MathAngle.calculateDelta(currentAngle, targetAngle);
        float yawDelta = angleDelta.getYaw();
        float pitchDelta = angleDelta.getPitch();
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (rotationDifference < 0.0001F) {
            rotationDifference = 0.0001F;
        }

        float on1 = MathUtils.getRandom(25, 360);
        float on2 = MathUtils.getRandom(4, 10);

        float yawJit = (float) (5 * Math.cos(System.currentTimeMillis() / 70D));
        float pitchJit = (float) (4 * Math.sin(System.currentTimeMillis() / 70D));

        boolean rayHit = entity != null && RaycastAngle.rayTrace(16, entity.getBoundingBox());
        boolean attackWindow = false;
        if (aura != null) {
            var attackHandler = aura.getAttackHandler();
            if (attackHandler != null && attackHandler.getAttackTimer() != null) {
                attackWindow = !attackHandler.getAttackTimer().finished(MathUtils.getRandom(50, 100));
            }
        }
        if (rayHit || attackWindow) {
            on1 = 0;
            on2 = 0;
            yawJit = 0;
            pitchJit = 0;
        }



        float straightLineYaw = Math.abs(yawDelta / rotationDifference) * on1;
        float straightLinePitch = Math.abs(pitchDelta / rotationDifference) * on2;

        float fyaw = currentAngle.getYaw() + Math.clamp(yawDelta, -straightLineYaw, straightLineYaw);
        float fpitch = Mth.clamp(currentAngle.getPitch() + Math.clamp(pitchDelta, -straightLinePitch, straightLinePitch), -89, 89) ;
        return animate(currentAngle, fyaw + yawJit, fpitch + pitchJit, MathUtils.getRandom(0.1f,0.4f));  }
    private Angle animate(Angle from, float targetYaw, float targetPitch, float speed) {
        float targetChangedYaw = Math.abs(Mth.wrapDegrees(targetYaw - lastTargetYaw));
        float targetChangedPitch = Math.abs(targetPitch - lastTargetPitch);

        if (animProgress >= 1.0F || targetChangedYaw > 6 || targetChangedPitch > 6) {
            startYaw = from.getYaw();
            startPitch = from.getPitch();

            lastTargetYaw = targetYaw;
            lastTargetPitch = targetPitch;

            animProgress = 0.0F;
        }

        animProgress = Mth.clamp(animProgress + speed, 0.0F, 1.0F);

        float eased = easeOutBack(animProgress);

        float yawDelta = Mth.wrapDegrees(lastTargetYaw - startYaw);
        float pitchDelta = lastTargetPitch - startPitch;

        float yaw = startYaw + yawDelta * eased;
        float pitch = Mth.clamp(startPitch + pitchDelta * eased, -89.0F, 89.0F);

        return new Angle(yaw, pitch);
    }

    private float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;

        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3)
                + c1 * (float) Math.pow(x - 1.0F, 2);
    }
    @Override
    public Vec3 randomValue() {
        return new Vec3(0.2f,0,5f);
    }
}
