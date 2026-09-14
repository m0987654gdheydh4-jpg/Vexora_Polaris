package polaris.api.module.impl.combat.aura.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class EntityRaycastUtils {
    private EntityRaycastUtils() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    public static boolean check4(float pitch, float yaw, double reach, Entity entity, boolean ignoreBlocks) {
        if (mc().player != null && mc().level != null && entity != null) {
            Vec3 start = mc().player.getEyePosition();
            Vec3 dir = resolve4(pitch, yaw);
            Vec3 end = start.add(dir.scale(reach));
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> hit = box.clip(start, end);
            if (box.contains(start)) {
                return true;
            } else if (hit.isEmpty()) {
                return false;
            } else if (ignoreBlocks) {
                return true;
            } else {
                HitResult blockHit = resolve5(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
                return blockHit.getType() == HitResult.Type.MISS
                        || hit.get().distanceToSqr(start) < blockHit.getLocation().distanceToSqr(start);
            }
        } else {
            return false;
        }
    }

    public static Vec3 resolve4(float pitch, float yaw) {
        float f = -yaw * (float) (Math.PI / 180.0) - (float) Math.PI;
        float g = -pitch * (float) (Math.PI / 180.0);
        float h = Mth.cos(f);
        float i = Mth.sin(f);
        float j = -Mth.cos(g);
        float k = Mth.sin(g);
        return new Vec3(i * j, k, h * j);
    }

    public static HitResult resolve5(Vec3 start, Vec3 end, ClipContext.Block block, ClipContext.Fluid fluid) {
        return mc().level.clip(new ClipContext(start, end, block, fluid, mc().player));
    }
}