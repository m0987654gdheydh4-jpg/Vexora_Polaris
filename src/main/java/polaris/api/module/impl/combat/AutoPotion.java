package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;

public final class AutoPotion extends Module {

    private final BooleanSetting strength = register(new BooleanSetting("Strength", "Throw strength potions.", true));
    private final BooleanSetting speed = register(new BooleanSetting("Speed", "Throw swiftness potions.", true));
    private final BooleanSetting fireResistance = register(new BooleanSetting("Fire Resistance", "Throw fire resistance potions.", true));
    private final BooleanSetting regeneration = register(new BooleanSetting("Regeneration", "Throw regeneration potions.", true));
    private final BooleanSetting healing = register(new BooleanSetting("Healing", "Throw healing potions when low HP.", true));

    private final NumberSetting healHp = register(new NumberSetting("Heal HP", "Throw healing potion at or below this health.", 10.0, 1.0, 20.0, 1.0));
    private final NumberSetting delay = register(new NumberSetting("Delay", "Extra ticks between throws.", 2.0, 0.0, 20.0, 1.0));

    private static final String[] STRENGTH_POTIONS = {"strength", "long_strength", "strong_strength"};
    private static final String[] SPEED_POTIONS = {"swiftness", "long_swiftness", "strong_swiftness"};
    private static final String[] FIRE_POTIONS = {"fire_resistance", "long_fire_resistance"};
    private static final String[] REGEN_POTIONS = {"regeneration", "long_regeneration", "strong_regeneration"};
    private static final String[] HEAL_POTIONS = {"healing", "strong_healing"};

    private int delayTicks;
    private int lastThrowTick = -1;

    public AutoPotion() {
        super("AutoPotion", "Silently throws potions from hotbar and inventory.", ModuleCategory.COMBAT);
        healHp.visibleWhen(healing::getValue);
    }

    @Override
    protected void onEnable() {
        delayTicks = 0;
        lastThrowTick = -1;
    }

    @SubscribeEvent
    private void onTick(TickEvent event) {
        Minecraft client = event.getClient();
        if (client == null || client.player == null || client.gameMode == null || client.getConnection() == null) {
            return;
        }
        if (client.screen != null) {
            return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Гард: не больше одного броска за тик (событие может firing дважды)
        if (lastThrowTick == client.player.tickCount) {
            return;
        }

        int slot = findPotionToThrow(client.player);
        if (slot == -1) {
            return;
        }

        throwPotion(client, slot);

        lastThrowTick = client.player.tickCount;
        // Строго 1 зелье: минимум 20 тиков (1 сек) между бросками поверх Delay
        delayTicks = Math.max(delay.getValue().intValue(), 20);
    }

    private int findPotionToThrow(Player player) {
        if (healing.getValue() && player.getHealth() <= healHp.getValue().floatValue()) {
            int slot = findPotionSlot(HEAL_POTIONS);
            if (slot != -1) {
                return slot;
            }
        }
        if (strength.getValue() && !hasFreshEffect(player, MobEffects.STRENGTH)) {
            int slot = findPotionSlot(STRENGTH_POTIONS);
            if (slot != -1) {
                return slot;
            }
        }
        if (speed.getValue() && !hasFreshEffect(player, MobEffects.SPEED)) {
            int slot = findPotionSlot(SPEED_POTIONS);
            if (slot != -1) {
                return slot;
            }
        }
        if (fireResistance.getValue() && !hasFreshEffect(player, MobEffects.FIRE_RESISTANCE)) {
            int slot = findPotionSlot(FIRE_POTIONS);
            if (slot != -1) {
                return slot;
            }
        }
        if (regeneration.getValue() && !hasFreshEffect(player, MobEffects.REGENERATION)) {
            int slot = findPotionSlot(REGEN_POTIONS);
            if (slot != -1) {
                return slot;
            }
        }
        return -1;
    }

    private boolean hasFreshEffect(Player player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance != null && instance.getDuration() > 60;
    }

    private int findPotionSlot(String... paths) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return -1;
        }
        for (int i = 0; i < 36; i++) {
            if (isPotionOfType(client.player.getInventory().getItem(i), paths)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isPotionOfType(net.minecraft.world.item.ItemStack stack, String... paths) {
        if (stack == null || !stack.is(Items.SPLASH_POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) {
            return false;
        }
        String key = BuiltInRegistries.POTION.getKey(contents.potion().get().value()).getPath();
        for (String path : paths) {
            if (path.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void throwPotion(Minecraft client, int slot) {
        Player player = client.player;
        var conn = client.getConnection();
        if (conn == null) {
            return;
        }

        float yawBefore = player.getYRot();
        float pitchBefore = player.getXRot();

        int original = player.getInventory().getSelectedSlot();
        int useSlot = slot;
        boolean inventorySwapped = false;

        if (useSlot > 8) {
            client.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId, useSlot, original, ClickType.SWAP, player);
            useSlot = original;
            inventorySwapped = true;
        }

        conn.send(new ServerboundSetCarriedItemPacket(useSlot));
        conn.send(new ServerboundMovePlayerPacket.Rot(yawBefore, 90.0F, player.onGround(), player.horizontalCollision));
        conn.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        conn.send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yawBefore, 90.0F));

        conn.send(new ServerboundSetCarriedItemPacket(original));
        conn.send(new ServerboundMovePlayerPacket.Rot(yawBefore, pitchBefore, player.onGround(), player.horizontalCollision));

        if (inventorySwapped) {
            client.gameMode.handleInventoryMouseClick(
                    player.containerMenu.containerId, slot, original, ClickType.SWAP, player);
        }
    }
}