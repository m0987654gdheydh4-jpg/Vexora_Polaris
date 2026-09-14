package polaris.utils.modules.auction;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;
import polaris.utils.modules.autobuy.AuctionUtils;

import java.util.Locale;


public final class AuctionFilterUtil {
    private AuctionFilterUtil() {
    }

    public static boolean hasEnchantments(ItemStack stack) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        return enchants != null && !enchants.isEmpty();
    }

    public static CompoundTag getCustomData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : new CompoundTag();
    }

    public static int getCustomEnchantmentLevel(ItemStack stack, String type) {
        ListTag list = getCustomData(stack).getListOrEmpty("custom-enchantments");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag compound = list.getCompoundOrEmpty(i);
            String enchantType = compound.getStringOr("type", "");
            if (enchantType.equals(type)) {
                return compound.getIntOr("level", 0);
            }
        }
        return -1;
    }

    
    public static int getEnchantmentLevel(ItemStack stack, String enchantId) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null || enchants.isEmpty()) {
            return -1;
        }
        String needle = enchantId.toLowerCase(Locale.ROOT);
        if (needle.contains(":")) {
            needle = needle.substring(needle.indexOf(':') + 1);
        }
        for (Holder<Enchantment> entry : enchants.keySet()) {
            String full = entry.unwrapKey()
                    .map(Object::toString)
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            if (full.contains(needle)) {
                int level = enchants.getLevel(entry);
                return level > 0 ? level : -1;
            }
        }
        return -1;
    }

    public static boolean isArmor(ItemStack stack) {
        return AuctionUtils.isArmorItem(stack);
    }

    public static boolean isElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA);
    }

    public static boolean isSword(ItemStack stack) {
        return stack.is(ItemTags.SWORDS);
    }

    public static boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES);
    }

    public static boolean isAxe(ItemStack stack) {
        return stack.is(ItemTags.AXES);
    }

    public static boolean isShovel(ItemStack stack) {
        return stack.is(ItemTags.SHOVELS);
    }

    public static boolean isHoe(ItemStack stack) {
        return stack.is(ItemTags.HOES);
    }

    public static boolean isTrident(ItemStack stack) {
        return stack.is(Items.TRIDENT);
    }

    public static boolean isBow(ItemStack stack) {
        return stack.is(Items.BOW);
    }

    public static boolean isCrossbow(ItemStack stack) {
        return stack.is(Items.CROSSBOW);
    }

    public static boolean isArrow(ItemStack stack) {
        return stack.is(ItemTags.ARROWS);
    }

    public static boolean isHorseArmor(ItemStack stack) {
        Item item = stack.getItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id != null
                && "minecraft".equals(id.getNamespace())
                && id.getPath().endsWith("_horse_armor");
    }

    public static boolean isChestArmor(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && !stack.is(Items.ELYTRA);
    }

    public static int getPrice(ItemStack stack) {
        int price = AuctionUtils.getPrice(stack);
        if (price >= 0) {
            return price;
        }
        return readPriceFromTooltipDigits(stack);
    }

    
    private static int readPriceFromTooltipDigits(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return -1;
        }
        for (var line : lore.lines()) {
            String text = line.getString();
            String lower = text.toLowerCase(Locale.ROOT)
                    .replace('e', 'е')
                    .replace('a', 'а');
            if (!lower.contains("цена") && !lower.contains("price") && !text.contains("$")) {
                continue;
            }
            StringBuilder digits = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                }
            }
            if (digits.isEmpty()) {
                continue;
            }
            try {
                long value = Long.parseLong(digits.toString());
                if (value > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return (int) value;
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
