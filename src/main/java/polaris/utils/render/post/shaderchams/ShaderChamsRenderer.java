package polaris.utils.render.post.shaderchams;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;


public final class ShaderChamsRenderer {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/post/shaderchams_composite");
    private static final Identifier VS = Identifier.fromNamespaceAndPath("cataclysm", "post/shaderchams/fullscreen");
    private static final Identifier FS = Identifier.fromNamespaceAndPath("cataclysm", "post/shaderchams/composite");
    
    private static final int UNIFORM_BYTES = 96;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dataBuffer;
    private static boolean disabledAfterError;
    private static long lastNanos = -1L;
    private static float animTime;

    private ShaderChamsRenderer() {
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    public static void clear() {
        lastNanos = -1L;
        animTime = 0f;
    }

    public static void apply(RenderTarget target,
                             RenderTarget mask,
                             int mode,
                             float speed,
                             int playerColor,
                             int mobColor,
                             int otherColor,
                             float outlineWidth,
                             float glowRadius,
                             float glowStrength,
                             float fillOpacity,
                             float rainbowScale) {
        if (disabledAfterError
                || target == null || target.getColorTexture() == null
                || mask == null || mask.getColorTexture() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (!ensureReady()) {
            return;
        }

        try {
            tickTime(speed);
            writeUniforms(mode, playerColor, mobColor, otherColor,
                    outlineWidth, glowRadius, glowStrength, fillOpacity, rainbowScale,
                    mask.width, mask.height);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "cataclysm:shaderchams",
                    target.getColorTextureView(),
                    OptionalInt.empty(),
                    null,
                    OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("MaskSampler", mask.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("ShaderChamsData", uniformBuffer.slice());
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.draw(0, 6);
            }
        } catch (Throwable t) {
            disabledAfterError = true;
            t.printStackTrace();
        }
    }

    private static void tickTime(float speed) {
        long now = System.nanoTime();
        if (lastNanos < 0L) {
            lastNanos = now;
            return;
        }
        float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
        lastNanos = now;
        animTime = (animTime + dt * Math.max(0f, speed)) % 10000f;
    }

    private static void writeUniforms(int mode, int player, int mob, int other,
                                      float outlineWidth, float glowRadius, float glowStrength,
                                      float fillOpacity, float rainbowScale,
                                      int width, int height) {
        dataBuffer.clear();
        putColor(player);
        putColor(mob);
        putColor(other);
        dataBuffer.putFloat(mode);
        dataBuffer.putFloat(animTime);
        dataBuffer.putFloat(outlineWidth);
        dataBuffer.putFloat(glowRadius);
        dataBuffer.putFloat(fillOpacity);
        dataBuffer.putFloat(glowStrength);
        dataBuffer.putFloat(1f / Math.max(1, width));
        dataBuffer.putFloat(1f / Math.max(1, height));
        dataBuffer.putFloat(rainbowScale);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(0f);
        dataBuffer.flip();
    }

    private static void putColor(int argb) {
        dataBuffer.putFloat(((argb >> 16) & 0xFF) / 255f);
        dataBuffer.putFloat(((argb >> 8) & 0xFF) / 255f);
        dataBuffer.putFloat((argb & 0xFF) / 255f);
        dataBuffer.putFloat(((argb >> 24) & 0xFF) / 255f);
    }

    private static boolean ensureReady() {
        if (pipeline == null || uniformBuffer == null || dummyVertexBuffer == null || dataBuffer == null) {
            init();
        }
        return pipeline != null && uniformBuffer != null && dummyVertexBuffer != null && dataBuffer != null;
    }

    private static void init() {
        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VS)
                    .withFragmentShader(FS)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("ShaderChamsData", UniformType.UNIFORM_BUFFER)
                    .withSampler("MaskSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            dataBuffer = MemoryUtil.memAlloc(UNIFORM_BYTES);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderchams_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderchams_dummy_vb",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyData
            );
            MemoryUtil.memFree(dummyData);
        } catch (Throwable t) {
            disabledAfterError = true;
            t.printStackTrace();
        }
    }
}
