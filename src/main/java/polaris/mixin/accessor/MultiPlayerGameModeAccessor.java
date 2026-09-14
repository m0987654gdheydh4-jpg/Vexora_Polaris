package polaris.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("isDestroying")
    void cataclysm$setDestroying(boolean isDestroying);

    @Accessor("isDestroying")
    boolean cataclysm$isDestroying();

    @Accessor("destroyBlockPos")
    BlockPos cataclysm$getDestroyBlockPos();

    @Invoker("ensureHasSentCarriedItem")
    void cataclysm$ensureHasSentCarriedItem();

    @Accessor("destroyDelay")
    void cataclysm$setDestroyDelay(int destroyDelay);

    @Accessor("destroyProgress")
    float cataclysm$getDestroyProgress();

    @Accessor("destroyProgress")
    void cataclysm$setDestroyProgress(float destroyProgress);
}

