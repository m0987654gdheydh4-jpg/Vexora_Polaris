package polaris.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import polaris.screens.modernui.impl.WorldAnimation;
import polaris.utils.render.ui.blur.BlurFramebuffer;
import polaris.utils.render.ui.glass.GlassRenderer;
import polaris.utils.render.ui.image.ImageRenderer;
import polaris.utils.render.ui.outline.outline360.Outline360Renderer;
import polaris.utils.render.ui.outline.outlinedefault.DefaultOutlineRenderer;
import polaris.utils.render.ui.outline.outlineglass.GlassOutlineRenderer;
import polaris.utils.render.ui.arc.ArcOutlineRenderer;
import polaris.utils.render.ui.arc.ArcRenderer;
import polaris.utils.render.ui.rectangle.rectdefault.DefaultRectangleRenderer;
import polaris.utils.render.ui.rectangle.recthalficon.HalfIconRectangleRenderer;
import polaris.utils.render.ui.rectangle.recthalftone.HalftoneRectangleRenderer;
import polaris.utils.render.ui.ripple.RippleRenderer;
import polaris.utils.render.ui.shine.ShineRenderer;
import polaris.utils.render.ui.zippy.ZippyRenderer;
import polaris.utils.render.item.RenderItem;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    private RenderPass cataclysm$currentRenderPass;
    private boolean cataclysm$blurDrawActive;
    private boolean cataclysm$glassDrawActive;
    private boolean cataclysm$glassOutlineDrawActive;
    private boolean cataclysm$rectangleDrawActive;
    private boolean cataclysm$halfIconRectangleDrawActive;
    private boolean cataclysm$halftoneRectangleDrawActive;
    private boolean cataclysm$zippyDrawActive;
    private boolean cataclysm$arcDrawActive;
    private boolean cataclysm$arcOutlineDrawActive;
    private boolean cataclysm$outlineDrawActive;
    private boolean cataclysm$outline360DrawActive;
    private boolean cataclysm$imageDrawActive;
    private boolean cataclysm$itemDrawActive;
    private boolean cataclysm$rippleDrawActive;
    private boolean cataclysm$shineDrawActive;

    @Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V", ordinal = 0))
    private void cataclysm$useModernWorldProjection(GpuBufferSlice projection, ProjectionType projectionType) {
        GpuBufferSlice override = WorldAnimation.projectionOverride();
        if (override != null) {
            RenderSystem.setProjectionMatrix(override, ProjectionType.PERSPECTIVE);
            return;
        }
        RenderSystem.setProjectionMatrix(projection, projectionType);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cataclysm$beginBlurFrame(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().beginGuiFrame();
        GlassRenderer.getInstance().beginGuiFrame();
        GlassOutlineRenderer.getInstance().beginGuiFrame();
        DefaultRectangleRenderer.getInstance().beginGuiFrame();
        HalfIconRectangleRenderer.getInstance().beginGuiFrame();
        HalftoneRectangleRenderer.getInstance().beginGuiFrame();
        ZippyRenderer.getInstance().beginGuiFrame();
        ArcRenderer.getInstance().beginGuiFrame();
        ArcOutlineRenderer.getInstance().beginGuiFrame();
        DefaultOutlineRenderer.getInstance().beginGuiFrame();
        Outline360Renderer.getInstance().beginGuiFrame();
        ImageRenderer.getInstance().beginGuiFrame();
        RippleRenderer.getInstance().beginGuiFrame();
        ShineRenderer.getInstance().beginGuiFrame();
        RenderItem.beginGuiFrame();
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void cataclysm$preparePendingBlurResources(CallbackInfo ci) {
        BlurFramebuffer.getInstance().preparePending();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void cataclysm$prepareRenderUniforms(CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareBuffers();
        GlassRenderer.getInstance().prepareBuffers();
        GlassOutlineRenderer.getInstance().prepareBuffers();
        DefaultRectangleRenderer.getInstance().prepareBuffers();
        HalfIconRectangleRenderer.getInstance().prepareBuffers();
        HalftoneRectangleRenderer.getInstance().prepareBuffers();
        ZippyRenderer.getInstance().prepareBuffers();
        ArcRenderer.getInstance().prepareBuffers();
        ArcOutlineRenderer.getInstance().prepareBuffers();
        DefaultOutlineRenderer.getInstance().prepareBuffers();
        Outline360Renderer.getInstance().prepareBuffers();
        ImageRenderer.getInstance().prepareBuffers();
        RippleRenderer.getInstance().prepareBuffers();
        ShineRenderer.getInstance().prepareBuffers();
        RenderItem.prepareBuffers();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void cataclysm$prepareBlurCapture(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V", shift = At.Shift.BEFORE))
    private void cataclysm$prepareBlurCaptureAfterBeforeBlur(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void cataclysm$trackPipeline(RenderPass renderPass, RenderPipeline pipeline) {
        cataclysm$currentRenderPass = renderPass;
        cataclysm$blurDrawActive = BlurFramebuffer.getInstance().isBlurPipeline(pipeline);
        cataclysm$glassDrawActive = GlassRenderer.getInstance().isGlassPipeline(pipeline);
        cataclysm$glassOutlineDrawActive = GlassOutlineRenderer.getInstance().isGlassOutlinePipeline(pipeline);
        cataclysm$rectangleDrawActive = DefaultRectangleRenderer.getInstance().isRectanglePipeline(pipeline);
        cataclysm$halfIconRectangleDrawActive = HalfIconRectangleRenderer.getInstance().isHalfIconRectanglePipeline(pipeline);
        cataclysm$halftoneRectangleDrawActive = HalftoneRectangleRenderer.getInstance().isHalftoneRectanglePipeline(pipeline);
        cataclysm$zippyDrawActive = ZippyRenderer.getInstance().isZippyPipeline(pipeline);
        cataclysm$arcDrawActive = ArcRenderer.getInstance().isArcPipeline(pipeline);
        cataclysm$arcOutlineDrawActive = ArcOutlineRenderer.getInstance().isArcOutlinePipeline(pipeline);
        cataclysm$outlineDrawActive = DefaultOutlineRenderer.getInstance().isOutlinePipeline(pipeline);
        cataclysm$outline360DrawActive = Outline360Renderer.getInstance().isOutline360Pipeline(pipeline);
        cataclysm$imageDrawActive = ImageRenderer.getInstance().isImagePipeline(pipeline);
        cataclysm$rippleDrawActive = RippleRenderer.getInstance().isRipplePipeline(pipeline);
        cataclysm$shineDrawActive = ShineRenderer.getInstance().isShinePipeline(pipeline);
        cataclysm$itemDrawActive = RenderItem.isItemPipeline(pipeline);
        renderPass.setPipeline(pipeline);
    }

    @Inject(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V", shift = At.Shift.BEFORE))
    private void cataclysm$bindBlurParams(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        if (cataclysm$blurDrawActive && cataclysm$currentRenderPass != null) {
            BlurFramebuffer.getInstance().bindBlurParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$glassDrawActive && cataclysm$currentRenderPass != null) {
            GlassRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$glassOutlineDrawActive && cataclysm$currentRenderPass != null) {
            GlassOutlineRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$rectangleDrawActive && cataclysm$currentRenderPass != null) {
            DefaultRectangleRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$halfIconRectangleDrawActive && cataclysm$currentRenderPass != null) {
            HalfIconRectangleRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$halftoneRectangleDrawActive && cataclysm$currentRenderPass != null) {
            HalftoneRectangleRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$zippyDrawActive && cataclysm$currentRenderPass != null) {
            ZippyRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$arcDrawActive && cataclysm$currentRenderPass != null) {
            ArcRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$arcOutlineDrawActive && cataclysm$currentRenderPass != null) {
            ArcOutlineRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$outlineDrawActive && cataclysm$currentRenderPass != null) {
            DefaultOutlineRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$outline360DrawActive && cataclysm$currentRenderPass != null) {
            Outline360Renderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$imageDrawActive && cataclysm$currentRenderPass != null) {
            ImageRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$rippleDrawActive && cataclysm$currentRenderPass != null) {
            RippleRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$shineDrawActive && cataclysm$currentRenderPass != null) {
            ShineRenderer.getInstance().bindParams(cataclysm$currentRenderPass);
        }
        if (cataclysm$itemDrawActive && cataclysm$currentRenderPass != null) {
            RenderItem.bindParams(cataclysm$currentRenderPass);
        }
    }

    @Inject(method = "executeDraw", at = @At("RETURN"))
    private void cataclysm$clearTrackedPipeline(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        cataclysm$currentRenderPass = null;
        cataclysm$blurDrawActive = false;
        cataclysm$glassDrawActive = false;
        cataclysm$glassOutlineDrawActive = false;
        cataclysm$rectangleDrawActive = false;
        cataclysm$halfIconRectangleDrawActive = false;
        cataclysm$halftoneRectangleDrawActive = false;
        cataclysm$zippyDrawActive = false;
        cataclysm$arcDrawActive = false;
        cataclysm$arcOutlineDrawActive = false;
        cataclysm$outlineDrawActive = false;
        cataclysm$outline360DrawActive = false;
        cataclysm$imageDrawActive = false;
        cataclysm$itemDrawActive = false;
        cataclysm$rippleDrawActive = false;
        cataclysm$shineDrawActive = false;
    }
}

