package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;

// Твои импорты системы событий Polaris API
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.RotationUpdateEvent;

// Твой точный импорт аксессора
import polaris.mixin.accessor.MinecraftAccessor;

public class NoBlockDelay extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    public NoBlockDelay() {
        super("No Block Delay", "Removes delay only when placing blocks.", ModuleCategory.MISC);
    }

    @SubscribeEvent
    private void onUpdate(RotationUpdateEvent event) {
        if (mc.player == null) return;

        // Проверяем, держит ли игрок блок в основной или левой руке
        boolean holdingBlock = mc.player.getMainHandItem().getItem() instanceof BlockItem
                || mc.player.getOffhandItem().getItem() instanceof BlockItem;

        // Обнуляем задержку правого клика через твой аксессор только если в руках блок
        if (holdingBlock) {
            ((MinecraftAccessor) mc).cataclysm$setRightClickDelay(0);
        }
    }
}
