package polaris.utils.modules.warden;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;


public final class SpecialItemUtils {
    private SpecialItemUtils() {
    }

    private static final List<MobEffectInstance> ITEMS = List.of(
            new MobEffectInstance(MobEffects.SLOWNESS, 200, 9),
            new MobEffectInstance(MobEffects.SPEED, 400, 4),
            new MobEffectInstance(MobEffects.BLINDNESS, 100, 9),
            new MobEffectInstance(MobEffects.GLOWING, 3600, 0)
    );
    private static final List<MobEffectInstance> ITEMS_2 = List.of(
            new MobEffectInstance(MobEffects.STRENGTH, 600, 4),
            new MobEffectInstance(MobEffects.SLOWNESS, 600, 3)
    );
    private static final List<MobEffectInstance> ITEMS_3 = List.of(
            new MobEffectInstance(MobEffects.RESISTANCE, 12000, 0),
            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 12000, 0),
            new MobEffectInstance(MobEffects.HEALTH_BOOST, 1200, 2),
            new MobEffectInstance(MobEffects.INVISIBILITY, 18000, 0)
    );
    private static final List<MobEffectInstance> ITEMS_4 = List.of(
            new MobEffectInstance(MobEffects.REGENERATION, 900, 1),
            new MobEffectInstance(MobEffects.INVISIBILITY, 12000, 1),
            new MobEffectInstance(MobEffects.INSTANT_HEALTH, 0, 1)
    );
    private static final List<MobEffectInstance> ITEMS_5 = List.of(
            new MobEffectInstance(MobEffects.STRENGTH, 1200, 3),
            new MobEffectInstance(MobEffects.SPEED, 6000, 2),
            new MobEffectInstance(MobEffects.HASTE, 1200, 0),
            new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 0, 1)
    );
    private static final List<MobEffectInstance> ITEMS_6 = List.of(
            new MobEffectInstance(MobEffects.POISON, 1200, 1),
            new MobEffectInstance(MobEffects.WITHER, 1200, 1),
            new MobEffectInstance(MobEffects.SLOWNESS, 1800, 2),
            new MobEffectInstance(MobEffects.HUNGER, 1200, 4),
            new MobEffectInstance(MobEffects.GLOWING, 2400, 0)
    );
    private static final List<MobEffectInstance> ITEMS_7 = List.of(
            new MobEffectInstance(MobEffects.WEAKNESS, 1800, 1),
            new MobEffectInstance(MobEffects.MINING_FATIGUE, 200, 1),
            new MobEffectInstance(MobEffects.WITHER, 1800, 2),
            new MobEffectInstance(MobEffects.BLINDNESS, 200, 0)
    );

    private static Map<Holder<Attribute>, Double> resolve(ItemStack itemStack) {
        ItemAttributeModifiers modifiers = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        HashMap<Holder<Attribute>, Double> map = new HashMap<>();
        if (modifiers == null) {
            return map;
        }
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            map.put(entry.attribute(), modifier.amount());
        }
        return map;
    }

    private static boolean check(Map<Holder<Attribute>, Double> map, Holder<Attribute> attribute, double d) {
        return Math.abs(map.getOrDefault(attribute, 0.0) - d) < 1.0E-4;
    }

    private static boolean check2(ItemStack itemStack, String textureValue) {
        if (!itemStack.is(Items.PLAYER_HEAD)) {
            return false;
        }
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        try {
            CompoundTag root = customData.copyTag();
            
            return root.getCompound("SkullOwner")
                    .flatMap(skull -> skull.getCompound("Properties"))
                    .flatMap(props -> {
                        if (!props.contains("textures")) {
                            return Optional.empty();
                        }
                        ListTag textures = props.getListOrEmpty("textures");
                        if (textures.isEmpty()) {
                            return Optional.empty();
                        }
                        return textures.getCompound(0);
                    })
                    .flatMap(tex -> tex.getString("Value"))
                    .map(textureValue::equals)
                    .orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean check3(ItemStack itemStack, List<MobEffectInstance> list) {
        PotionContents potionContents = itemStack.get(DataComponents.POTION_CONTENTS);
        if (potionContents == null) {
            return false;
        }
        List<MobEffectInstance> customEffects = potionContents.customEffects();
        for (MobEffectInstance wanted : list) {
            boolean found = false;
            for (MobEffectInstance actual : customEffects) {
                if (actual.getEffect().equals(wanted.getEffect()) && actual.getAmplifier() == wanted.getAmplifier()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean check4(ItemStack itemStack, String string) {
        return itemStack.getHoverName().getString().contains(string);
    }

    private static boolean check5(ItemStack itemStack, String string) {
        return itemStack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(string.toLowerCase(Locale.ROOT));
    }

    private static boolean check6(ItemStack itemStack, String string) {
        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component text : lore.lines()) {
            if (text.getString().contains(string)) {
                return true;
            }
        }
        return false;
    }

    private static boolean check7(ItemStack itemStack, String string) {
        String needle = string.toLowerCase(Locale.ROOT);
        if (itemStack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        ItemLore lore = itemStack.get(DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        for (Component text : lore.lines()) {
            if (text.getString().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    

    public static boolean check8(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.MAX_HEALTH, -4.0)
                && check(m, Attributes.ARMOR, 1.5)
                && check(m, Attributes.ATTACK_DAMAGE, 2.5)
                && check(m, Attributes.MOVEMENT_SPEED, 0.07)
                && check(m, Attributes.ATTACK_SPEED, 0.13)
                && check(m, Attributes.GRAVITY, 0.09);
    }

    public static boolean check9(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ARMOR, 2.5)
                && check(m, Attributes.ARMOR_TOUGHNESS, 2.5)
                && check(m, Attributes.MOVEMENT_SPEED, -0.15);
    }

    public static boolean check10(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 6.0)
                && check(m, Attributes.ARMOR, -2.0)
                && check(m, Attributes.MAX_HEALTH, -2.0);
    }

    public static boolean check11(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ARMOR, 1.0)
                && check(m, Attributes.MAX_HEALTH, 4.0)
                && check(m, Attributes.MOVEMENT_SPEED, 0.1)
                && check(m, Attributes.ATTACK_SPEED, 0.1);
    }

    public static boolean check12(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.MAX_HEALTH, 4.0)
                && check(m, Attributes.ARMOR, 2.0)
                && check(m, Attributes.SUBMERGED_MINING_SPEED, 0.5)
                && check(m, Attributes.OXYGEN_BONUS, 0.5);
    }

    public static boolean check13(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 2.0) && check(m, Attributes.MAX_HEALTH, 2.0);
    }

    public static boolean check14(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.LUCK, 1.0)
                && check(m, Attributes.MAX_HEALTH, 2.0)
                && check(m, Attributes.BLOCK_INTERACTION_RANGE, 1.0);
    }

    public static boolean check15(ItemStack itemStack) {
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 2.0)
                && check(m, Attributes.JUMP_STRENGTH, -0.1)
                && check(m, Attributes.ATTACK_SPEED, 0.15);
    }

    public static boolean check16(ItemStack itemStack) {
        return itemStack.is(Items.PLAYER_HEAD) && check4(itemStack, "Сфера Мороза") && check6(itemStack, "Вечная мерзлота");
    }

    

    public static boolean check17(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 2.5) && check(m, Attributes.ATTACK_SPEED, 0.1);
    }

    public static boolean check18(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 7.0)
                && check(m, Attributes.MAX_HEALTH, -4.0)
                && check(m, Attributes.MOVEMENT_SPEED, 0.1);
    }

    public static boolean check19(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ARMOR, 1.5) && check(m, Attributes.MAX_HEALTH, 1.5);
    }

    public static boolean check20(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 5.0) && check(m, Attributes.MAX_HEALTH, -4.0);
    }

    public static boolean check21(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 2.0)
                && check(m, Attributes.ARMOR, 2.0)
                && check(m, Attributes.MAX_HEALTH, -4.0);
    }

    public static boolean check22(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.MAX_HEALTH, 4.0)
                && check(m, Attributes.ATTACK_DAMAGE, 3.0)
                && check(m, Attributes.ARMOR_TOUGHNESS, 2.0)
                && check(m, Attributes.ARMOR, 2.0);
    }

    public static boolean check23(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 4.0)
                && check(m, Attributes.MAX_HEALTH, 2.0)
                && check(m, Attributes.MOVEMENT_SPEED, 0.1)
                && check(m, Attributes.ATTACK_SPEED, 0.1)
                && check(m, Attributes.ARMOR, -3.0);
    }

    public static boolean check24(ItemStack itemStack) {
        if (!itemStack.is(Items.TOTEM_OF_UNDYING)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.MAX_HEALTH, 2.0);
    }

    public static String resolve2(ItemStack itemStack) {
        if (check17(itemStack)) return "Талисман Демона";
        if (check18(itemStack)) return "Талисман Карателя";
        if (check19(itemStack)) return "Талисман Мрака";
        if (check20(itemStack)) return "Талисман Ярости";
        if (check21(itemStack)) return "Талисман Тирана";
        if (check22(itemStack)) return "Талисман Крушителя";
        if (check23(itemStack)) return "Талисман Раздора";
        return check24(itemStack) ? "Талисман Сары" : "";
    }

    

    public static boolean check25(ItemStack itemStack) {
        if (!itemStack.is(Items.SPLASH_POTION)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        boolean attrs = check(m, Attributes.ATTACK_DAMAGE, 12.0)
                && check(m, Attributes.MOVEMENT_SPEED, 0.6)
                && check(m, Attributes.ATTACK_SPEED, 0.1);
        return attrs || check3(itemStack, ITEMS_5);
    }

    public static boolean check26(ItemStack itemStack) {
        if (!itemStack.is(Items.SPLASH_POTION)) return false;
        Map<Holder<Attribute>, Double> m = resolve(itemStack);
        return check(m, Attributes.ATTACK_DAMAGE, 5.0) && check3(itemStack, ITEMS_2);
    }

    public static boolean check27(ItemStack itemStack) {
        return itemStack.is(Items.SPLASH_POTION) && check3(itemStack, ITEMS);
    }

    public static boolean check28(ItemStack itemStack) {
        return itemStack.is(Items.SPLASH_POTION) && (check3(itemStack, ITEMS_4) || check4(itemStack, "Святая вода"));
    }

    public static boolean check29(ItemStack itemStack) {
        return itemStack.is(Items.SPLASH_POTION) && check3(itemStack, ITEMS_3);
    }

    public static boolean check30(ItemStack itemStack) {
        return itemStack.is(Items.SPLASH_POTION) && check3(itemStack, ITEMS_6);
    }

    public static boolean check31(ItemStack itemStack) {
        return itemStack.is(Items.SPLASH_POTION) && check3(itemStack, ITEMS_7);
    }

    

    public static boolean check32(ItemStack itemStack) {
        return itemStack.is(Items.SUGAR) && check4(itemStack, "Явная пыль") && check6(itemStack, "Каст: Световая вспышка");
    }

    public static boolean check33(ItemStack itemStack) {
        return itemStack.is(Items.ENDER_EYE) && check4(itemStack, "Дезориентация") && check6(itemStack, "Чем ближе цель");
    }

    public static boolean check34(ItemStack itemStack) {
        return itemStack.is(Items.NETHERITE_SCRAP) && check4(itemStack, "Трапка") && check6(itemStack, "Каст: Нерушимая клетка");
    }

    public static boolean check35(ItemStack itemStack) {
        return itemStack.is(Items.TRIPWIRE_HOOK) && check4(itemStack, "Отмычка к Сферам") && check6(itemStack, "Открыть хранилище с Сферам");
    }

    public static boolean check36(ItemStack itemStack) {
        return itemStack.is(Items.DRIED_KELP) && check4(itemStack, "Пласт") && check6(itemStack, "Каст: Нерушимая стена");
    }

    public static boolean check37(ItemStack itemStack) {
        return itemStack.is(Items.EXPERIENCE_BOTTLE) && (check7(itemStack, "Опыт с уровнем 15") || check7(itemStack, "15 ур"));
    }

    public static boolean check38(ItemStack itemStack) {
        return itemStack.is(Items.EXPERIENCE_BOTTLE) && (check7(itemStack, "Опыт с уровнем 30") || check7(itemStack, "30 ур"));
    }

    public static boolean check39(ItemStack itemStack) {
        return itemStack.is(Items.EXPERIENCE_BOTTLE) && (check7(itemStack, "Опыт с уровнем 50") || check7(itemStack, "50 ур"));
    }

    public static boolean check40(ItemStack itemStack) {
        return itemStack.is(Items.EXPERIENCE_BOTTLE) && (check7(itemStack, "Опыт с уровнем 45") || check7(itemStack, "45 ур"));
    }

    public static boolean check41(ItemStack itemStack) {
        return itemStack.is(Items.TNT) && check4(itemStack, "WHITE") && check6(itemStack, "в 10 раз сильнее");
    }

    public static boolean check42(ItemStack itemStack) {
        return itemStack.is(Items.TNT) && check4(itemStack, "BLACK") && check6(itemStack, "взорвать обсидиан");
    }

    public static boolean check43(ItemStack itemStack) {
        return itemStack.is(Items.CAMPFIRE) && check4(itemStack, "Случайный") && check6(itemStack, "Уровень лута: Случайный");
    }

    public static boolean check44(ItemStack itemStack) {
        return itemStack.is(Items.CAMPFIRE) && check4(itemStack, "Обычный") && check6(itemStack, "Уровень лута: Обычный");
    }

    public static boolean check45(ItemStack itemStack) {
        return itemStack.is(Items.CAMPFIRE) && check4(itemStack, "Богатый") && check6(itemStack, "Уровень лута: Богатый");
    }

    public static boolean check46(ItemStack itemStack) {
        return itemStack.is(Items.SOUL_CAMPFIRE) && check4(itemStack, "Легендарный") && check6(itemStack, "Уровень лута: Легендарный");
    }

    public static boolean check47(ItemStack itemStack) {
        return itemStack.is(Items.JIGSAW) && check4(itemStack, "Блок дамагер") && check6(itemStack, "Каст: Нанесение урона");
    }

    public static boolean check48(ItemStack itemStack) {
        return itemStack.is(Items.STRUCTURE_BLOCK) && check4(itemStack, "1x1") && check6(itemStack, "(1x1)");
    }

    public static boolean check49(ItemStack itemStack) {
        return itemStack.is(Items.BEACON) && check4(itemStack, "Маяк") && check6(itemStack, "раздающий Монеты");
    }

    public static boolean check50(ItemStack itemStack) {
        return itemStack.is(Items.SOUL_LANTERN) && check4(itemStack, "Проклятая душа") && check6(itemStack, "Обменяй души");
    }

    public static boolean check51(ItemStack itemStack) {
        return itemStack.is(Items.PAPER) && check4(itemStack, "Драконий скин") && check6(itemStack, "Драконий скин взамен");
    }

    public static boolean check52(ItemStack itemStack) {
        return itemStack.is(Items.FIRE_CHARGE) && check4(itemStack, "Огненный смерч") && check6(itemStack, "Каст: Огненная волна");
    }

    public static boolean check53(ItemStack itemStack) {
        return itemStack.is(Items.SNOWBALL) && check4(itemStack, "Снежок заморозка") && check6(itemStack, "Каст: Ледяная сфера");
    }

    public static boolean check54(ItemStack itemStack) {
        return itemStack.is(Items.PHANTOM_MEMBRANE) && check4(itemStack, "Божья аура") && check6(itemStack, "Каст: Божественная аура");
    }

    public static boolean check55(ItemStack itemStack) {
        return itemStack.is(Items.IRON_NUGGET) && check4(itemStack, "Серебро");
    }

    public static boolean check56(ItemStack itemStack) {
        return itemStack.is(Items.GOLDEN_PICKAXE) && check5(itemStack, "Божье касание") && check6(itemStack, "Может добыть спавнер");
    }

    public static boolean check57(ItemStack itemStack) {
        return itemStack.is(Items.GOLDEN_PICKAXE) && check4(itemStack, "Мощный удар") && check6(itemStack, "Может разрушить бедрок");
    }

    public static boolean check58(ItemStack itemStack) {
        return itemStack.is(Items.NETHERITE_PICKAXE) && check4(itemStack, "мега-бульдозер") && check6(itemStack, "Вскапывает территорию");
    }

    public static boolean check59(ItemStack itemStack) {
        return itemStack.is(Items.ELYTRA) && check4(itemStack, "Нерушимые элитры") && check6(itemStack, "Нерушимый предмет");
    }

    
    @SuppressWarnings("unused")
    private static boolean textureMatch(ItemStack stack, String v) {
        return check2(stack, v);
    }
}
