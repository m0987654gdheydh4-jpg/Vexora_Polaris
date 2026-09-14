package polaris.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import polaris.IMinecraft;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.impl.visual.Ambience;
import polaris.api.module.impl.visual.FogBlur;
import polaris.api.module.impl.visual.KillEffect;
import polaris.api.module.impl.visual.NoRender;
import polaris.api.module.impl.visual.ShaderChams;
import polaris.api.module.impl.visual.ShaderFog;
import polaris.manager.Manager;
import polaris.screens.clickgui.ClickGuiOpenEffects;
import polaris.utils.render.Render3D;
import polaris.utils.render.RenderCompatibility;
import polaris.utils.render.ui.blur.BlurFramebuffer;
import polaris.utils.render.post.fogblur.FogBlurRenderer;
import polaris.utils.render.world.starsparkle.StarSparkle3D;
import polaris.utils.render.world.blockoutline.BlockOutline3D;
import polaris.utils.render.world.watercaustic.WaterCaustic3D;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin implements IMinecraft {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow @Final private SkyRenderer skyRenderer;
    @Shadow private ClientLevel level;
    @Shadow private RenderTarget entityOutlineTarget;

    
    @Redirect(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
            ),
            require = 0
    )
    private PostChain cataclysm$skipOutlineSobel(ShaderManager manager, net.minecraft.resources.Identifier id, java.util.Set<net.minecraft.resources.Identifier> targetSet) {
        if (ShaderChams.isActive() && "entity_outline".equals(id.getPath())) {
            return null;
        }
        return manager.getPostChain(id, targetSet);
    }

    
    @Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$shaderChamsComposite(CallbackInfo ci) {
        ShaderChams chams = ShaderChams.getInstance();
        if (chams == null || !chams.isEnabled()) {
            return;
        }
        ci.cancel();
        if (ShaderChams.isActive() && entityOutlineTarget != null) {
            chams.onComposite(minecraft.getMainRenderTarget(), entityOutlineTarget);
        }
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void onRenderSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice fog, CallbackInfo ci) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.shouldHideSkyBackground() || level == null) {
            return;
        }

        ci.cancel();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderHead(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PoseStack captureStack = new PoseStack();
        captureStack.mulPose(new Matrix4f(positionMatrix));
        Render3D.lastProjMat.set(projectionMatrix);
        Render3D.lastModMat.set(positionMatrix);
        Render3D.lastWorldSpaceMatrix.set(captureStack.last().pose());
        Render3D.setLastWorldSpaceEntry(captureStack.last());
        Render3D.setLastTickDelta(tickCounter.getGameTimeDeltaPartialTick(true));
        Render3D.setLastCameraPos(camera.position());
        Render3D.setLastCameraRotation(new Quaternionf(camera.rotation()));
        WaterCaustic3D.setMatrices(projectionMatrix, positionMatrix);
        StarSparkle3D.setMatrices(projectionMatrix, positionMatrix);
        BlockOutline3D.setMatrices(projectionMatrix, positionMatrix);

        if (fogColor == null) {
            return;
        }

        
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isEnabled() && ambience.hasCustomSkyColor()) {
            int customColor = ambience.getCustomSkyColor();
            
            float r = ((customColor >> 16) & 0xFF) / 255.0f;
            float g = ((customColor >> 8) & 0xFF) / 255.0f;
            float b = (customColor & 0xFF) / 255.0f;
            
            fogColor.set(
                    fogColor.x * 0.25f + r * 0.75f,
                    fogColor.y * 0.25f + g * 0.75f,
                    fogColor.z * 0.25f + b * 0.75f,
                    fogColor.w
            );
        }

        float saturation = KillEffect.getWorldSaturationMultiplier();
        if (ambience != null && ambience.isEnabled()) {
            saturation *= ambience.getSaturationFactor();
        }
        if (Float.isFinite(saturation) && Math.abs(saturation - 1.0F) > 0.0005F) {
            saturation = Math.clamp(saturation, 0.0F, 2.0F);
            float luminance = fogColor.x * 0.2126F + fogColor.y * 0.7152F + fogColor.z * 0.0722F;
            fogColor.set(
                    luminance + (fogColor.x - luminance) * saturation,
                    luminance + (fogColor.y - luminance) * saturation,
                    luminance + (fogColor.z - luminance) * saturation,
                    fogColor.w
            );
        }

        BlurFramebuffer.setSkyFallbackColor(fogColor.x, fogColor.y, fogColor.z);
        FogBlurRenderer.setFallbackColor(fogColor.x, fogColor.y, fogColor.z);
    }

    @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
    private void onHasBlindnessOrDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
        NoRender noRender = NoRender.getInstance();
        if (noRender == null || !noRender.isEnabled()) return;

        Entity entity = camera.entity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        boolean hasBlindness = livingEntity.hasEffect(MobEffects.BLINDNESS);
        boolean hasDarkness = livingEntity.hasEffect(MobEffects.DARKNESS);

        if (NoRender.isActive("Bad Effects") && hasBlindness && !hasDarkness) {
            cir.setReturnValue(false);
        }

        if (NoRender.isActive("Darkness") && hasDarkness && !hasBlindness) {
            cir.setReturnValue(false);
        }

        if (NoRender.isActive("Bad Effects") && NoRender.isActive("Darkness")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderWorld(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        

        RenderCompatibility.primeFromCurrentContext();

        PoseStack stack = new PoseStack();
        stack.mulPose(new Matrix4f(positionMatrix));

        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int[] prevScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, prevScissorBox);

        try {
            WorldRenderEvent event = new WorldRenderEvent(stack, tickCounter.getGameTimeDeltaPartialTick(true));
            Manager.postEvent(event);
            Render3D.onWorldRender(event);
        } finally {
            restoreRenderState(
                    prevDepthTest,
                    prevBlend,
                    prevCull,
                    prevScissor,
                    prevDepthMask,
                    prevDepthFunc,
                    prevBlendSrcRgb,
                    prevBlendDstRgb,
                    prevBlendSrcAlpha,
                    prevBlendDstAlpha,
                    prevScissorBox
            );
        }
    }

    @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void cataclysm$fragEffectAfterTranslucent(GpuBufferSlice fog, LevelRenderState levelRenderState, ProfilerFiller profiler, Matrix4f positionMatrix,
                                                  ResourceHandle<RenderTarget> mainTarget, ResourceHandle<RenderTarget> translucentTarget, boolean renderBlockOutline,
                                                  ResourceHandle<RenderTarget> itemEntityTarget, ResourceHandle<RenderTarget> entityOutlineTarget, CallbackInfo ci) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        RenderTarget target = mainTarget != null ? mainTarget.get() : minecraft.getMainRenderTarget();
        FogBlur fogBlur = FogBlur.getInstance();
        if (fogBlur != null && fogBlur.isEnabled()) {
            fogBlur.onAfterTranslucent(target);
        }

        ShaderFog shaderFog = ShaderFog.getInstance();
        if (shaderFog != null && shaderFog.isEnabled()) {
            shaderFog.onAfterTranslucent(target);
        }

        KillEffect killEffect = KillEffect.getInstanceIfReady();
        if (killEffect != null && killEffect.isEnabled()) {
            killEffect.onAfterTranslucent(target, Render3D.lastProjMat, positionMatrix, Render3D.lastCameraPos);
        } else {
            
            ClickGuiOpenEffects.renderScanIfNeeded(
                    target, Render3D.lastProjMat, positionMatrix, Render3D.lastCameraPos);
        }
    }

    private void restoreRenderState(boolean depthTest, boolean blend, boolean cull, boolean scissor, boolean depthMask, int depthFunc, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha, int[] scissorBox) {
        if (depthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthMask(depthMask);
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._colorMask(true, true, true, true);

        if (blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        } else {
            GlStateManager._disableBlend();
        }

        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }

        if (scissor) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
    }

    
    @Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void cataclysm$torchLight(LevelRenderer.BrightnessGetter brightnessGetter, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!Ambience.isTorchLightActive()) {
            return;
        }
        int extra = Ambience.torchLightAt(pos.getX(), pos.getY(), pos.getZ());
        if (extra <= 0) {
            return;
        }
        int packed = cir.getReturnValueI();
        if (extra > LightTexture.block(packed)) {
            cir.setReturnValue(LightTexture.pack(extra, LightTexture.sky(packed)));
        }
    }
}
