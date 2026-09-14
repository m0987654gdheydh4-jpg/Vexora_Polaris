package polaris.utils.modules.warden.rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;


public final class RotationResetController {
    private int intValue = -1;

    public void invoke(Rotation rotation, LivingEntity livingEntity, float f, float g) {
        if (livingEntity.getId() != this.intValue) {
            this.intValue = livingEntity.getId();
        }
        float yaw = rotation.floatValue + f;
        float pitch = Mth.clamp(rotation.floatValue2 + g, -90.0F, 90.0F);
        RotationController.invoke3(new Rotation(yaw, pitch), Math.abs(f), Math.abs(g), 20.0F, 20.0F, 1, 15, false);
    }

    public void invoke2(Rotation rotation2, float f, float g, int i, int j) {
        this.invoke3(rotation2, f, g, 20.0F, 20.0F, i, j);
    }

    public void invoke3(Rotation rotation3, float f, float g, float h, float i, int j, int k) {
        this.intValue = -1;
        RotationController.invoke3(rotation3, f, g, h, i, j, k, false);
    }

    public void invoke4() {
        this.intValue = -1;
        RotationController.rotationControllerState2 = RotationController.RotationControllerState2.IDLE;
        RotationController.rotation = null;
        RotationController.intValue = 0;
    }
}
