package polaris.api.module.impl.visual.richdog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.ModeSetting;

public final class RichDog extends Module {

    private static final Identifier TEX_JACK_RUSSELL =
            Identifier.fromNamespaceAndPath("cataclysm", "textures/djekrussel.png");
    private static final Identifier TEX_DACHSHUND =
            Identifier.fromNamespaceAndPath("cataclysm", "textures/taksa.png");

    private final PetModel model = new PetModel();
    private final PetBrain brain = new PetBrain();

    private final ModeSetting skin = register(new ModeSetting(
            "Skin", "Dog breed.", "Jack Russell", "Jack Russell", "Dachshund"));

    public RichDog() {
        super("Richi Dog", "A loyal follower dog.", ModuleCategory.VISUAL);
    }

    @Override
    protected void onDisable() {
        brain.setEntity(null);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) {
            return;
        }
        brain.setEntity(client.player);
        brain.onUpdate();
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        brain.setEntity(mc.player);

        PoseStack pose = event.getStack();
        MultiBufferSource.BufferSource provider = mc.renderBuffers().bufferSource();

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vec3 render = brain.getPos().subtract(cam);

        Identifier texture = skin.is("Dachshund") ? TEX_DACHSHUND : TEX_JACK_RUSSELL;
        VertexConsumer consumer = provider.getBuffer(RenderTypes.entityCutoutNoCull(texture));

        pose.pushPose();
        pose.translate(render.x, render.y, render.z);

        model.setupAnim(mc.player.tickCount + event.getPartialTicks(), brain);
        model.render(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, brain);

        pose.popPose();
        provider.endBatch(RenderTypes.entityCutoutNoCull(texture));
    }
}

