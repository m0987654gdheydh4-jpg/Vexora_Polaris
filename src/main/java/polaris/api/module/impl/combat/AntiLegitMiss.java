package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.AttackEvent; // Если не работает, попробуйте PlayerAttackEvent или HitEntityEvent
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;

public class AntiLegitMiss extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    public AntiLegitMiss() {
        super("No Legit Miss", "Cancels attacks if you are not looking at an entity.", ModuleCategory.COMBAT);
        // Строка setInfo удалена, чтобы избежать ошибок компиляции
    }

    @SubscribeEvent
    private void onAttack(AttackEvent event) {
        // Если мы смотрим не на сущность (блок, воздух и т.д.), отменяем атаку
        if (!(mc.hitResult instanceof EntityHitResult)) {
            event.cancel();
        }
    }
}