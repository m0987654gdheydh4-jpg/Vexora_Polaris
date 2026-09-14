package polaris.utils.render.world.targetesp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import polaris.utils.render.WorldVertex;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

/**
 * TargetESP crystals ported from Cataclysm new 1.21.4
 * ({@code TargetESP#drawCrystals} + {@code CrystalRenderer}).
 * <p>
 * Parent PoseStack is already at target feet (smoothedPos − camera),
 * same as Cataclysm {@code RenderUtility.prepareMatrices(ms, renderPos)}.
 */
public final class CrystalTargetEspRenderer {
    private static final Identifier BLOOM = Identifier.fromNamespaceAndPath("cataclysm", "textures/bloom.png");

    
    private static final Vector3f[] VERTICES = {
            new Vector3f(0.0F, 1.5F, 0.0F),
            new Vector3f(0.0F, -1.5F, 0.0F),
            new Vector3f(1.0F, 0.0F, 0.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0F),
            new Vector3f(0.0F, 0.0F, -1.0F)
    };

    private static final int[][] FACES = {
            {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
            {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
    };

    private static final float[] FACE_BRIGHTNESS = {1.0F, 0.8F, 0.6F, 0.9F, 0.7F, 0.5F, 0.4F, 0.6F};

    
    private static final float CRYSTAL_SIZE = 0.1F;
    
    private static final float BLOOM_SIZE = 1.0F;

    private CrystalTargetEspRenderer() {
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource provider, TargetEspRenderContext context) {
        LivingEntity target = context.target();
        float anim = Mth.clamp(context.alpha(), 0.0f, 1.0f);
        if (anim <= 0.01f || target == null) {
            return;
        }

        
        
        float moving = (context.frameTimeMs() % 100_000L) * 0.2f;
        float width = target.getBbWidth() * 1.5F;
        float height = target.getBbHeight();

        
        int rgb = context.primaryColor() | 0xFF000000;
        int baseColor = ColorUtil.withAlpha(rgb, Math.round(255.0f * anim));
        int bloomColor = ColorUtil.withAlpha(rgb, Math.round(255.0f * anim * 0.2f));

        
        VertexConsumer crystal = provider.getBuffer(ClientPipelines.CRYSTAL_FILLED_TRIANGLES);

        for (int i = 0; i < 360; i += 20) {
            float val = 1.2F - 0.5F * anim;
            float angle = (float) Math.toRadians(i + moving * 0.3F);
            float sin = (float) (Math.sin(angle) * width * val);
            float cos = (float) (Math.cos(angle) * width * val);
            
            float yOff = 0.1F + height * Math.abs((float) Math.sin(i));

            stack.pushPose();
            stack.translate(sin, yOff, cos);

            
            
            Vector3f dir = new Vector3f(-sin, height * 0.5F - 1.0F, -cos);
            if (dir.lengthSquared() < 1.0e-8f) {
                dir.set(0.0F, 1.0F, 0.0F);
            } else {
                dir.normalize();
            }
            stack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir));

            renderCrystalMesh(stack, crystal, CRYSTAL_SIZE, baseColor);
            stack.popPose();
        }
        provider.endBatch(ClientPipelines.CRYSTAL_FILLED_TRIANGLES);

        
        VertexConsumer bloom = provider.getBuffer(ClientPipelines.BLOOM_ESP.apply(BLOOM));
        Quaternionf camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float half = BLOOM_SIZE * 0.5F;

        for (int i = 0; i < 360; i += 20) {
            float val = 1.2F - 0.5F * anim;
            float angle = (float) Math.toRadians(i + moving * 0.3F);
            float sin = (float) (Math.sin(angle) * width * val);
            float cos = (float) (Math.cos(angle) * width * val);
            float yOff = 0.1F + height * Math.abs((float) Math.sin(i));

            stack.pushPose();
            stack.translate(sin, yOff, cos);
            
            stack.mulPose(camRot);

            PoseStack.Pose pose = stack.last();
            WorldVertex.textured(bloom, pose, -half, -half, 0f, 0f, 0f, bloomColor);
            WorldVertex.textured(bloom, pose, half, -half, 0f, 1f, 0f, bloomColor);
            WorldVertex.textured(bloom, pose, half, half, 0f, 1f, 1f, bloomColor);
            WorldVertex.textured(bloom, pose, -half, half, 0f, 0f, 1f, bloomColor);
            stack.popPose();
        }
        provider.endBatch(ClientPipelines.BLOOM_ESP.apply(BLOOM));
    }

    public static void endBatch(MultiBufferSource.BufferSource provider) {
        
    }

    
    private static void renderCrystalMesh(PoseStack matrices, VertexConsumer buffer, float size, int color) {
        matrices.pushPose();
        matrices.scale(size, size, size);
        PoseStack.Pose pose = matrices.last();

        for (int i = 0; i < FACES.length; i++) {
            int[] face = FACES[i];
            int shaded = applyBrightness(color, FACE_BRIGHTNESS[i]);
            Vector3f v1 = VERTICES[face[0]];
            Vector3f v2 = VERTICES[face[1]];
            Vector3f v3 = VERTICES[face[2]];
            buffer.addVertex(pose, v1.x, v1.y, v1.z).setColor(shaded);
            buffer.addVertex(pose, v2.x, v2.y, v2.z).setColor(shaded);
            buffer.addVertex(pose, v3.x, v3.y, v3.z).setColor(shaded);
        }
        matrices.popPose();
    }

    private static int applyBrightness(int color, float brightness) {
        int alpha = color >> 24 & 0xFF;
        int red = Math.min(255, Math.max(0, (int) ((color >> 16 & 0xFF) * brightness)));
        int green = Math.min(255, Math.max(0, (int) ((color >> 8 & 0xFF) * brightness)));
        int blue = Math.min(255, Math.max(0, (int) ((color & 0xFF) * brightness)));
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
