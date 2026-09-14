package polaris.utils.render.world.glasstorus;

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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Rockstar Torus mesh + Glass shader (light screen-space water).
 */
public final class GlassTorus3D {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int UNIFORM_SIZE = 112;
    private static final int MAX_VERTICES = 48_000;
    private static final int VERTEX_SIZE = 16;
    
    private static final int SEGMENTS = 20;

    private static final float[] worldX = new float[MAX_VERTICES];
    private static final float[] worldY = new float[MAX_VERTICES];
    private static final float[] worldZ = new float[MAX_VERTICES];
    private static final int[] colors = new int[MAX_VERTICES];
    private static int vertexCount;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer vertexBuffer;
    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneCopyView;
    private static int copyW, copyH;
    private static boolean disabled;
    private static int errorCount;
    private static boolean initedOnce;

    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static final Matrix4f mvp = new Matrix4f();
    private static final Matrix4f model = new Matrix4f();
    private static final Vector3f tmp = new Vector3f();

    private static float lastNoise, lastReflect, lastBlur;

    private GlassTorus3D() {
    }

    public static void setMatrices(Matrix4f proj, Matrix4f viewMat) {
        if (proj != null) projection.set(proj);
        if (viewMat != null) view.set(viewMat);
    }

    public static void begin() {
        if (disabled) return;
        if (pipeline == null) init();
        vertexCount = 0;
    }

    /**
     * Rockstar TorusObj.render — exact mesh + orientation.
     */
    public static void renderRockstar(
            Vec3 worldPos,
            float yawDeg,
            float pitchDeg,
            float outerRadius,
            float innerRadius,
            float noise,
            float reflect,
            float blur
    ) {
        if (disabled || pipeline == null) return;
        if (outerRadius < 0.01f && innerRadius < 0.005f) return;
        if (vertexCount + SEGMENTS * SEGMENTS * 6 >= MAX_VERTICES) return;

        lastNoise = noise;
        lastReflect = reflect;
        lastBlur = Math.max(0f, blur);

        
        
        
        
        
        Vec3 cam = MC.gameRenderer.getMainCamera().position();
        model.identity()
                .translate((float) (worldPos.x - cam.x), (float) (worldPos.y - cam.y), (float) (worldPos.z - cam.z))
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg - 90.0f));

        
        int white = 0xFFFFFFFF;

        for (int i = 0; i < SEGMENTS; i++) {
            double th1 = 2.0 * Math.PI * i / SEGMENTS;
            double th2 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
            for (int j = 0; j < SEGMENTS; j++) {
                double ph1 = 2.0 * Math.PI * j / SEGMENTS;
                double ph2 = 2.0 * Math.PI * (j + 1) / SEGMENTS;

                
                float x1 = (float) ((outerRadius + innerRadius * Math.cos(ph1)) * Math.cos(th1));
                float y1 = (float) (innerRadius * Math.sin(ph1));
                float z1 = (float) ((outerRadius + innerRadius * Math.cos(ph1)) * Math.sin(th1));

                float x2 = (float) ((outerRadius + innerRadius * Math.cos(ph2)) * Math.cos(th1));
                float y2 = (float) (innerRadius * Math.sin(ph2));
                float z2 = (float) ((outerRadius + innerRadius * Math.cos(ph2)) * Math.sin(th1));

                float x3 = (float) ((outerRadius + innerRadius * Math.cos(ph2)) * Math.cos(th2));
                float y3 = (float) (innerRadius * Math.sin(ph2));
                float z3 = (float) ((outerRadius + innerRadius * Math.cos(ph2)) * Math.sin(th2));

                float x4 = (float) ((outerRadius + innerRadius * Math.cos(ph1)) * Math.cos(th2));
                float y4 = (float) (innerRadius * Math.sin(ph1));
                float z4 = (float) ((outerRadius + innerRadius * Math.cos(ph1)) * Math.sin(th2));

                
                put(x1, y1, z1, white);
                put(x2, y2, z2, white);
                put(x3, y3, z3, white);
                put(x1, y1, z1, white);
                put(x3, y3, z3, white);
                put(x4, y4, z4, white);
            }
        }
    }

    public static void end() {
        if (disabled || pipeline == null || vertexCount == 0) {
            vertexCount = 0;
            return;
        }
        RenderTarget target = MC.getMainRenderTarget();
        if (target == null || target.getColorTexture() == null || target.getColorTextureView() == null
                || target.getDepthTextureView() == null) {
            vertexCount = 0;
            return;
        }

        try {
            ensureSceneCopy(target.width, target.height);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(
                    target.getColorTexture(), sceneCopy,
                    0, 0, 0, 0, 0, target.width, target.height
            );

            ByteBuffer vbo = MemoryUtil.memAlloc(vertexCount * VERTEX_SIZE);
            ByteBuffer ubo = MemoryUtil.memAlloc(UNIFORM_SIZE);
            try {
                
                for (int i = 0; i < vertexCount; i++) {
                    vbo.putFloat(worldX[i]);
                    vbo.putFloat(worldY[i]);
                    vbo.putFloat(worldZ[i]);
                    vbo.putInt(colors[i]);
                }
                vbo.flip();

                mvp.set(projection).mul(view);
                putMatrix(ubo, mvp);
                
                ubo.putFloat(Math.max(0.001f, lastNoise) * 0.01f);
                ubo.putFloat(Math.max(1f, lastReflect));
                ubo.putFloat(Math.max(0f, lastBlur));
                ubo.putFloat(0f);
                ubo.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(0.85f);
                ubo.putFloat(target.width).putFloat(target.height).putFloat(0f).putFloat(0f);
                ubo.flip();

                encoder.writeToBuffer(uniformBuffer.slice(), ubo);
                encoder.writeToBuffer(vertexBuffer.slice(0, vbo.remaining()), vbo);

                try (RenderPass pass = encoder.createRenderPass(
                        () -> "GlassTorus3D",
                        target.getColorTextureView(),
                        OptionalInt.empty(),
                        target.getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
                    pass.setPipeline(pipeline);
                    pass.setUniform("Uniforms", uniformBuffer);
                    pass.bindTexture("SceneSampler", sceneCopyView, RenderSampler.linear());
                    pass.setVertexBuffer(0, vertexBuffer);
                    pass.draw(0, vertexCount);
                }
                errorCount = 0;
            } finally {
                MemoryUtil.memFree(vbo);
                MemoryUtil.memFree(ubo);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            errorCount++;
            
            
            if (errorCount >= 5) {
                disabled = true;
                closeSceneCopy();
            }
        } finally {
            vertexCount = 0;
        }
    }

    private static void put(float x, float y, float z, int color) {
        if (vertexCount >= MAX_VERTICES) return;
        tmp.set(x, y, z);
        model.transformPosition(tmp);
        worldX[vertexCount] = tmp.x;
        worldY[vertexCount] = tmp.y;
        worldZ[vertexCount] = tmp.z;
        colors[vertexCount] = color;
        vertexCount++;
    }

    private static void init() {
        try {
            VertexFormat format = VertexFormat.builder()
                    .add("inPosition", VertexFormatElement.POSITION)
                    .add("inColor", VertexFormatElement.COLOR)
                    .build();

            pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("cataclysm", "pipeline/world/glass_torus"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("cataclysm", "world/glass_torus/glass_torus"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("cataclysm", "world/glass_torus/glass_torus"))
                    .withVertexFormat(format, VertexFormat.Mode.TRIANGLES)
                    .withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            if (uniformBuffer == null) {
                uniformBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "GlassTorus Uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        UNIFORM_SIZE
                );
            }
            if (vertexBuffer == null) {
                vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "GlassTorus Vertices",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        MAX_VERTICES * VERTEX_SIZE
                );
            }
            initedOnce = true;
        } catch (Throwable t) {
            t.printStackTrace();
            pipeline = null;
            disabled = true;
        }
    }

    private static void closeSceneCopy() {
        if (sceneCopyView != null) { sceneCopyView.close(); sceneCopyView = null; }
        if (sceneCopy != null) { sceneCopy.close(); sceneCopy = null; }
        copyW = -1;
        copyH = -1;
    }

    private static void ensureSceneCopy(int w, int h) {
        if (sceneCopy != null && copyW == w && copyH == h) return;
        
        
        closeSceneCopy();
        sceneCopy = RenderSystem.getDevice().createTexture(
                () -> "GlassTorus SceneCopy",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h, 1, 1
        );
        sceneCopyView = RenderSystem.getDevice().createTextureView(sceneCopy);
        copyW = w;
        copyH = h;
    }

    private static void putMatrix(ByteBuffer b, Matrix4f m) {
        b.putFloat(m.m00()).putFloat(m.m01()).putFloat(m.m02()).putFloat(m.m03());
        b.putFloat(m.m10()).putFloat(m.m11()).putFloat(m.m12()).putFloat(m.m13());
        b.putFloat(m.m20()).putFloat(m.m21()).putFloat(m.m22()).putFloat(m.m23());
        b.putFloat(m.m30()).putFloat(m.m31()).putFloat(m.m32()).putFloat(m.m33());
    }
}
