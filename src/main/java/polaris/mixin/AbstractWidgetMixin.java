package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.module.impl.visual.Animations;


@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Unique
    private static final float CATACLYSM$HOVER_SCALE = 1.03F;

    @Unique
    private float cataclysm$hover;

    @Unique
    private long cataclysm$lastFrame;

    @Unique
    private boolean cataclysm$widgetScaled;

    @Shadow
    public abstract int getX();

    @Shadow
    public abstract int getY();

    @Shadow
    public abstract int getWidth();

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract boolean isHovered();

    @Inject(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
            require = 0
    )
    private void cataclysm$widgetHoverStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        cataclysm$widgetScaled = false;
        if (!Animations.buttonsEnabled()) {
            cataclysm$hover = 0.0F;
            cataclysm$lastFrame = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (cataclysm$lastFrame == 0L) {
            cataclysm$lastFrame = now;
        }
        float delta = Math.min((now - cataclysm$lastFrame) / 1000.0F, 0.1F);
        cataclysm$lastFrame = now;

        float target = isHovered() ? 1.0F : 0.0F;
        cataclysm$hover += (target - cataclysm$hover) * Math.min(1.0F, delta * Animations.buttonRate());
        if (Math.abs(target - cataclysm$hover) < 0.002F) {
            cataclysm$hover = target;
        }
        if (cataclysm$hover <= 0.0F) {
            return;
        }

        float scale = 1.0F + (CATACLYSM$HOVER_SCALE - 1.0F) * cataclysm$hover;
        float centerX = getX() + getWidth() * 0.5F;
        float centerY = getY() + getHeight() * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        cataclysm$widgetScaled = true;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void cataclysm$widgetHoverEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (cataclysm$widgetScaled) {
            graphics.pose().popMatrix();
            cataclysm$widgetScaled = false;
        }
    }
}
