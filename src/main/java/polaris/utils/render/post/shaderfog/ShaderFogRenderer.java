package polaris.utils.render.post.shaderfog;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;
import polaris.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;


public final class ShaderFogRenderer {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/post/shaderfog_composite");
    private static final Identifier VS = Identifier.fromNamespaceAndPath("cataclysm", "post/shaderfog/fullscreen");
    private static final Identifier FS = Identifier.fromNamespaceAndPath("cataclysm", "post/shaderfog/composite");
    
    private static final int UNIFORM_BYTES = 112;

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dataBuffer;
    private static GpuTexture sceneCopy;
    private static GpuTextureView sceneView;
    private static int lastW = -1, lastH = -1;
    private static boolean disabledAfterError;
    private static long lastNanos = -1L;
    private static float animTime;
    
    
    private static float realTime;

    private ShaderFogRenderer() {
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    public static void clear() {
        lastNanos = -1L;
        animTime = 0f;
        realTime = 0f;
    }

    public static void apply(RenderTarget target,
                             int mode,
                             float intensity,
                             float timeSpeed,
                             int firstColor,
                             int secondColor,
                             int purpleColor,
                             float lightningPower,
                             float strikePeriod,
                             float worldFlash) {
        if (disabledAfterError || target == null || target.getColorTexture() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) {
            return;
        }
        if (!ensureReady(target.width, target.height)) {
            return;
        }

        try {
            tickTime(timeSpeed);
            Camera camera = mc.gameRenderer.getMainCamera();
            float fov = ((Integer) mc.options.fov().get()).floatValue();
            float tanHalf = (float) Math.tan(Math.toRadians(fov) * 0.5);
            float aspect = (float) target.width / Math.max(1f, target.height);
            float pitch = (float) Math.toRadians(camera.xRot());
            float yaw = (float) (Math.PI - Math.toRadians(camera.yRot()));
            Vec3 cam = camera.position();

            writeUniforms(mode, intensity, pitch, yaw, tanHalf, aspect, cam, firstColor, secondColor, purpleColor,
                    lightningPower, strikePeriod, worldFlash);

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
            encoder.copyTextureToTexture(target.getColorTexture(), sceneCopy, 0, 0, 0, 0, 0, target.width, target.height);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "cataclysm:shaderfog",
                    target.getColorTextureView(),
                    OptionalInt.empty(),
                    null,
                    OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("InSampler", sceneView, RenderSampler.linear());
                
                pass.bindTexture("DepthSampler", target.getDepthTextureView(), RenderSampler.nearest());
                pass.setUniform("ShaderFogData", uniformBuffer.slice());
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
        realTime = (realTime + dt) % 10000f;
    }

    private static void writeUniforms(int mode, float intensity, float pitch, float yaw,
                                      float tanHalf, float aspect, Vec3 cam,
                                      int first, int second, int purple,
                                      float lightningPower, float strikePeriod, float worldFlash) {
        dataBuffer.clear();
        putColor(first);
        putColor(second);
        putColor(purple);
        dataBuffer.putFloat((float) cam.x);
        dataBuffer.putFloat((float) cam.y);
        dataBuffer.putFloat((float) cam.z);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(intensity);
        dataBuffer.putFloat(animTime);
        dataBuffer.putFloat(pitch);
        dataBuffer.putFloat(yaw);
        dataBuffer.putFloat(tanHalf);
        dataBuffer.putFloat(aspect);
        dataBuffer.putFloat(mode);
        dataBuffer.putFloat(0f);
        dataBuffer.putFloat(Math.max(0f, worldFlash));
        dataBuffer.putFloat(Math.max(0.5f, strikePeriod));
        dataBuffer.putFloat(Math.max(0f, lightningPower));
        dataBuffer.putFloat(realTime);
        dataBuffer.flip();
    }

    private static void putColor(int argb) {
        dataBuffer.putFloat(((argb >> 16) & 0xFF) / 255f);
        dataBuffer.putFloat(((argb >> 8) & 0xFF) / 255f);
        dataBuffer.putFloat((argb & 0xFF) / 255f);
        dataBuffer.putFloat(((argb >> 24) & 0xFF) / 255f);
    }

    private static boolean ensureReady(int w, int h) {
        if (pipeline == null || uniformBuffer == null || dummyVertexBuffer == null || dataBuffer == null) {
            init();
        }
        ensureTextures(w, h);
        return pipeline != null && uniformBuffer != null && dummyVertexBuffer != null
                && dataBuffer != null && sceneCopy != null && sceneView != null;
    }

    private static void init() {
        try {
            pipeline = RenderPipeline.builder()
                    .withLocation(PIPELINE_ID)
                    .withVertexShader(VS)
                    .withFragmentShader(FS)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("ShaderFogData", UniformType.UNIFORM_BUFFER)
                    .withSampler("InSampler")
                    .withSampler("DepthSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            dataBuffer = MemoryUtil.memAlloc(UNIFORM_BYTES);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderfog_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm:shaderfog_dummy_vb",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyData
            );
            MemoryUtil.memFree(dummyData);
        } catch (Throwable t) {
            disabledAfterError = true;
            t.printStackTrace();
        }
    }

    private static void ensureTextures(int w, int h) {
        if (sceneCopy != null && lastW == w && lastH == h) return;
        if (sceneView != null) sceneView.close();
        if (sceneCopy != null) sceneCopy.close();
        sceneView = null;
        sceneCopy = null;
        try {
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
            sceneCopy = RenderSystem.getDevice().createTexture(
                    () -> "cataclysm:shaderfog_scene", usage, TextureFormat.RGBA8, w, h, 1, 1);
            sceneView = RenderSystem.getDevice().createTextureView(sceneCopy);
            lastW = w;
            lastH = h;
        } catch (Throwable t) {
            disabledAfterError = true;
            t.printStackTrace();
        }
    }
}
