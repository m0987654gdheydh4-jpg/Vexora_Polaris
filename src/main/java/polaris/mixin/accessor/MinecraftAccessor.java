package polaris.mixin.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean cataclysm$startAttack();

    @Invoker("startUseItem")
    void cataclysm$startUseItem();

    @Invoker("updateLevelInEngines")
    void cataclysm$updateLevelInEngines(ClientLevel level);

    @Accessor("rightClickDelay")
    void cataclysm$setRightClickDelay(int delay);
}

