package polaris.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import polaris.api.module.impl.visual.NoRender;


@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    @Final
    private GpuBuffer emptyBuffer;

    @Shadow
    @Final
    private static int FOG_UBO_SIZE;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$getFogBuffer(FogRenderer.FogMode fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (NoRender.isActive("Fog")) {
            cir.setReturnValue(this.emptyBuffer.slice(0, FOG_UBO_SIZE));
        }
    }

    @Inject(method = "getFogType", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$getFogType(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (camera != null && camera.getFluidInCamera() == FogType.LAVA && NoRender.isActive("Lava")) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }
}
