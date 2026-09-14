package polaris.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.DeathScreenEvent;
import polaris.manager.Manager;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cataclysm$deathScreenTick(CallbackInfo ci) {
        Manager.postEvent(new DeathScreenEvent());
    }
}

