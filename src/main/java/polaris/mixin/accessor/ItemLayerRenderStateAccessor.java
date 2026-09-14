package polaris.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {
    @Accessor("foilType")
    ItemStackRenderState.FoilType cataclysm$getFoilType();

    @Accessor("transform")
    ItemTransform cataclysm$getItemTransform();

    @Accessor("usesBlockLight")
    boolean cataclysm$getUsesBlockLight();

    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> cataclysm$getSpecialRenderer();

    @Accessor("tintLayers")
    int[] cataclysm$getTintLayers();
}

