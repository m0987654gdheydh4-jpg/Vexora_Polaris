package polaris.api.module.impl.combat.aura.rotations;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.ai.AiRotationTrainer;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;


public final class AiAngle extends RotateConstructor {

    public AiAngle() {
        super("AI");
    }

    @Override
    public Angle limitAngleChange(Angle currentAngle, Angle targetAngle, Vec3 vec3d, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(entity instanceof LivingEntity living)) {
            return currentAngle;
        }
        AiRotationTrainer.invoke3(living);
        Angle silent = AiRotationTrainer.getSilentAngle();
        return silent != null ? silent : (currentAngle != null ? currentAngle : targetAngle);
    }

    @Override
    public Vec3 randomValue() {
        return Vec3.ZERO;
    }

    public void reset() {
        AiRotationTrainer.invoke10();
        AiRotationTrainer.clearSilent();
    }
}
