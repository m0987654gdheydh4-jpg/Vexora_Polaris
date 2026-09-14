package polaris.utils.render.post.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;


public final class MotionBlurRenderer {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/post/motionblur");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/motionblur/motionblur");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/motionblur/motionblur");

    private static final int UNIFORM_SIZE = 16;
    private static final ByteBuffer DATA_BUFFER = MemoryUtil.memAlloc(UNIFORM_SIZE);

    private static RenderPipeline pipeline;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture sceneTexture;
    private static GpuTextureView sceneTextureView;
    private static GpuTexture historyTexture;
    private static GpuTextureView historyTextureView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean historyValid;
    private static boolean disabledAfterError;
    
    private static int consecutiveFailures;
    private static final int FAILURE_LIMIT = 3;
    private static final Logger LOGGER = LogUtils.getLogger();

    private MotionBlurRenderer() {
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    
    public static void reset() {
        historyValid = false;
    }

    
    public static void clearError() {
        disabledAfterError = false;
        consecutiveFailures = 0;
        historyValid = false;
    }

    
    public static void apply(float blend, float threshold) {
        if (disabledAfterError || !init()) {
            return;
        }
        if (!Float.isFinite(blend) || blend <= 0.001f) {
            historyValid = false;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null) {
            return;
        }
        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            ensureTextures(width, height);
            GpuTexture main = client.getMainRenderTarget().getColorTexture();

            if (!historyValid) {
                
                RenderSystem.getDevice().createCommandEncoder()
                        .copyTextureToTexture(main, historyTexture, 0, 0, 0, 0, 0, width, height);
                historyValid = true;
                return;
            }

            
            RenderSystem.getDevice().createCommandEncoder()
                    .copyTextureToTexture(main, sceneTexture, 0, 0, 0, 0, 0, width, height);

            DATA_BUFFER.clear();
            DATA_BUFFER.putFloat(Math.clamp(blend, 0.0f, 0.95f));
            DATA_BUFFER.putFloat(Math.max(0.001f, threshold));
            DATA_BUFFER.putFloat(0.0f);
            DATA_BUFFER.putFloat(0.0f);
            DATA_BUFFER.flip();

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(), DATA_BUFFER);

            GpuTextureView targetView = client.getMainRenderTarget().getColorTextureView();
            try (RenderPass renderPass = encoder.createRenderPass(() -> "cataclysm:motionblur_pass", targetView, OptionalInt.empty())) {
                renderPass.setPipeline(pipeline);
                renderPass.setVertexBuffer(0, dummyVertexBuffer);
                renderPass.bindTexture("Sampler0", sceneTextureView, RenderSampler.linear());
                renderPass.bindTexture("Sampler1", historyTextureView, RenderSampler.linear());
                renderPass.setUniform("MotionBlurData", uniformBuffer.slice());
                renderPass.draw(0, 6);
            }

            
            RenderSystem.getDevice().createCommandEncoder()
                    .copyTextureToTexture(main, historyTexture, 0, 0, 0, 0, 0, width, height);
            consecutiveFailures = 0;
        } catch (Throwable throwable) {
            historyValid = false;
            consecutiveFailures++;
            LOGGER.error("[Polaris] MotionBlur pass failed ({}/{})", consecutiveFailures, FAILURE_LIMIT, throwable);
            if (consecutiveFailures >= FAILURE_LIMIT) {
                disabledAfterError = true;
                LOGGER.error("[Polaris] MotionBlur disabled after {} consecutive failures", FAILURE_LIMIT);
            }
        }
    }

    private static boolean init() {
        if (pipeline != null) {
            return true;
        }
        try {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder()
                            .withLocation(PIPELINE_ID)
                            .withVertexShader(VERTEX_SHADER)
                            .withFragmentShader(FRAGMENT_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("MotionBlurData", UniformType.UNIFORM_BUFFER)
                            .withSampler("Sampler0")
                            .withSampler("Sampler1")
                            
                            
                            
                            
                            .withoutBlend()
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );

            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            try {
                dummyData.putInt(0);
                dummyData.flip();
                dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "cataclysm:motionblur_dummy_vertex",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        dummyData
                );
            } finally {
                MemoryUtil.memFree(dummyData);
            }

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:motionblur_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("[Polaris] MotionBlur pipeline could not be built", throwable);
            disabledAfterError = true;
            pipeline = null;
            return false;
        }
    }

    private static void ensureTextures(int width, int height) {
        if (sceneTexture != null && historyTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        closeTextures();
        lastWidth = -1;
        lastHeight = -1;
        historyValid = false;

        sceneTexture = RenderSystem.getDevice().createTexture(
                () -> "cataclysm:motionblur_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);

        historyTexture = RenderSystem.getDevice().createTexture(
                () -> "cataclysm:motionblur_history",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        historyTextureView = RenderSystem.getDevice().createTextureView(historyTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private static void closeTextures() {
        if (sceneTextureView != null) {
            sceneTextureView.close();
            sceneTextureView = null;
        }
        if (sceneTexture != null) {
            sceneTexture.close();
            sceneTexture = null;
        }
        if (historyTextureView != null) {
            historyTextureView.close();
            historyTextureView = null;
        }
        if (historyTexture != null) {
            historyTexture.close();
            historyTexture = null;
        }
    }
}
