package polaris.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import polaris.api.module.impl.visual.Animations;


@Mixin(Gui.class)
public class GuiTabListMixin {
    @Redirect(
            method = "renderTabList",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"),
            require = 0
    )
    private boolean cataclysm$keepTabVisible(KeyMapping keyMapping) {
        return Animations.keepTabVisible(keyMapping.isDown());
    }
}
