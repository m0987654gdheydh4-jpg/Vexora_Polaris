package polaris.utils.render.post.saturation;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public class Saturation2D {

    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/post/saturation");
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/saturation/saturation");
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("cataclysm", "post/saturation/saturation");

    private static final ByteBuffer DATA_BUFFER = MemoryUtil.memAlloc(16);

    private static RenderPipeline pipeline;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuBuffer uniformBuffer;

    private static GpuTexture tempTexture;
    private static GpuTextureView tempTextureView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    
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
                            .withUniform("SaturationData", UniformType.UNIFORM_BUFFER)
                            .withSampler("Sampler0")
                            
                            
                            
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
                        () -> "cataclysm:saturation_dummy_vertex",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        dummyData
                );
            } finally {
                MemoryUtil.memFree(dummyData);
            }

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:saturation_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    16
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
        if (tempTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        
        
        if (tempTextureView != null) {
            tempTextureView.close();
            tempTextureView = null;
        }
        if (tempTexture != null) {
            tempTexture.close();
            tempTexture = null;
        }
        lastWidth = -1;
        lastHeight = -1;

        tempTexture = RenderSystem.getDevice().createTexture(
                () -> "cataclysm:saturation_temp",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
        tempTextureView = RenderSystem.getDevice().createTextureView(tempTexture);
        lastWidth = width;
        lastHeight = height;
    }

    public static void applyWithCopy(float saturation) {
        if (disabledAfterError || !init()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getMainRenderTarget() == null) {
            return;
        }
        if (!Float.isFinite(saturation)) {
            return;
        }
        float clampedSaturation = Math.clamp(saturation, 0.0f, 2.0f);
        if (Math.abs(clampedSaturation - 1.0f) <= 0.0005f) {
            return;
        }

        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        if (width <= 0 || height <= 0) {
            return;
        }
        ensureTextures(width, height);

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(
                    client.getMainRenderTarget().getColorTexture(),
                    tempTexture,
                    0,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height
            );

            apply(client.getMainRenderTarget().getColorTextureView(), tempTextureView, clampedSaturation);
        } catch (Throwable ignored) {
            disabledAfterError = true;
        }
    }

    private static void apply(GpuTextureView targetView, GpuTextureView sourceView, float saturation) {
        DATA_BUFFER.clear();
        DATA_BUFFER.putFloat(saturation);
        DATA_BUFFER.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(), DATA_BUFFER);

        try (RenderPass renderPass = encoder.createRenderPass(() -> "cataclysm:saturation_pass", targetView, OptionalInt.empty())) {
            renderPass.setPipeline(pipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", sourceView, RenderSampler.linear());
            renderPass.setUniform("SaturationData", uniformBuffer.slice());
            renderPass.draw(0, 6);
        }
    }
}

