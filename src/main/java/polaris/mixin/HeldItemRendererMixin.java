package polaris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.HandAnimationEvent;
import polaris.api.events.impl.HandOffsetEvent;
import polaris.api.events.impl.HeldItemUpdateEvent;
import polaris.api.events.impl.ItemRendererEvent;
import polaris.manager.Manager;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Unique
    private PoseStack cataclysm$customSwingMatrices;

    @Unique
    private InteractionHand cataclysm$customSwingHand;

    @Unique
    private float cataclysm$customSwingProgress;

    @Inject(method = "tick", at = @At("TAIL"))
    private void cataclysm$updateHeldItems(CallbackInfo ci) {
        HeldItemUpdateEvent event = Manager.postEvent(new HeldItemUpdateEvent(this.mainHandItem, this.offHandItem));
        if (event.getMainHand() != this.mainHandItem) {
            this.mainHandItem = event.getMainHand();
        }
        if (event.getOffHand() != this.offHandItem) {
            this.offHandItem = event.getOffHand();
        }
    }

    @WrapOperation(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"), require = 0)
    private void cataclysm$itemRenderHook(ItemInHandRenderer instance, AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector nodeCollector, int light, Operation<Void> original) {
        ItemRendererEvent event = Manager.postEvent(new ItemRendererEvent(player, item, hand));
        original.call(instance, event.getPlayer(), tickDelta, pitch, event.getHand(), swingProgress, event.getStack(), equipProgress, matrices, nodeCollector, light);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER), require = 0)
    private void cataclysm$handOffset(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector nodeCollector, int light, CallbackInfo ci) {
        HandOffsetEvent event = Manager.postEvent(new HandOffsetEvent(matrices, stack, hand));
        float scale = event.getScale();
        if (scale != 1.0F) {
            matrices.scale(scale, scale, scale);
        }
    }

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"), require = 0)
    private void cataclysm$baseSwingAnimation(ItemInHandRenderer instance, PoseStack matrices, HumanoidArm arm, float equipProgress, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true, ordinal = 2) float swingProgress) {
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            original.call(instance, matrices, arm, equipProgress);
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (event.isCancelled()) {
            cataclysm$markCustomSwing(matrices, hand, swingProgress);
            return;
        }

        original.call(instance, matrices, arm, equipProgress);
    }

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"), require = 0)
    private void cataclysm$swingAnimation(ItemInHandRenderer instance, float swingProgress, PoseStack matrices, int armX, HumanoidArm arm, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) InteractionHand hand) {
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            original.call(instance, swingProgress, matrices, armX, arm);
            return;
        }

        if (cataclysm$consumeCustomSwing(matrices, hand, swingProgress)) {
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (!event.isCancelled()) {
            original.call(instance, swingProgress, matrices, armX, arm);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("TAIL"), require = 0)
    private void cataclysm$clearSwingAnimation(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector nodeCollector, int light, CallbackInfo ci) {
        cataclysm$clearCustomSwing();
    }

    @Unique
    private void cataclysm$markCustomSwing(PoseStack matrices, InteractionHand hand, float swingProgress) {
        this.cataclysm$customSwingMatrices = matrices;
        this.cataclysm$customSwingHand = hand;
        this.cataclysm$customSwingProgress = swingProgress;
    }

    @Unique
    private boolean cataclysm$consumeCustomSwing(PoseStack matrices, InteractionHand hand, float swingProgress) {
        boolean matches = this.cataclysm$customSwingMatrices == matrices
                && this.cataclysm$customSwingHand == hand
                && Float.compare(this.cataclysm$customSwingProgress, swingProgress) == 0;
        if (matches) {
            cataclysm$clearCustomSwing();
        }
        return matches;
    }

    @Unique
    private void cataclysm$clearCustomSwing() {
        this.cataclysm$customSwingMatrices = null;
        this.cataclysm$customSwingHand = null;
        this.cataclysm$customSwingProgress = 0.0F;
    }
}

