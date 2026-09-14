package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import polaris.api.drag.core.ElementManager;
import polaris.api.module.impl.visual.Animations;
import polaris.api.module.impl.visual.Hud;
import polaris.utils.render.ui.Render2DCoordinateSpace;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$dragMouseClicked(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        MouseButtonEvent dragEvent = cataclysm$toDragEvent(event);
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseClicked(dragEvent, doubled)) {
            cir.setReturnValue(true);
            return;
        }
        if (ElementManager.getInstance().handleMouseClicked(dragEvent)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void cataclysm$cancelDragOnRemoved(CallbackInfo ci) {
        Animations.resetChatState();
        ElementManager.getInstance().cancelActiveElement();
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$cancelDragOnClose(CallbackInfo ci) {
        
        if (Animations.deferChatClose()) {
            ci.cancel();
            return;
        }
        ElementManager.getInstance().cancelActiveElement();
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cataclysm$animateChatStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        cataclysm$chatAnimated = false;
        if (!Animations.chatEnabled()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, Animations.chatOffset());
        cataclysm$chatAnimated = true;
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cataclysm$animateChatEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (cataclysm$chatAnimated) {
            graphics.pose().popMatrix();
            cataclysm$chatAnimated = false;
        }
    }

    @Unique
    private boolean cataclysm$chatAnimated;

    @Unique
    private MouseButtonEvent cataclysm$toDragEvent(MouseButtonEvent event) {
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        if (Math.abs(scale - 1.0F) <= 0.0001F) {
            return event;
        }

        MouseButtonInfo info = new MouseButtonInfo(event.button(), event.modifiers());
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, info);
    }
}

