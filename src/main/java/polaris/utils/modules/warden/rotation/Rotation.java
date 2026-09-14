package polaris.utils.modules.warden.rotation;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;


public final class Rotation {
    public float floatValue;
    public float floatValue2;

    public Rotation(Entity entity) {
        this.floatValue = entity.getYRot();
        this.floatValue2 = entity.getXRot();
    }

    public Rotation(float yaw, float pitch) {
        this.floatValue = yaw;
        this.floatValue2 = pitch;
    }

    public float measure(Rotation other) {
        float dy = Mth.wrapDegrees(other.floatValue - this.floatValue);
        float dp = other.floatValue2 - this.floatValue2;
        return (float) Math.hypot(Math.abs(dy), Math.abs(dp));
    }

    public double measure2(Rotation other) {
        double dy = Mth.wrapDegrees(other.floatValue - this.floatValue);
        double dp = Mth.wrapDegrees(other.floatValue2 - this.floatValue2);
        return Math.hypot(dy, dp);
    }
}
