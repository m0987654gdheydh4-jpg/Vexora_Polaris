package polaris.utils.render.world.blockoutline;

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

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the faces of targeted block boxes filled with the animated
 * "Base warp fBM" shader. Mirrors {@link polaris.utils.render.world.watercaustic.WaterCaustic3D}.
 */
public final class BlockOutline3D {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final int UNIFORM_SIZE = 128;   
    private static final int OUTLINE_DATA_SIZE = 32; 
    private static final int MAX_VERTICES = 8192;
    private static final int VERTEX_SIZE = 24;     
    private static final int VERTEX_STRIDE = 5;

    private static final float[] vertices = new float[MAX_VERTICES * VERTEX_STRIDE];
    private static int vertexCount;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer outlineDataBuffer;
    private static GpuBuffer vertexBuffer;

    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static final Matrix4f viewMatrix = new Matrix4f();
    private static final Matrix4f combinedMatrix = new Matrix4f();
    private static final Matrix4f identityMatrix = new Matrix4f();

    private static double animationTime;
    private static long lastNanos;
    private static boolean disabledAfterError;

    private BlockOutline3D() {
    }

    public static void init() {
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
                    .withLocation(Identifier.fromNamespaceAndPath("cataclysm", "pipeline/world/blockoutline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("cataclysm", "world/shared/particle_billboard"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("cataclysm", "world/blockoutline/blockoutline"))
                    .withVertexFormat(format, VertexFormat.Mode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("OutlineData", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "BlockOutline3D Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
            outlineDataBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "BlockOutline3D Data",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    OUTLINE_DATA_SIZE
            );
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "BlockOutline3D Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    MAX_VERTICES * VERTEX_SIZE
            );
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            disabledAfterError = true;
        }
    }

    public static void setMatrices(Matrix4f projection, Matrix4f view) {
        projectionMatrix.set(projection);
        viewMatrix.set(view);
    }

    public static void begin() {
        if (disabledAfterError) {
            return;
        }
        if (pipeline == null) {
            init();
        }
        vertexCount = 0;
    }

    public static void box(AABB box) {
        float x1 = (float) box.minX;
        float y1 = (float) box.minY;
        float z1 = (float) box.minZ;
        float x2 = (float) box.maxX;
        float y2 = (float) box.maxY;
        float z2 = (float) box.maxZ;

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float rx1 = (float) (x1 - cam.x);
        float ry1 = (float) (y1 - cam.y);
        float rz1 = (float) (z1 - cam.z);
        float rx2 = (float) (x2 - cam.x);
        float ry2 = (float) (y2 - cam.y);
        float rz2 = (float) (z2 - cam.z);

        quad(rx1, ry1, rz1, rx2, ry1, rz1, rx2, ry1, rz2, rx1, ry1, rz2);
        quad(rx1, ry2, rz1, rx1, ry2, rz2, rx2, ry2, rz2, rx2, ry2, rz1);
        quad(rx1, ry1, rz1, rx1, ry2, rz1, rx2, ry2, rz1, rx2, ry1, rz1);
        quad(rx1, ry1, rz2, rx2, ry1, rz2, rx2, ry2, rz2, rx1, ry2, rz2);
        quad(rx1, ry1, rz1, rx1, ry1, rz2, rx1, ry2, rz2, rx1, ry2, rz1);
        quad(rx2, ry1, rz1, rx2, ry2, rz1, rx2, ry2, rz2, rx2, ry1, rz2);
    }

    private static void quad(float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        addVertex(x1, y1, z1, 0, 0);
        addVertex(x2, y2, z2, 1, 0);
        addVertex(x3, y3, z3, 1, 1);
        addVertex(x1, y1, z1, 0, 0);
        addVertex(x3, y3, z3, 1, 1);
        addVertex(x4, y4, z4, 0, 1);
    }

    private static void addVertex(float x, float y, float z, float u, float v) {
        if (vertexCount >= MAX_VERTICES || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return;
        }
        int offset = vertexCount++ * VERTEX_STRIDE;
        vertices[offset] = x;
        vertices[offset + 1] = y;
        vertices[offset + 2] = z;
        vertices[offset + 3] = u;
        vertices[offset + 4] = v;
    }

    public static void end(float opacity, float speed, int tintColor, float tintAmount) {
        if (disabledAfterError) {
            return;
        }
        if (pipeline == null || vertexCount == 0 || uniformBuffer == null || outlineDataBuffer == null || vertexBuffer == null) {
            vertexCount = 0;
            return;
        }

        long now = System.nanoTime();
        if (lastNanos != 0L) {
            double delta = (now - lastNanos) / 1_000_000_000.0;
            animationTime += Math.clamp(delta, 0.0, 0.1) * Math.max(speed, 0.0f);
        }
        lastNanos = now;

        int count = Math.min(vertexCount, MAX_VERTICES);

        Matrix4f combined = combinedMatrix.set(projectionMatrix).mul(viewMatrix);
        if (!isFiniteMatrix(combined)) {
            vertexCount = 0;
            return;
        }

        ByteBuffer uniformData = MemoryUtil.memAlloc(UNIFORM_SIZE);
        putMatrix(uniformData, combined);
        putMatrix(uniformData, identityMatrix);
        uniformData.flip();

        RenderTarget framebuffer = mc.getMainRenderTarget();
        float resW = framebuffer != null ? framebuffer.width : 1920.0f;
        float resH = framebuffer != null ? framebuffer.height : 1080.0f;

        ByteBuffer outlineData = MemoryUtil.memAlloc(OUTLINE_DATA_SIZE);
        outlineData.putFloat(resW).putFloat(resH).putFloat((float) animationTime).putFloat(Math.clamp(opacity, 0.0f, 1.0f));
        float tr = ((tintColor >> 16) & 0xFF) / 255.0f;
        float tg = ((tintColor >> 8) & 0xFF) / 255.0f;
        float tb = (tintColor & 0xFF) / 255.0f;
        outlineData.putFloat(tr).putFloat(tg).putFloat(tb).putFloat(Math.clamp(tintAmount, 0.0f, 1.0f));
        outlineData.flip();

        ByteBuffer vertexData = MemoryUtil.memAlloc(count * VERTEX_SIZE);
        for (int i = 0; i < count; i++) {
            int offset = i * VERTEX_STRIDE;
            vertexData.putFloat(vertices[offset]).putFloat(vertices[offset + 1]).putFloat(vertices[offset + 2]);
            vertexData.putInt(0xFFFFFFFF);
            vertexData.putFloat(vertices[offset + 3]).putFloat(vertices[offset + 4]);
        }
        vertexData.flip();

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(), uniformData);
            encoder.writeToBuffer(outlineDataBuffer.slice(), outlineData);
            encoder.writeToBuffer(vertexBuffer.slice(), vertexData);

            if (framebuffer == null || framebuffer.getColorTextureView() == null || framebuffer.getDepthTextureView() == null) {
                vertexCount = 0;
                return;
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "BlockOutline3D",
                    framebuffer.getColorTextureView(),
                    OptionalInt.empty(),
                    framebuffer.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                pass.setUniform("Uniforms", uniformBuffer);
                pass.setUniform("OutlineData", outlineDataBuffer);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.draw(0, count);
            }
        } catch (Throwable throwable) {
            disabledAfterError = true;
        } finally {
            MemoryUtil.memFree(uniformData);
            MemoryUtil.memFree(outlineData);
            MemoryUtil.memFree(vertexData);
        }

        vertexCount = 0;
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

