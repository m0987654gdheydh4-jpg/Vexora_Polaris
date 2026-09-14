package polaris.utils.render.ui.shine;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import polaris.mixin.accessor.GuiGraphicsExtractorAccessor;
import polaris.utils.render.ui.Render2DCoordinateSpace;
import polaris.utils.render.ScissorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class ShineRenderer implements AutoCloseable {
    private static final int MAX_SHINES = 512;
    private static final int PARAMS_PER_SHINE = 4;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_SHINES * PARAMS_PER_SHINE * FLOATS_PER_PARAM * Float.BYTES;

    private static volatile ShineRenderer instance;

    public static final RenderPipeline SHINE_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/shine"))
            .withVertexShader(id("ui/shine/shine"))
            .withFragmentShader(id("ui/shine/shine"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("ShineParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<BuiltShine> preparedShines = new ArrayList<>(64);
    private GuiGraphics activeGraphics;
    private GpuBuffer paramsBuffer;
    private boolean paramsDirty = true;

    private ShineRenderer() {
    }

    public static ShineRenderer getInstance() {
        ShineRenderer local = instance;
        if (local == null) {
            synchronized (ShineRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new ShineRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        ShineRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void enqueue(BuiltShine shine) {
        submit(activeGraphics, shine);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedShines.clear();
        paramsDirty = false;
    }

    public boolean isShinePipeline(RenderPipeline pipeline) {
        return pipeline == SHINE_PIPELINE;
    }

    public void bindParams(RenderPass renderPass) {
        if (renderPass == null || preparedShines.isEmpty()) {
            return;
        }

        GpuBuffer buffer = ensureParamsBuffer();
        if (buffer != null) {
            renderPass.setUniform("ShineParamsArray", buffer);
        }
    }

    public void prepareBuffers() {
        if (preparedShines.isEmpty() || !paramsDirty) {
            return;
        }

        GpuBuffer buffer = ensureWritableParamsBuffer();
        if (buffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedShines);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(0, uniformData.remaining()), uniformData);
            paramsDirty = false;
        } catch (RuntimeException ignored) {
            paramsDirty = true;
        }
    }

    boolean reserve(BuiltShine shine) {
        if (preparedShines.size() == MAX_SHINES) {
            return false;
        }

        preparedShines.add(shine);
        paramsDirty = true;
        return true;
    }

    private void submit(GuiGraphics graphics, BuiltShine shine) {
        if (graphics == null || shine == null || !shine.visible()) {
            return;
        }

        try {
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ((GuiGraphicsExtractorAccessor) graphics)
                    .cataclysm$getGuiRenderState()
                    .submitGuiElement(new ShineRenderState(pose, shine, ScissorUtil.current()));
        } catch (RuntimeException ignored) {
        }
    }

    private GpuBuffer ensureParamsBuffer() {
        if (!paramsDirty && paramsBuffer != null) {
            return paramsBuffer;
        }

        prepareBuffers();
        if (!paramsDirty && paramsBuffer != null) {
            return paramsBuffer;
        }

        closeParamsBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedShines);
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "cataclysm_shine_params", GpuBuffer.USAGE_UNIFORM, uniformData);
            paramsDirty = false;
            return paramsBuffer;
        }
    }

    private GpuBuffer ensureWritableParamsBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) {
            return paramsBuffer;
        }

        closeParamsBuffer();

        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "cataclysm_shine_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            return paramsBuffer;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<BuiltShine> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_SHINE * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);

        for (int i = 0; i < batch.size(); i++) {
            BuiltShine shine = batch.get(i);
            int offset = i * PARAMS_PER_SHINE * FLOATS_PER_PARAM * Float.BYTES;

            data.putFloat(offset, shine.radiusTopLeft());
            data.putFloat(offset + 4, shine.radiusTopRight());
            data.putFloat(offset + 8, shine.radiusBottomRight());
            data.putFloat(offset + 12, shine.radiusBottomLeft());

            data.putFloat(offset + 16, shine.width());
            data.putFloat(offset + 20, shine.height());
            data.putFloat(offset + 24, shine.smoothness());
            data.putFloat(offset + 28, shine.progress());

            data.putFloat(offset + 32, shine.dirX());
            data.putFloat(offset + 36, shine.dirY());
            data.putFloat(offset + 40, shine.bandWidth());
            data.putFloat(offset + 44, shine.intensity());

            putColor(data, offset + 48, shine.color());
        }

        data.position(0);
        return data;
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        data.putFloat(offset, ((color >>> 16) & 0xFF) / 255.0f);
        data.putFloat(offset + 4, ((color >>> 8) & 0xFF) / 255.0f);
        data.putFloat(offset + 8, (color & 0xFF) / 255.0f);
        data.putFloat(offset + 12, ((color >>> 24) & 0xFF) / 255.0f);
    }

    private void closeParamsBuffer() {
        if (paramsBuffer != null) {
            paramsBuffer.close();
            paramsBuffer = null;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("cataclysm", path);
    }

    @Override
    public void close() {
        preparedShines.clear();
        activeGraphics = null;
        closeParamsBuffer();
    }
}

