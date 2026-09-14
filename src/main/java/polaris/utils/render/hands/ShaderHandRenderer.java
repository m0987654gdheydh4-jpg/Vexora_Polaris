package polaris.utils.render.hands;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;


public final class ShaderHandRenderer {
    private static final Identifier VERTEX_SHADER =
            Identifier.fromNamespaceAndPath("cataclysm", "effects/shaderhand/fullscreen");

    
    private static final int UNIFORM_BYTES = 64;
    private static final int UNIFORM_USED_BYTES = 48;

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[ShaderHandMode.count()];

    private static boolean enabled;
    private static ShaderHandMode mode = ShaderHandMode.AQUA;
    private static boolean animate = true;
    private static float timeSpeed = 1.0f;
    private static float effectAlpha = 1.0f;
    private static float patternSpeed = 1.0f;
    private static float shift = 1.6f;
    private static float handMotionX = 0.72f;
    private static float handMotionY = 0.22f;

    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dataBuffer;
    private static GpuTexture sceneTexture;
    private static GpuTextureView sceneView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static boolean armed;
    private static int consecutiveErrors;

    
    private static long lastNanos = -1L;
    private static float animTime;

    private ShaderHandRenderer() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        consecutiveErrors = 0;
        if (!value) {
            armed = false;
        }
    }

    public static void configure(int modeIndex, boolean anim, float timeSpeedValue, float effectAlphaPercent,
                                 float patternSpeedValue, float shiftValue, float motionX, float motionY) {
        mode = ShaderHandMode.byIndex(modeIndex);
        animate = anim;
        timeSpeed = Math.max(0.0f, timeSpeedValue);
        effectAlpha = clamp01(effectAlphaPercent / 100.0f);
        patternSpeed = Math.max(0.0f, patternSpeedValue);
        shift = Math.max(0.0f, shiftValue);
        handMotionX = motionX;
        handMotionY = motionY;
    }

    public static boolean shouldRender() {
        if (!enabled || consecutiveErrors > 20) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.level != null;
    }

    
    public static void armForHandRender() {
        armed = shouldRender();
    }

    public static boolean isArmed() {
        return armed;
    }

    public static void renderAfterHands() {
        if (!armed) {
            return;
        }
        armed = false;
        if (!shouldRender()) {
            return;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (!isUsable(target)) {
            return;
        }

        try {
            RenderPipeline pipeline = pipelineFor(mode);
            if (pipeline == null || !ensureResources(target.width, target.height)) {
                return;
            }

            tickTime();
            writeUniforms(target.width, target.height);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
            
            
            
            encoder.copyTextureToTexture(target.getColorTexture(), sceneTexture, 0, 0, 0, 0, 0,
                    target.width, target.height);
            drawShader(encoder, target, pipeline);

            consecutiveErrors = Math.max(0, consecutiveErrors - 1);
        } catch (Throwable throwable) {
            onSoftError(throwable);
        }
    }

    private static void drawShader(CommandEncoder encoder, RenderTarget target, RenderPipeline pipeline) {
        try (RenderPass pass = encoder.createRenderPass(
                () -> "cataclysm:shaderhand",
                target.getColorTextureView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("ColorTexture", sceneView, RenderSampler.linear());
            
            
            pass.bindTexture("DepthTexture", target.getDepthTextureView(), RenderSampler.nearest());
            pass.setUniform("ShaderHandData", uniformBuffer.slice());
            pass.setVertexBuffer(0, dummyVertexBuffer);
            pass.draw(0, 6);
        }
    }

    
    private static void tickTime() {
        long now = System.nanoTime();
        if (lastNanos < 0L) {
            lastNanos = now;
            return;
        }
        float delta = Math.min((now - lastNanos) / 1_000_000_000.0f, 0.1f);
        lastNanos = now;
        if (animate) {
            animTime = (animTime + delta * timeSpeed) % 100000.0f;
        }
    }

    
    private static void writeUniforms(int width, int height) {
        float speed = mode.isPatterned() ? patternSpeed : 1.0f;

        dataBuffer.clear();
        dataBuffer.putFloat(Math.max(1.0f, width));
        dataBuffer.putFloat(Math.max(1.0f, height));
        dataBuffer.putFloat(handMotionX);
        dataBuffer.putFloat(handMotionY);
        dataBuffer.putFloat(speed);
        dataBuffer.putFloat(speed);
        
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(animTime);
        dataBuffer.putFloat(shift);
        dataBuffer.putFloat(effectAlpha);
        dataBuffer.putFloat(0.0f);
        dataBuffer.flip();
    }

    private static RenderPipeline pipelineFor(ShaderHandMode target) {
        int index = target.ordinal();
        if (PIPELINES[index] != null) {
            return PIPELINES[index];
        }
        try {
            PIPELINES[index] = RenderPipeline.builder()
                    .withLocation(target.pipelineId())
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(target.fragmentShader())
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("ShaderHandData", UniformType.UNIFORM_BUFFER)
                    .withSampler("ColorTexture")
                    .withSampler("DepthTexture")
                    
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        } catch (Throwable throwable) {
            onSoftError(throwable);
            PIPELINES[index] = null;
        }
        return PIPELINES[index];
    }

    private static boolean ensureResources(int width, int height) {
        if (uniformBuffer == null || dummyVertexBuffer == null || dataBuffer == null) {
            initBuffers();
        }
        ensureTextures(width, height);
        return uniformBuffer != null && dummyVertexBuffer != null && dataBuffer != null
                && sceneTexture != null && sceneView != null;
    }

    private static void initBuffers() {
        
        
        releaseBuffers();
        try {
            dataBuffer = MemoryUtil.memAlloc(UNIFORM_USED_BYTES);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderhand_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderhand_dummy_vb",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyData
            );
            MemoryUtil.memFree(dummyData);
        } catch (Throwable throwable) {
            onSoftError(throwable);
            releaseBuffers();
        }
    }

    private static void ensureTextures(int width, int height) {
        if (sceneTexture != null && lastWidth == width && lastHeight == height) {
            return;
        }
        closeTextures();
        try {
            sceneTexture = RenderSystem.getDevice().createTexture(
                    () -> "cataclysm:shaderhand_scene",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, width, height, 1, 1);
            sceneView = RenderSystem.getDevice().createTextureView(sceneTexture);
            lastWidth = width;
            lastHeight = height;
        } catch (Throwable throwable) {
            onSoftError(throwable);
            closeTextures();
        }
    }

    private static void releaseBuffers() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
    }

    private static void closeTextures() {
        if (sceneView != null) {
            sceneView.close();
            sceneView = null;
        }
        if (sceneTexture != null) {
            sceneTexture.close();
            sceneTexture = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }

    private static boolean isUsable(RenderTarget target) {
        return target != null
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.getDepthTextureView() != null
                && target.width > 0
                && target.height > 0;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static void onSoftError(Throwable throwable) {
        consecutiveErrors++;
        armed = false;
        if (consecutiveErrors <= 3) {
            throwable.printStackTrace();
        }
    }
}
