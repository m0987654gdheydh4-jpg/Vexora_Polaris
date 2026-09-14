package polaris.utils.render.ui.shine;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

public final class ShineRenderState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final BuiltShine shine;
    private final ScreenRectangle scissorArea;

    public ShineRenderState(Matrix3x2f pose, BuiltShine shine, ScreenRectangle scissorArea) {
        this.pose = pose;
        this.shine = shine;
        this.scissorArea = scissorArea;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        if (!ShineRenderer.getInstance().reserve(shine)) {
            return;
        }

        consumer.addVertexWith2DPose(pose, shine.x(), shine.y()).setUv(0.0f, 0.0f);
        consumer.addVertexWith2DPose(pose, shine.x(), shine.y() + shine.height()).setUv(0.0f, 1.0f);
        consumer.addVertexWith2DPose(pose, shine.x() + shine.width(), shine.y() + shine.height()).setUv(1.0f, 1.0f);
        consumer.addVertexWith2DPose(pose, shine.x() + shine.width(), shine.y()).setUv(1.0f, 0.0f);
    }

    @Override
    public RenderPipeline pipeline() {
        return ShineRenderer.SHINE_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Override
    public ScreenRectangle bounds() {
        return new ScreenRectangle(
                Math.round(shine.x()),
                Math.round(shine.y()),
                Math.round(shine.width()),
                Math.round(shine.height())
        ).transformMaxBounds(pose);
    }
}

