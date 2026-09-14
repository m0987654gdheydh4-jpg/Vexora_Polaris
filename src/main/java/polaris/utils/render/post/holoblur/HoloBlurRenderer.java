package polaris.utils.render.post.holoblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;


public final class HoloBlurRenderer {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/post/holoblur");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/holoblur/holoblur");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/holoblur/holoblur");

    
    private static final int UNIFORM_FLOATS = 19;
    private static final int UNIFORM_SIZE = 80;
    private static final ByteBuffer DATA_BUFFER = MemoryUtil.memAlloc(UNIFORM_SIZE);

    private static RenderPipeline pipeline;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture sceneTexture;
    private static GpuTextureView sceneTextureView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private HoloBlurRenderer() {
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    
    public static void apply(float mouseX, float mouseY, float entry, float entryCenterX, float entryCenterY,
                             float time, float[] params) {
        if (disabledAfterError || params == null || params.length < 11 || !init()) {
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

            
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    client.getMainRenderTarget().getColorTexture(), sceneTexture, 0, 0, 0, 0, 0, width, height);

            DATA_BUFFER.clear();
            DATA_BUFFER.putFloat(width);
            DATA_BUFFER.putFloat(height);
            DATA_BUFFER.putFloat(time);
            DATA_BUFFER.putFloat(clamp01(mouseX));
            DATA_BUFFER.putFloat(clamp01(mouseY));
            DATA_BUFFER.putFloat(clamp01(params[0]));                 
            DATA_BUFFER.putFloat(Math.max(0.0f, params[1]));          
            DATA_BUFFER.putFloat(clamp01(params[2]));                 
            DATA_BUFFER.putFloat(Math.max(0.0f, params[3]));          
            DATA_BUFFER.putFloat(Math.max(0.5f, params[4]));          
            DATA_BUFFER.putFloat(params[5]);                          
            DATA_BUFFER.putFloat(Math.max(0.05f, params[6]));         
            DATA_BUFFER.putFloat(clamp01(params[7]));                 
            DATA_BUFFER.putFloat(clamp01(params[8]));                 
            DATA_BUFFER.putFloat(clamp01(params[9]));                 
            DATA_BUFFER.putFloat(clamp01(params[10]));                
            DATA_BUFFER.putFloat(clamp01(entry));
            DATA_BUFFER.putFloat(clamp01(entryCenterX));
            DATA_BUFFER.putFloat(clamp01(entryCenterY));
            while (DATA_BUFFER.position() < UNIFORM_SIZE) {
                DATA_BUFFER.putFloat(0.0f);
            }
            DATA_BUFFER.flip();

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(), DATA_BUFFER);

            GpuTextureView targetView = client.getMainRenderTarget().getColorTextureView();
            try (RenderPass renderPass = encoder.createRenderPass(() -> "cataclysm:holoblur_pass", targetView, OptionalInt.empty())) {
                renderPass.setPipeline(pipeline);
                renderPass.setVertexBuffer(0, dummyVertexBuffer);
                renderPass.bindTexture("Sampler0", sceneTextureView, RenderSampler.linear());
                renderPass.setUniform("HoloData", uniformBuffer.slice());
                renderPass.draw(0, 6);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            disabledAfterError = true;
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
                            .withUniform("HoloData", UniformType.UNIFORM_BUFFER)
                            .withSampler("Sampler0")
                            .withBlend(BlendFunction.TRANSLUCENT)
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
                        () -> "cataclysm:holoblur_dummy_vertex",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        dummyData
                );
            } finally {
                MemoryUtil.memFree(dummyData);
            }

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:holoblur_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
            return true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            disabledAfterError = true;
            pipeline = null;
            return false;
        }
    }

    private static void ensureTextures(int width, int height) {
        if (sceneTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        if (sceneTextureView != null) {
            sceneTextureView.close();
            sceneTextureView = null;
        }
        if (sceneTexture != null) {
            sceneTexture.close();
            sceneTexture = null;
        }
        lastWidth = -1;
        lastHeight = -1;

        sceneTexture = RenderSystem.getDevice().createTexture(
                () -> "cataclysm:holoblur_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, width, height, 1, 1);
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
