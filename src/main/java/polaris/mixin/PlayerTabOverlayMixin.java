package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.module.impl.visual.Animations;


@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Unique
    private boolean cataclysm$tabScaled;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cataclysm$scaleTabStart(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        cataclysm$tabScaled = false;
        if (!Animations.tabEnabled()) {
            return;
        }
        float scale = Animations.tabScale();
        float centerX = width / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, 0.0F);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, 0.0F);
        cataclysm$tabScaled = true;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cataclysm$scaleTabEnd(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (cataclysm$tabScaled) {
            graphics.pose().popMatrix();
            cataclysm$tabScaled = false;
        }
    }
}
