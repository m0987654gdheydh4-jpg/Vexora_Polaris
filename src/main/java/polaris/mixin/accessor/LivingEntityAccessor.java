package polaris.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean cataclysm$isJumping();

    @Accessor("noJumpDelay")
    int cataclysm$getJumpingCooldown();

    @Accessor("noJumpDelay")
    void cataclysm$setJumpingCooldown(int value);
}

