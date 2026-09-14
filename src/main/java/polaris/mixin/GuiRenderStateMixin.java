package polaris.mixin;

import polaris.access.GuiRenderStateLayerAccessor;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements GuiRenderStateLayerAccessor {
    @Unique
    private int cataclysm$layerSerial;

    @Override
    public int cataclysm$getLayerSerial() {
        return cataclysm$layerSerial;
    }

    @Inject(method = "nextStratum", at = @At("RETURN"))
    private void cataclysm$trackNextStratum(CallbackInfo ci) {
        cataclysm$layerSerial++;
    }

    @Inject(method = "up", at = @At("RETURN"))
    private void cataclysm$trackUpLayer(CallbackInfo ci) {
        cataclysm$layerSerial++;
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void cataclysm$resetLayerSerial(CallbackInfo ci) {
        cataclysm$layerSerial = 0;
    }
}

