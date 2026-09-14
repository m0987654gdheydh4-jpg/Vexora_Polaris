package polaris.api.module.impl.combat.aura.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.attack.StrikeManager;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;
import polaris.api.module.impl.combat.aura.util.MathUtils;

import java.util.concurrent.ThreadLocalRandom;


public final class FovSnapAngle extends RotateConstructor {
    private int snapTicks;

    public FovSnapAngle() {
        super("FOV");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        AuraModule aura = AuraModule.getInstance();
        if (mc.player == null || aura == null || !(entity instanceof LivingEntity living)) {
            return currentAngle;
        }

        float wobbleY = 0.25F * (float) Math.cos(System.currentTimeMillis() / 1500.0);
        float wobbleX = 0.2F * (float) Math.cos(System.currentTimeMillis() / 700.0);
        float wobbleZ = 0.2F * (float) Math.cos(System.currentTimeMillis() / 900.0);
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = living.position()
                .add(wobbleZ, Mth.clamp(eye.y - living.getY(), 0.0, 0.8) - wobbleY, wobbleX)
                .subtract(eye)
                .normalize();
        float aimYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float aimPitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))), -90.0, 90.0);

        StrikeManager handler = aura.getAttackHandlerRaw();
        boolean inFov = aura.isTargetInsideAuraFov(living);
        boolean canHit = inFov && handler != null && handler.canAttack(aura.getConfig(), 0);
        String snap = aura.getSnapMode() != null ? aura.getSnapMode().getValue() : "Fast";

        float yaw = currentAngle.getYaw();
        float pitch = currentAngle.getPitch();
        float speed;

        if (snap.contains("Smooth")) {
            speed = 24.0F;
            if (canHit) {
                snapTicks = 2;
                speed = 130.0F;
            }
            if (snapTicks > 0) {
                yaw = aimYaw;
                pitch = aimPitch;
                snapTicks--;
            }
        } else if (snap.contains("Random")) {
            speed = MathUtils.getRandom(30.0F, 35.0F);
            if (canHit) {
                snapTicks = 2;
            }
            if (snapTicks > 0) {
                speed = MathUtils.getRandom(200.0F, 280.0F);
                yaw = aimYaw;
                pitch = aimPitch;
                snapTicks--;
            }
            float jy = ThreadLocalRandom.current().nextFloat(-3.0F, 3.0F)
                    + (float) (MathUtils.getRandom(4.0F, 5.0F) * Math.cos(System.currentTimeMillis() / 150.0))
                    + (float) (MathUtils.getRandom(4.0F, 5.0F) * Math.sin(System.currentTimeMillis() / 50.0));
            float jp = ThreadLocalRandom.current().nextFloat(-1.0F, 1.0F)
                    + (float) (MathUtils.getRandom(2.0F, 3.0F) * Math.cos(System.currentTimeMillis() / 170.0));
            yaw += jy / 4.0F;
            pitch += jp;
        } else {
            
            speed = MathUtils.getRandom(280.0F, 360.0F);
            if (canHit) {
                yaw = aimYaw;
                pitch = aimPitch;
            }
        }

        Angle desired = new Angle(yaw, pitch);
        Angle delta = MathAngle.calculateDelta(currentAngle, desired);
        return new Angle(
                currentAngle.getYaw() + Mth.clamp(delta.getYaw(), -speed, speed),
                Mth.clamp(currentAngle.getPitch() + Mth.clamp(delta.getPitch(), -speed, speed), -90.0F, 90.0F)
        );
    }

    @Override
    public Vec3 randomValue() {
        return Vec3.ZERO;
    }

    public void reset() {
        snapTicks = 0;
    }
}