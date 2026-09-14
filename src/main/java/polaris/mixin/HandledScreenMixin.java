package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.HandledScreenEvent;
import polaris.api.module.impl.visual.Animations;
import polaris.manager.Manager;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    @Final
    protected int imageWidth;

    @Shadow
    @Final
    protected int imageHeight;

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    
    @Inject(
            method = "renderContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlots(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            ),
            require = 0
    )
    private void cataclysm$beforeSlots(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Manager.postEvent(new HandledScreenEvent(
                HandledScreenEvent.Phase.SLOTS_BACKGROUND,
                graphics,
                hoveredSlot,
                imageWidth,
                imageHeight
        ));
    }

    
    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void cataclysm$handledScreenRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Manager.postEvent(new HandledScreenEvent(
                HandledScreenEvent.Phase.POST,
                graphics,
                hoveredSlot,
                imageWidth,
                imageHeight
        ));
    }

    @Unique
    private boolean cataclysm$screenScaled;

    
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cataclysm$animateScreenStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        cataclysm$screenScaled = false;
        Screen self = (Screen) (Object) this;
        if (!Animations.screenEnabled(self)) {
            return;
        }
        float scale = Animations.screenScale(self);
        float centerX = graphics.guiWidth() / 2.0F;
        float centerY = graphics.guiHeight() / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        cataclysm$screenScaled = true;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cataclysm$animateScreenEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (cataclysm$screenScaled) {
            graphics.pose().popMatrix();
            cataclysm$screenScaled = false;
        }
    }

    
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$animateScreenClose(CallbackInfo ci) {
        if (Animations.deferScreenClose((Screen) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void cataclysm$animateScreenRemoved(CallbackInfo ci) {
        Animations.resetScreenState();
    }
}
