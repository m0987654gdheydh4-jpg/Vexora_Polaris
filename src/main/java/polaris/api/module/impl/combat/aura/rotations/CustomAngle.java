package polaris.api.module.impl.combat.aura.rotations;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.custom.AimPointMode;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;
import polaris.api.module.impl.combat.aura.util.MathUtils;


public final class CustomAngle extends RotateConstructor {
    public CustomAngle() {
        super("Custom");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return currentAngle;
        }
        return AimPointMode.aim(living, currentAngle);
    }

    @Override
    public Vec3 randomValue() {
        return new Vec3(
                MathUtils.getRandom(-0.05F, 0.05F),
                MathUtils.getRandom(-0.05F, 0.05F),
                MathUtils.getRandom(-0.05F, 0.05F)
        );
    }

    public void reset() {
        AimPointMode.reset();
    }
}
