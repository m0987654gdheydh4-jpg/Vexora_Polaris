package polaris.utils.render.world.skyshader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.resources.Identifier;

/**
 * Fullscreen procedural sky shader. Renders an animated sky (aurora, galaxy,
 * sunset, daylight, digital) behind the world geometry by drawing a fullscreen
 * quad at the far depth plane with a LEQUAL depth test, so the world occludes it.
 * Mirrors the proven rendering path used by
 * {@link polaris.utils.render.world.blockoutline.BlockOutline3D}.
 */
public final class SkyShader3D {
    
    private static final int UNIFORM_SIZE = 112;
    private static final int VERTEX_COUNT = 6;
    private static final int VERTEX_SIZE = 24; 

    
    private static final float[] QUAD = {
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 1.0f
    };

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer vertexBuffer;
    private static boolean disabledAfterError;

    private static final Matrix4f combined = new Matrix4f();

    private static double animationTime;
    private static long lastNanos;

    private SkyShader3D() {
    }

    private static void init() {
        if (pipeline != null) {
            return;
        }
        try {
            VertexFormat format = VertexFormat.builder()
                    .add("inPosition", VertexFormatElement.POSITION)
                    .add("inColor", VertexFormatElement.COLOR)
                    .add("inUV", VertexFormatElement.UV)
                    .build();

            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("cataclysm", "pipeline/world/skyshader"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("cataclysm", "world/skyshader/fullscreen"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("cataclysm", "world/skyshader/skyshader"))
                    .withVertexFormat(format, VertexFormat.Mode.TRIANGLES)
                    .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "SkyShader3D Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );

            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "SkyShader3D Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    VERTEX_COUNT * VERTEX_SIZE
            );

            ByteBuffer vertexData = MemoryUtil.memAlloc(VERTEX_COUNT * VERTEX_SIZE);
            for (int i = 0; i < VERTEX_COUNT; i++) {
                int offset = i * 4;
                
                vertexData.putFloat(QUAD[offset]).putFloat(QUAD[offset + 1]).putFloat(1.0f);
                vertexData.putInt(0xFFFFFFFF);
                vertexData.putFloat(QUAD[offset + 2]).putFloat(QUAD[offset + 3]);
            }
            vertexData.flip();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertexData);
            MemoryUtil.memFree(vertexData);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            disabledAfterError = true;
        }
    }

    /**
     * Renders the fullscreen sky shader to the given render target.
     *
     * @param projection projection matrix of the current frame
     * @param modelView  camera model-view matrix (rotation only, world relative to camera)
     * @param target     render target to draw the sky into
     * @param style      sky style index (0 = aurora, 1 = galaxy, 2 = sunset, 3 = daylight, 4 = digital)
     * @param colorA     primary color (rgb packed int)
     * @param colorB     secondary color (rgb packed int)
     * @param speed      animation speed multiplier
     * @param opacity    overall opacity 0..1
     */
    public static void render(Matrix4f projection, Matrix4f modelView, RenderTarget target,
                              int style, int colorA, int colorB, float speed, float opacity) {
        if (disabledAfterError) {
            return;
        }
        if (pipeline == null) {
            init();
        }
        if (pipeline == null || uniformBuffer == null || vertexBuffer == null) {
            return;
        }
        if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            return;
        }

        long now = System.nanoTime();
        if (lastNanos != 0L) {
            double delta = (now - lastNanos) / 1_000_000_000.0;
            animationTime += Math.clamp(delta, 0.0, 0.1) * Math.max(speed, 0.0f);
        }
        lastNanos = now;
        float time = (float) animationTime;

        combined.set(projection).mul(modelView);
        Matrix4f invViewProj = new Matrix4f(combined);
        try {
            invViewProj.invert();
        } catch (Throwable throwable) {
            return;
        }
        if (!isFiniteMatrix(invViewProj)) {
            return;
        }

        ByteBuffer uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        putMatrix(uniformData, invViewProj);

        float ar = ((colorA >> 16) & 0xFF) / 255.0f;
        float ag = ((colorA >> 8) & 0xFF) / 255.0f;
        float ab = (colorA & 0xFF) / 255.0f;
        uniformData.putFloat(ar).putFloat(ag).putFloat(ab).putFloat(1.0f);

        float br = ((colorB >> 16) & 0xFF) / 255.0f;
        float bg = ((colorB >> 8) & 0xFF) / 255.0f;
        float bb = (colorB & 0xFF) / 255.0f;
        uniformData.putFloat(br).putFloat(bg).putFloat(bb).putFloat(1.0f);

        uniformData.putFloat(time).putFloat(style).putFloat(Math.clamp(opacity, 0.0f, 1.0f)).putFloat(0.0f);
        uniformData.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(), uniformData);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "SkyShader3D",
                    target.getColorTextureView(),
                    OptionalInt.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                pass.setUniform("SkyData", uniformBuffer);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.draw(0, VERTEX_COUNT);
            }
        } catch (Throwable throwable) {
            disabledAfterError = true;
        } finally {
            MemoryUtil.memFree(uniformData);
        }
    }

    private static boolean isFiniteMatrix(Matrix4f m) {
        return Float.isFinite(m.m00()) && Float.isFinite(m.m01()) && Float.isFinite(m.m02()) && Float.isFinite(m.m03())
                && Float.isFinite(m.m10()) && Float.isFinite(m.m11()) && Float.isFinite(m.m12()) && Float.isFinite(m.m13())
                && Float.isFinite(m.m20()) && Float.isFinite(m.m21()) && Float.isFinite(m.m22()) && Float.isFinite(m.m23())
                && Float.isFinite(m.m30()) && Float.isFinite(m.m31()) && Float.isFinite(m.m32()) && Float.isFinite(m.m33());
    }

    private static void putMatrix(ByteBuffer buffer, Matrix4f matrix) {
        buffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
        buffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
        buffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
        buffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
    }
}

