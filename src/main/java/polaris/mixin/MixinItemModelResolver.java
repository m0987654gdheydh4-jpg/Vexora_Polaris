package polaris.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import polaris.api.module.impl.misc.CustomSword;


@Mixin(ItemModelResolver.class)
public abstract class MixinItemModelResolver {

    @ModifyVariable(
            method = "updateForLiving",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack cataclysm$hookUpdateForLiving(ItemStack stack) {
        CustomSword replacer = CustomSword.getInstance();
        if (replacer == null) {
            return stack;
        }
        return replacer.getRenderStack(stack);
    }

    @ModifyVariable(
            method = "updateForNonLiving",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack cataclysm$hookUpdateForNonLiving(ItemStack stack) {
        CustomSword replacer = CustomSword.getInstance();
        if (replacer == null) {
            return stack;
        }
        return replacer.getRenderStack(stack);
    }

    @ModifyVariable(
            method = "updateForTopItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack cataclysm$hookUpdateForTopItem(ItemStack stack) {
        CustomSword replacer = CustomSword.getInstance();
        if (replacer == null) {
            return stack;
        }
        return replacer.getRenderStack(stack);
    }

    @ModifyVariable(
            method = "appendItemLayers",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private ItemStack cataclysm$hookAppendItemLayers(ItemStack stack) {
        CustomSword replacer = CustomSword.getInstance();
        if (replacer == null) {
            return stack;
        }
        return replacer.getRenderStack(stack);
    }
}

