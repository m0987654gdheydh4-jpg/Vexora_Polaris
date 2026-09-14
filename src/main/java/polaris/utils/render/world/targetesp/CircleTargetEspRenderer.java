package polaris.utils.render.world.targetesp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import polaris.utils.render.WorldVertex;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.pipeline.ClientPipelines;

/**
 * "Jello" target ESP: a thick, wobbling ring of additive bloom sprites around
 * the target. Each angular point stacks several glow layers whose height waves
 * with time, producing a soft jelly-like glowing ring. Rendered in target-local
 * space (the PoseStack is already translated to the target origin by the caller).
 */
public final class CircleTargetEspRenderer {

    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("cataclysm", "textures/particles/glow.png");

    private static final int ANGLE_STEP = 2;
    private static final int LAYERS = 15;
    private static final float SPRITE_SIZE = 0.2f;

    private static float jelloMoving;

    private CircleTargetEspRenderer() {
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource provider, TargetEspRenderContext context) {
        jelloMoving += 4.0f;

        float alpha = context.alpha();
        if (alpha <= 0.0f) {
            return;
        }

        LivingEntity target = context.target();
        float entityWidth = target.getBbWidth() * 1.65f;
        float entityHeight = target.getBbHeight() - 0.15f;
        float alphaAnim = TargetEspMath.easeOutCubic(alpha);
        float scale = Math.max(0.5f, 0.7f - 0.2f * alphaAnim);

        int color = damageColor(context);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Quaternionf cameraRotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        VertexConsumer buffer = provider.getBuffer(ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_TEXTURE));

        
        for (int i = 0; i < 360; i += ANGLE_STEP) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);

            for (int j = 0; j < LAYERS; j++) {
                float yLayer = entityHeight / 1.7f
                        + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + j * 2.0f));
                int a = (int) (255 * alphaAnim * ((float) j / LAYERS) * 0.05f);
                appendSprite(stack, buffer, cameraRotation, xOffset, yLayer, zOffset, SPRITE_SIZE, r, g, b, a);
            }
        }

        
        for (int i = 0; i < 360; i += ANGLE_STEP) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);
            float yOffset = entityHeight / 1.75f
                    + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + 30.0f));
            int a = (int) (255 * alphaAnim * 0.2f);
            appendSprite(stack, buffer, cameraRotation, xOffset, yOffset, zOffset, SPRITE_SIZE, r, g, b, a);
        }
    }

    private static void appendSprite(PoseStack stack, VertexConsumer buffer, Quaternionf cameraRotation,
                                     float x, float y, float z, float size, int r, int g, int b, int alpha) {
        if (alpha <= 0) {
            return;
        }
        int color = ColorUtil.rgba(r, g, b, Mth.clamp(alpha, 0, 255));
        stack.pushPose();
        stack.translate(x, y, z);
        stack.mulPose(cameraRotation);
        PoseStack.Pose pose = stack.last();
        float h = size / 2.0f;
        WorldVertex.textured(buffer, pose, -h, -h, 0.0f, 0.0f, 0.0f, color);
        WorldVertex.textured(buffer, pose, h, -h, 0.0f, 1.0f, 0.0f, color);
        WorldVertex.textured(buffer, pose, h, h, 0.0f, 1.0f, 1.0f, color);
        WorldVertex.textured(buffer, pose, -h, h, 0.0f, 0.0f, 1.0f, color);
        stack.popPose();
    }

    private static int damageColor(TargetEspRenderContext context) {
        int base = context.primaryColor() | 0xFF000000;
        float hurt = context.hurtProgress();
        if (hurt > 0.0f) {
            float impact = TargetEspMath.easeOutCubic(Mth.clamp(hurt, 0.0f, 1.0f));
            int red = ColorUtil.rgba(255, 50, 50, 255);
            base = ColorUtil.lerpColor(base, red, impact);
        }
        return base;
    }

    public static void endBatch(MultiBufferSource.BufferSource provider) {
        provider.endBatch(ClientPipelines.WORLD_PARTICLES_GLOW.apply(GLOW_TEXTURE));
    }

    public static void reset() {
        jelloMoving = 0.0f;
    }
}

