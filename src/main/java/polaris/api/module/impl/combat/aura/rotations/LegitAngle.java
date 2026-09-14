package polaris.api.module.impl.combat.aura.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;


public final class LegitAngle extends RotateConstructor {
    private long lastFrameNs;
    private Angle lastOut = new Angle(0F, 0F);

    public LegitAngle() {
        super("Legit");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            return new Angle(mc.player.getYRot(), mc.player.getXRot());
        }
        return currentAngle != null ? currentAngle : new Angle(0F, 0F);
    }

    
    public Angle stepFrame(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        AuraModule aura = AuraModule.getInstance();
        if (mc == null || mc.player == null || aura == null || !(entity instanceof LivingEntity living)) {
            float y = mc != null && mc.player != null ? mc.player.getYRot() : 0F;
            float p = mc != null && mc.player != null ? mc.player.getXRot() : 0F;
            lastOut = new Angle(y, p);
            return lastOut;
        }

        Vec3 dir = living.position()
                .add(0.0, Mth.clamp(mc.player.getEyeY() - living.getY(), 0.0, 1.0), 0.0)
                .subtract(mc.player.getEyePosition());
        if (dir.lengthSqr() < 1.0E-8) {
            lastOut = new Angle(mc.player.getYRot(), mc.player.getXRot());
            return lastOut;
        }
        dir = dir.normalize();
        float aimYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float currentYaw = mc.player.getYRot();
        float yawDelta = Mth.wrapDegrees(aimYaw - currentYaw);

        float speed = 0.08F;
        if (aura.getLegitSpeed() != null) {
            speed = Mth.clamp(aura.getLegitSpeed().getFloat(), 0.02F, 0.4F);
        }
        float frameScale = Mth.clamp(frameDelta(), 0.25F, 4.0F);
        float factor = 1.0F - (float) Math.pow(1.0F - speed, frameScale);
        float newYaw = currentYaw + yawDelta * factor;

        lastOut = new Angle(newYaw, mc.player.getXRot());
        return lastOut;
    }

    public Angle getLast() {
        return lastOut;
    }

    private float frameDelta() {
        long now = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = now;
            return 1.0F;
        }
        float scale = (float) (now - lastFrameNs) / 1.6666667E7F;
        lastFrameNs = now;
        return scale;
    }

    @Override
    public Vec3 randomValue() {
        return Vec3.ZERO;
    }

    public void reset() {
        lastFrameNs = 0L;
        lastOut = new Angle(0F, 0F);
    }
}
