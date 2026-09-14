package polaris.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import polaris.api.module.impl.visual.Ambience;
import polaris.api.module.impl.visual.ESP;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<S extends EntityRenderState> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"), require = 0)
    private void cataclysm$hideEspNameTags(Entity entity, S state, float tickDelta, CallbackInfo ci) {
        if (ESP.shouldHideVanillaName(entity)) {
            state.nameTag = null;
        }

        
        int chamsColor = polaris.api.module.impl.visual.ShaderChams.maskColor(entity);
        if (chamsColor != 0) {
            state.outlineColor = chamsColor;
        }
    }

    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$hideEspServerTagEntities(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (ESP.shouldSuppressServerTagEntity(entity)) {
            cir.setReturnValue(false);
        }
    }

    
    @Inject(method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void cataclysm$torchLight(Entity entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (!Ambience.isTorchLightActive()) {
            return;
        }
        int extra = Ambience.torchLightAt(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
        if (extra <= 0) {
            return;
        }
        int packed = cir.getReturnValueI();
        if (extra > LightTexture.block(packed)) {
            cir.setReturnValue(LightTexture.pack(extra, LightTexture.sky(packed)));
        }
    }
}

