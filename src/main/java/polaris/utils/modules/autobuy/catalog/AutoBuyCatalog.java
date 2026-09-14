package polaris.utils.modules.autobuy.catalog;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import polaris.utils.modules.autobuy.AutoBuyItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;


public final class AutoBuyCatalog {
    private static final List<AutoBuyItem> ITEMS = new ArrayList<>();
    private static final Set<String> SEEN_IDS = new HashSet<>();
    private static boolean built;

    private AutoBuyCatalog() {
    }

    public static List<AutoBuyItem> all() {
        ensureBuilt();
        return Collections.unmodifiableList(ITEMS);
    }

    
    public static List<AutoBuyItem> forFunTimeFamily() {
        ensureBuilt();
        List<AutoBuyItem> out = new ArrayList<>();
        for (AutoBuyItem item : ITEMS) {
            if (!item.getCategory().isHolyWorld()) {
                out.add(item);
            }
        }
        return out;
    }

    
    public static List<AutoBuyItem> forHolyWorld() {
        return byCategory(AutoBuyItemCategory.HOLYWORLD);
    }

    
    public static List<AutoBuyItem> forServer(String serverMode) {
        if (serverMode != null && serverMode.equalsIgnoreCase("HolyWorld")) {
            return forHolyWorld();
        }
        return forFunTimeFamily();
    }

    public static List<AutoBuyItem> byCategory(AutoBuyItemCategory category) {
        ensureBuilt();
        List<AutoBuyItem> out = new ArrayList<>();
        for (AutoBuyItem item : ITEMS) {
            if (item.getCategory() == category) {
                out.add(item);
            }
        }
        return out;
    }

    public static List<AutoBuyItem> byCategoryForServer(AutoBuyItemCategory category, String serverMode) {
        if (category == null) {
            return forServer(serverMode);
        }
        if (!category.matchesServer(serverMode)) {
            return List.of();
        }
        return byCategory(category);
    }

    private static synchronized void ensureBuilt() {
        if (built) {
            return;
        }
        built = true;
        SEEN_IDS.clear();
        ITEMS.clear();
        buildKrush();
        buildSpheresNeverbuy();
        buildTalismansNeverbuy();
        buildPotions();
        buildMisc();
        buildHolyWorld();
    }

    

    private static void buildKrush() {
        krush("Шлем Крушителя", 150_000, Items.NETHERITE_HELMET);
        krush("Нагрудник Крушителя", 250_000, Items.NETHERITE_CHESTPLATE);
        krush("Поножи Крушителя", 200_000, Items.NETHERITE_LEGGINGS);
        krush("Ботинки Крушителя", 150_000, Items.NETHERITE_BOOTS);
        krush("Меч Крушителя", 300_000, Items.NETHERITE_SWORD);
        krush("Булава Крушителя", 280_000, Items.MACE, "Булава");
        krush("Кирка Крушителя", 200_000, Items.NETHERITE_PICKAXE);
    }

    
    private static void buildSpheresNeverbuy() {
        sphere("Сфера Афины", 80_000);
        sphere("Сфера Хаоса", 80_000);
        sphere("Сфера Сатира", 80_000);
        sphere("Сфера Бестии", 80_000);
        sphere("Сфера Ареса", 80_000);
        sphere("Сфера Гидры", 80_000);
        sphere("Сфера Икара", 80_000);
        sphere("Сфера Титана", 80_000);
        sphere("Сфера Эрида", 80_000);
    }

    
    private static void buildTalismansNeverbuy() {
        talisman("Талисман Крушителя", 150_000);
        talisman("Талисман Раздора", 100_000);
        talisman("Талисман Тирана", 100_000);
        talisman("Талисман Ярости", 100_000);
        talisman("Талисман Вихря", 100_000);
        talisman("Талисман Мрака", 100_000);
        talisman("Талисман Демона", 100_000);
        talisman("Талисман Карателя", 100_000);
    }

    private static void buildPotions() {
        potion("Зелье Ассасина", 25_000, 0x333333);
        potion("Зелье Гнева", 25_000, 0x993333);
        potion("Хлопушка", 20_000, 0xFF69B4);
        potion("Святая Вода", 20_000, 0xFFFFFF, "Святая вода");
        potion("Зелье Палладина", 30_000, 0x00FFFF);
        potion("Зелье Радиации", 25_000, 0x32CD32);
        potion("Снотворное", 20_000, 0x484848);
        potion("Мандариновый сок", 15_000, 0xD6CE43);
        potion("Зелье Агента", 20_000, 0x55FF55);
        potion("Зелье Киллера", 25_000, 0xAA0000);
        potion("Зелье Медика", 20_000, 0x55FF55);
        potion("Зелье Победителя", 25_000, 0xFFAA00, "Зелье победителя");
    }

    private static void buildMisc() {
        
        item("Отмычка к сферам", AutoBuyItemCategory.MISC, 50_000, Items.TRIPWIRE_HOOK,
                "Отмычка к Сферам", "Отмычка");
        item("Чарка", AutoBuyItemCategory.MISC, 25_000, Items.ENCHANTED_GOLDEN_APPLE,
                "Зачарованное золотое яблоко");
        item("Маяк", AutoBuyItemCategory.MISC, 80_000, Items.BEACON, "Загадочный маяк");
        item("Модификатор полёта", AutoBuyItemCategory.MISC, 60_000, Items.FEATHER, "Модификатор полета");
        item("Незеритовый слиток", AutoBuyItemCategory.MISC, 3_000, Items.NETHERITE_INGOT);
        item("Шалкер", AutoBuyItemCategory.MISC, 20_000, Items.SHULKER_BOX, "Шалкеровый ящик");

        
        item("Явная Пыль", AutoBuyItemCategory.MISC, 15_000, Items.SUGAR, "Явная пыль");
        item("Дезориентация", AutoBuyItemCategory.MISC, 15_000, Items.ENDER_EYE);
        item("Трапка", AutoBuyItemCategory.MISC, 40_000, Items.NETHERITE_SCRAP);
        item("Пласт", AutoBuyItemCategory.MISC, 30_000, Items.DRIED_KELP);
        item("Опыт 15", AutoBuyItemCategory.MISC, 5_000, Items.EXPERIENCE_BOTTLE,
                "Пузырек опыта [15 ур]", "Пузырёк опыта [15 ур]");
        item("Опыт 30", AutoBuyItemCategory.MISC, 10_000, Items.EXPERIENCE_BOTTLE,
                "Пузырек опыта [30 ур]", "Пузырёк опыта [30 Ур.]");
        item("Опыт 50", AutoBuyItemCategory.MISC, 20_000, Items.EXPERIENCE_BOTTLE,
                "Пузырек опыта [50 ур]");
        item("Вайт", AutoBuyItemCategory.MISC, 25_000, Items.TNT, "TNT - TIER WHITE", "TNT WHITE");
        item("Блек", AutoBuyItemCategory.MISC, 40_000, Items.TNT, "TNT - TIER BLACK", "TNT BLACK");
        item("Тотем бессмертия", AutoBuyItemCategory.MISC, 5_000, Items.TOTEM_OF_UNDYING);
        item("Элитры", AutoBuyItemCategory.MISC, 50_000, Items.ELYTRA);
        item("Эндер жемчуг", AutoBuyItemCategory.MISC, 500, Items.ENDER_PEARL, "Эндер-жемчуг");
        item("Золотое яблоко", AutoBuyItemCategory.MISC, 1_000, Items.GOLDEN_APPLE);
        item("Спавнер", AutoBuyItemCategory.MISC, 100_000, Items.SPAWNER);
        item("Незеритовый блок", AutoBuyItemCategory.MISC, 25_000, Items.NETHERITE_BLOCK);
        item("Заряд ветра", AutoBuyItemCategory.MISC, 5_000, Items.WIND_CHARGE);
    }

    
    private static void buildHolyWorld() {
        
        hw("Шлем Infinity", 200_000, Items.NETHERITE_HELMET);
        hw("Нагрудник Infinity", 300_000, Items.NETHERITE_CHESTPLATE);
        hw("Поножи Infinity", 250_000, Items.NETHERITE_LEGGINGS);
        hw("Ботинки Infinity", 200_000, Items.NETHERITE_BOOTS);

        
        hw("Шлем Eternity", 120_000, Items.NETHERITE_HELMET);
        hw("Нагрудник Eternity", 180_000, Items.NETHERITE_CHESTPLATE);
        hw("Штаны Eternity", 150_000, Items.NETHERITE_LEGGINGS, "Поножи Eternity");
        hw("Ботинки Eternity", 120_000, Items.NETHERITE_BOOTS);

        hw("Шлем солнца", 100_000, Items.GOLDEN_HELMET);
        hw("Броневая элитра", 250_000, Items.ELYTRA);
        
        hw("Меч Eternity", 200_000, Items.NETHERITE_SWORD);
        hw("Кирка Eternity", 150_000, Items.NETHERITE_PICKAXE);
        hw("Арбалет Eternity", 100_000, Items.CROSSBOW);
        hw("Громовержец", 150_000, Items.TRIDENT);

        
        hwSphere("Сфера Цербера", 100_000, "Cerber");
        hwSphere("Сфера Флеша", 100_000, "Flash");
        hwSphere("Сфера Имморталити", 100_000, "Сфера ɪᴍᴍᴏʀᴛᴀʟɪᴛʏ", "Immortal");
        hwSphere("Сфера Арморталити", 100_000, "Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ", "Armortality");
        hwSphere("Сфера на Скорость III", 80_000, "Сфера на скорость 3", "Speed3");
        hwSphere("Сфера Eternity", 100_000, "Eternity");
        hwSphere("Сфера Stinger", 100_000, "Stinger");
        hwSphere("Сфера на броня III скорость II", 90_000, "Сфера на броня 3", "Mythical3");
        hwSphere("Сфера на урон II броня III", 90_000);
        hwSphere("Сфера на броня II урон III", 90_000);

        
        hw("Талисман Stinger", 120_000, Items.TOTEM_OF_UNDYING);
        hw("Талисман Infinity", 150_000, Items.TOTEM_OF_UNDYING);
        hw("Талисман Eternity", 130_000, Items.TOTEM_OF_UNDYING);
        hw("Легендарный талисман", 100_000, Items.TOTEM_OF_UNDYING);
        

        
        hw("Пузырек с 15 уровнем", 5_000, Items.EXPERIENCE_BOTTLE, "15");
        hw("Пузырек с 50 уровнем", 25_000, Items.EXPERIENCE_BOTTLE, "50");
        hw("Пузырек с 100 уровнем", 80_000, Items.EXPERIENCE_BOTTLE, "100");
        hw("Обычный пузырек опыта", 1_000, Items.EXPERIENCE_BOTTLE, "опыт");

        
        hw("Рюкзак I уровень", 30_000, Items.PINK_SHULKER_BOX, "рюкзак 1 уровень");
        hw("Рюкзак II уровень", 50_000, Items.LIGHT_BLUE_SHULKER_BOX, "рюкзак 2 уровень");
        hw("Рюкзак III уровень", 80_000, Items.RED_SHULKER_BOX, "рюкзак 3 уровень");
        hw("Рюкзак IV уровень", 120_000, Items.MAGENTA_SHULKER_BOX, "рюкзак 4 уровень");
        hw("Рюкзак Infinity", 200_000, Items.LIME_SHULKER_BOX, "рюкзак infinity");

        
        
        hw("Взрывная трапка", 40_000, Items.PRISMARINE_SHARD);
        hw("Стан", 35_000, Items.NETHER_STAR);
        hw("Взрывная штучка", 20_000, Items.FIRE_CHARGE);
        hw("Ком снега", 15_000, Items.SNOWBALL);
        hw("Руна «Бессмертие»", 50_000, Items.ORANGE_DYE, "Бессмертие", "Руна Бессмертие");

        
        hwPotion("Улучшенное зелье силы", 20_000, 0xFF5500);
        hwPotion("Улучшенное зелье скорости", 20_000, 0x33AAFF);
        
        hwPotion("Зелье исцеления", 15_000, 0xFF0000, "Зелье исцеление");
        hwPotion("Зелье черепашьей мощи", 18_000, 0x7FB4B8);
        hwPotion("Зелье черепашьей мощи II", 25_000, 0x7FB4B8);

        
        hw("Охотник", 40_000, Items.NETHERITE_SWORD);
        hw("Снеговик", 30_000, Items.SNOW_BLOCK);
        hw("Иллюминатор", 30_000, Items.SEA_LANTERN);
        hw("Эндермен", 35_000, Items.ENDER_PEARL);
        hw("Анти Фантом", 25_000, Items.PHANTOM_MEMBRANE);
        hw("Телекинез", 30_000, Items.HONEY_BLOCK);
        hw("Гравитация", 30_000, Items.FEATHER);
        hw("Вампиризм", 40_000, Items.WITHER_SKELETON_SKULL);
        hw("Справедливость", 35_000, Items.POTION);
        hw("Универсальный ключ", 50_000, Items.TRIPWIRE_HOOK);
        hw("Фармер", 40_000, Items.DIAMOND_SWORD);

        hw("Золотая морковь", 500, Items.GOLDEN_CARROT);
        hw("Плод хоруса", 300, Items.CHORUS_FRUIT);
        hw("Артефакт", 80_000, Items.CONDUIT);
        hw("Фейерверк", 200, Items.FIREWORK_ROCKET);
        hw("Порох", 100, Items.GUNPOWDER);
        hw("Боевой фрагмент", 15_000, Items.PRISMARINE_CRYSTALS);
        hw("Взрывчатое вещество", 20_000, Items.CLAY);
        hw("Динамит А", 25_000, Items.TNT, "Динамит A");
        hw("Динамит B", 30_000, Items.TNT, "динамит б");
        hw("Динамит B2", 35_000, Items.TNT, "динамит б2");
        hw("C4 ВзРыВчАтКа", 50_000, Items.TNT, "с4 взрывчатка", "C4");
        hw("Золотая кирка Джейка", 60_000, Items.GOLDEN_PICKAXE);
        hw("Осколок сферы", 20_000, Items.PLAYER_HEAD);
    }

    

    private static void krush(String name, int price, Item icon, String... aliases) {
        item(name, AutoBuyItemCategory.KRUSH, price, namedIcon(icon, name), aliases);
    }

    private static void sphere(String name, int price) {
        item(name, AutoBuyItemCategory.SPHERES, price, Items.PLAYER_HEAD, "[★] " + name);
    }

    private static void talisman(String name, int price, String... aliases) {
        item(name, AutoBuyItemCategory.TALISMANS, price, Items.TOTEM_OF_UNDYING, aliases);
    }

    private static void hw(String name, int price, Item icon, String... aliases) {
        item(name, AutoBuyItemCategory.HOLYWORLD, price, icon, aliases);
    }

    private static void hwSphere(String name, int price, String... aliases) {
        item(name, AutoBuyItemCategory.HOLYWORLD, price, Items.PLAYER_HEAD, aliases);
    }

    private static void hwPotion(String name, int price, int color, String... aliases) {
        Supplier<ItemStack> factory = () -> {
            ItemStack stack = new ItemStack(Items.POTION);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.AQUA));
            stack.set(DataComponents.POTION_CONTENTS,
                    new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
            return stack;
        };
        addUnique(new AutoBuyItem(name, AutoBuyItemCategory.HOLYWORLD, price, factory, aliases));
    }

    private static void potion(String name, int price, int color, String... aliases) {
        Supplier<ItemStack> factory = () -> {
            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.AQUA));
            stack.set(DataComponents.POTION_CONTENTS,
                    new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(name))));
            return stack;
        };
        addUnique(new AutoBuyItem(name, AutoBuyItemCategory.POTIONS, price, factory, aliases));
    }

    private static void item(String name, AutoBuyItemCategory cat, int price, Item icon, String... aliases) {
        addUnique(new AutoBuyItem(name, cat, price, icon, aliases));
    }

    private static void item(String name, AutoBuyItemCategory cat, int price, Supplier<ItemStack> icon, String... aliases) {
        addUnique(new AutoBuyItem(name, cat, price, icon, aliases));
    }

    private static void addUnique(AutoBuyItem item) {
        if (item == null || item.getId().isEmpty()) {
            return;
        }
        if (!SEEN_IDS.add(item.getId())) {
            return;
        }
        ITEMS.add(item);
    }

    private static Supplier<ItemStack> namedIcon(Item item, String name) {
        return () -> {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(name).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
            stack.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("[★] Оригинальный предмет").withStyle(ChatFormatting.GRAY))));
            return stack;
        };
    }
}
