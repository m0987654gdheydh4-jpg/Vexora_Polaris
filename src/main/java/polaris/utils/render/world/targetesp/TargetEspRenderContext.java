package polaris.utils.render.world.targetesp;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public record TargetEspRenderContext(
        LivingEntity target,
        
        Vec3 origin,
        float alpha,
        float partialTicks,
        long frameTimeMs,
        int primaryColor,
        int secondaryColor,
        float hurtProgress,
        float chainImpactProgress
) {
}
