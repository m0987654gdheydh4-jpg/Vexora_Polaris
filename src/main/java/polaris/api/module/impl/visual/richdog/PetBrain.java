package polaris.api.module.impl.visual.richdog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.visual.richdog.util.PetAnimation;
import polaris.utils.timer.TimerUtil;

import java.util.concurrent.ThreadLocalRandom;

public final class PetBrain {

    private final Minecraft mc = Minecraft.getInstance();

    private Vec3 pos;
    private Vec3 motion = Vec3.ZERO;
    private float direction = randomAngle();
    private float yaw;
    private float body;

    private final PetAnimation x = new PetAnimation();
    private final PetAnimation y = new PetAnimation();
    private final PetAnimation z = new PetAnimation();
    private final PetAnimation bodyAnim  = new PetAnimation();
    private final PetAnimation yawAnim   = new PetAnimation();
    private final PetAnimation pitchAnim = new PetAnimation();

    private boolean lay;
    private final TimerUtil staying = new TimerUtil();

    public float prevLimbSwingAmount;
    public float limbSwingAmount;
    public float limbSwing;

    private Player entity;

    public void setEntity(Player entity) {
        this.entity = entity;
    }

    public void onUpdate() {
        if (entity == null || mc.level == null) {
            return;
        }

        Vec3 playerPos = entity.position();

        if (pos == null || pos.distanceTo(playerPos) > 10) {
            pos = playerPos;
            x.animate((float) pos.x, 1);
            y.animate((float) pos.y, 1);
            z.animate((float) pos.z, 1);
        }

        motion = motion.add(0, -0.2, 0);
        Vec3 newPos = pos.add(motion);

        if (isBlockSolid(newPos.x, newPos.y, newPos.z)) {
            int blockY = (int) newPos.y;
            double correctedY = blockY + 1 + 0.1;
            newPos = new Vec3(newPos.x, correctedY, newPos.z);
            motion = new Vec3(motion.x, 0, motion.z);
        }

        if (collidesHorizontally(newPos)) {
            newPos = new Vec3(pos.x, newPos.y, pos.z);
            motion = new Vec3(-motion.x * 0.35, motion.y, -motion.z * 0.35);
            direction = randomAngle();
        }

        motion = new Vec3(motion.x, 0, motion.z);

        LivingEntity target = auraTarget();
        if (target != null && entity instanceof LocalPlayer) {
            if (isBlockSolid(newPos.x, newPos.y - 0.1, newPos.z)) {
                motion = new Vec3(motion.x, 0.62, motion.z);
            }

            AABB box = new AABB(getPos().subtract(new Vec3(0.4, 0, 0.4)), getPos().add(0.4, 0.4, 0.4));
            AABB targetBox = target.getBoundingBox().inflate(-0.1, 0, -0.1);

            motion = motion.add(target.position().subtract(newPos).normalize());

            if (box.maxX > targetBox.minX && box.maxY > targetBox.minY && box.maxZ > targetBox.minZ
                    && box.minX < targetBox.maxX && box.minY < targetBox.maxY && box.minZ < targetBox.maxZ) {
                motion = motion.multiply(-1, 1, -1);
            }
        } else {
            if (newPos.distanceTo(playerPos) > 2) {
                motion = motion.add(playerPos.subtract(newPos).normalize());
            }
        }

        handleRotation();

        pos = newPos;

        if (pos.distanceTo(playerPos) < 0.1) {
            direction = randomAngle();
            double xMot = -Math.sin(Math.toRadians(direction)) * 0.1;
            double zMot = Math.cos(Math.toRadians(direction)) * 0.1;
            motion = motion.add(xMot, 0, zMot);
        }

        motion = motion.scale(0.5);

        int speed = 150;
        x.animate((float) pos.x, speed);
        y.animate((float) pos.y, speed);
        z.animate((float) pos.z, speed);

        limbTick();

        if (Math.abs(pos.x - x.get()) > 0.1f || Math.abs(pos.z - z.get()) > 0.1f) {
            staying.reset();
        }
        lay = staying.isReached(1000);
    }

    private void handleRotation() {
        if (motion.x != 0 || motion.z != 0) {
            double angle = Math.atan2(motion.z, motion.x);
            yaw = (float) Math.toDegrees(angle) - 90;
            yaw %= 360;
            if (yaw < 0) yaw += 360;
        }

        float[] rotation = lookAt(pos, entity.getEyePosition(1.0F));

        LivingEntity target = auraTarget();
        if (target != null && entity instanceof LocalPlayer) {
            rotation = lookAt(pos, target.position());
        }

        float gradus  = lay ? 200 : 150;
        float gradus1 = lay ? 100 : 50;
        if (rotation[0] - yaw < -gradus || rotation[0] - yaw > gradus) {
            yaw = rotation[0];
        }

        float shortestYawPath = (((((yaw - body) % 360) + 540) % 360) - 180);

        if (!lay) {
            bodyAnim.animate(body + shortestYawPath, 150);
        }
        yawAnim.animate(Mth.clamp(rotation[0] - yaw, -gradus1, gradus1), 150);
        pitchAnim.animate(rotation[1], 150);

        body = body + shortestYawPath;
    }

    private void limbTick() {
        prevLimbSwingAmount = limbSwingAmount;
        double d0 = x.get() - pos.x;
        double d2 = z.get() - pos.z;
        float f = Mth.sqrt((float) (d0 * d0 + d2 * d2)) * 4.0F;
        if (f > 1.0F) f = 1.0F;
        limbSwingAmount += (f - limbSwingAmount) * 0.4F;
        limbSwing += limbSwingAmount;
    }

    public float getBody()  { return bodyAnim.get(); }
    public float getYaw()   { return yawAnim.get(); }
    public float getPitch() { return pitchAnim.get(); }
    public boolean isLay()  { return lay; }

    public Vec3 getPos() {
        return new Vec3(x.get(), y.get(), z.get());
    }

    private LivingEntity auraTarget() {
        AuraModule aura = AuraModule.getInstance();
        return aura != null && aura.isEnabled() ? AuraModule.target : null;
    }

    private static float[] lookAt(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dxz));
        return new float[]{yaw, pitch};
    }

    private static float randomAngle() {
        return ThreadLocalRandom.current().nextFloat() * 360.0F;
    }

    private boolean isBlockSolid(double bx, double by, double bz) {
        if (mc.level == null) return false;
        BlockPos bp = BlockPos.containing(bx, by, bz);
        return !mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty();
    }

    private boolean collidesHorizontally(Vec3 position) {
        double radius = 0.35;
        double baseY = horizontalBaseY(position);
        double[] heights = {0.0, 0.45};
        for (double height : heights) {
            double sy = baseY + height;
            if (isBlockSolid(position.x + radius, sy, position.z + radius)
                    || isBlockSolid(position.x + radius, sy, position.z - radius)
                    || isBlockSolid(position.x - radius, sy, position.z + radius)
                    || isBlockSolid(position.x - radius, sy, position.z - radius)) {
                return true;
            }
        }
        return false;
    }

    private double horizontalBaseY(Vec3 position) {
        double supportRadius = 0.32;
        double probeY = position.y - 0.2;
        double highest = Double.NEGATIVE_INFINITY;
        double[][] offsets = {
                {0.0, 0.0}, {supportRadius, supportRadius}, {supportRadius, -supportRadius},
                {-supportRadius, supportRadius}, {-supportRadius, -supportRadius}
        };
        for (double[] off : offsets) {
            if (isBlockSolid(position.x + off[0], probeY, position.z + off[1])) {
                highest = Math.max(highest, Math.floor(probeY) + 1.0);
            }
        }
        if (highest == Double.NEGATIVE_INFINITY) {
            return position.y + 0.05;
        }
        return Math.max(position.y + 0.05, highest + 0.05);
    }
}

