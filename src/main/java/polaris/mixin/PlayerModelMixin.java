package polaris.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.emotions.EmotionWheelManager;


@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void polaris$emotionsAfterPlayerSetup(AvatarRenderState state, CallbackInfo ci) {
        EmotionWheelManager manager = EmotionWheelManager.getInstance();
        if (manager == null) {
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        manager.applyToModel(
                state,
                model.head,
                model.hat,
                model.body,
                model.rightArm,
                model.leftArm,
                model.rightLeg,
                model.leftLeg
        );
    }
}
