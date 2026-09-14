package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.RotationUpdateEvent;

public class AutoArmor extends Module {

    private final BooleanSetting ignoreElytra = register(new BooleanSetting("Ignore Elytra", "Don't swap chestplate if wearing elytra.", true));
    private final NumberSetting delay = register(new NumberSetting("Delay (ms)", "Delay between inventory clicks.", 120.0, 50.0, 300.0, 10.0));

    private final Minecraft mc = Minecraft.getInstance();
    private long lastClickTime = System.currentTimeMillis();

    public AutoArmor() {
        super("Auto Armor", "Automatically equips best armor and drops old pieces.", ModuleCategory.COMBAT);
    }

    @SubscribeEvent
    private void onUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.gameMode == null) return;

        // Строгий чек задержки для полной безопасности от киков
        if (System.currentTimeMillis() - lastClickTime < delay.getValue().longValue()) return;

        int[] bestArmorSlots = new int[]{-1, -1, -1, -1};
        int[] bestArmorValues = new int[]{-1, -1, -1, -1};

        // 1. Считываем текущую надеgroupтую броню
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String slotName = slot.name();
            int armorTypeIndex = getArmorIndexBySlotName(slotName);
            if (armorTypeIndex == -1) continue;

            ItemStack currentArmor = mc.player.getItemBySlot(slot);

            if (ignoreElytra.getValue() && armorTypeIndex == 2 && currentArmor.is(Items.ELYTRA)) {
                bestArmorValues[armorTypeIndex] = Integer.MAX_VALUE;
                continue;
            }

            if (!currentArmor.isEmpty()) {
                bestArmorValues[armorTypeIndex] = getArmorValueByRegistry(currentArmor);
            }
        }

        // 2. Сканируем инвентарь на наличие лучшей замены
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            int armorTypeIndex = getArmorIndexByStack(stack);
            if (armorTypeIndex == -1) continue;

            if (ignoreElytra.getValue() && armorTypeIndex == 2 && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                continue;
            }

            int defense = getArmorValueByRegistry(stack);
            if (defense > bestArmorValues[armorTypeIndex]) {
                bestArmorValues[armorTypeIndex] = defense;
                bestArmorSlots[armorTypeIndex] = i;
            }
        }

        // 3. Логика безопасного надевания и выбрасывания старой брони
        for (int armorTypeIndex = 0; armorTypeIndex < 4; armorTypeIndex++) {
            int slotToEquip = bestArmorSlots[armorTypeIndex];
            if (slotToEquip == -1) continue;

            // Индексы слотов брони в окне инвентаря: 5 = HEAD, 6 = CHEST, 7 = LEGS, 8 = FEET
            int armorSlotInMenu = 8 - armorTypeIndex;
            ItemStack currentArmor = mc.player.inventoryMenu.getSlot(armorSlotInMenu).getItem();

            if (currentArmor.isEmpty()) {
                // Если слот пустой, просто быстро надеваем вещь в один клик через QUICK_MOVE
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotToEquip, 0, ClickType.QUICK_MOVE, mc.player);
            } else {
                // Если на нас ЕСТЬ старая броня, проворачиваем легитный трюк с дропом:
                // 1. Кликаем по лучшей броне в инвентаре (берём её «в руку» / в курсор)
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slotToEquip, 0, ClickType.PICKUP, mc.player);

                // 2. Кликаем по нашему слоту брони (новая надевается на нас, старая оказывается в курсоре)
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, armorSlotInMenu, 0, ClickType.PICKUP, mc.player);

                // 3. Отправляем клик по специальному слоту выброса (-999), чтобы выбросить старую броню из курсора на землю
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, -999, 0, ClickType.PICKUP, mc.player);
            }

            lastClickTime = System.currentTimeMillis();
            break; // Выполняем ровно одну операцию за тик, чтобы исключить детекты
        }
    }

    private int getArmorIndexByStack(ItemStack stack) {
        String id = stack.getItem().toString().toLowerCase();
        if (id.contains("helmet")) return 3;
        if (id.contains("chestplate")) return 2;
        if (id.contains("leggings")) return 1;
        if (id.contains("boots")) return 0;
        return -1;
    }

    private int getArmorIndexBySlotName(String slotName) {
        if (slotName.equals("HEAD")) return 3;
        if (slotName.equals("CHEST")) return 2;
        if (slotName.equals("LEGS")) return 1;
        if (slotName.equals("FEET")) return 0;
        return -1;
    }

    private int getArmorValueByRegistry(ItemStack stack) {
        String id = stack.getItem().toString().toLowerCase();
        int baseValue = 0;
        if (id.contains("netherite")) baseValue = 20;
        else if (id.contains("diamond")) baseValue = 15;
        else if (id.contains("iron")) baseValue = 10;
        else if (id.contains("chainmail")) baseValue = 7;
        else if (id.contains("gold")) baseValue = 5;
        else if (id.contains("leather")) baseValue = 2;
        return baseValue;
    }
}