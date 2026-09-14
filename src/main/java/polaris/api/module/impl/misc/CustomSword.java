package polaris.api.module.impl.misc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.tags.ItemTags;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;

import java.util.List;


public final class CustomSword extends Module {

    private static final String NONE = "Нет";

    
    private static final String[] MODELS = {
            "Abominable Blade", "Abominable Great Saber", "Abominable Scythe", "Acidic Cleaver",
            "Amethyst Shuriken", "Ancient Royal Great Sword", "Aquatic Sacred Blade", "Arcanethyst",
            "Ashura's Blade", "Awakened Lichblade", "Blood Edge", "Bloody Death", "Bramblethorn",
            "Brimstone Claymore", "Carian Knight's Sword", "Chrono Blade", "Corrupted Mythic Blade",
            "Creation Splitter", "Crescent Rose", "Cyber Katana", "Cyber Mantis Blade", "Cyber Sword",
            "Cybernetic Chainsaw Blade", "Cybernetic Katana", "Cybernetic Knife", "Dainsleif",
            "Dark Blade", "Dark Cleaver", "Death Knight's Dagger", "Death Knight's Sword",
            "Demigod's Unholy Blade", "Demigod's Unholy Halberd", "Demon Lord's Great Axe",
            "Demon Lord's Sword", "Demonic Blade", "Demonic Cleaver", "Divine Axe Rhitta",
            "Divine Justice", "Divine Punisher", "Divine Reaper", "Dragon Slaying blade",
            "Edge Of The Astral Plane", "Emberblade", "Enigma", "Epic Sword", "Estoc",
            "Fallen God's Spear", "Fallen God's Sword", "Floral Longsword", "Floral Sabre",
            "Forest Guardian's Glaive", "Frost Axe", "Frost Blade", "Frost Scythe", "Hearthflame",
            "Hero Sword", "Holy Moonlight Sword", "Hornet's Needle", "Icewhisper", "Jade Halberd",
            "Katana", "Legendary Sword", "Longsword", "Magi Scythe", "Masamune", "Mjolnir",
            "Molten Blade", "Molten Sword", "Muramasa", "Mystical Spellblade", "Mythic Blade",
            "Ocean's Rage", "Partisan", "Pharaoh's Treasure", "Pheonix Grace", "Plague Longsword",
            "Power Fuse Hammer", "Power Fuse Sword", "Requiem of the Ninth Abyss", "Ribbon Cleaver",
            "Righteous Relic", "Rivers Of Blood", "Royal Chakram", "Royal Rapier", "Sabre",
            "Scissor Blade", "Sculk Cleaver", "Sculk Scythe", "Sculk Sword", "Sentinel's Will",
            "Silverine Blade", "Soul Claws", "Soul Collector", "Soul Devourer", "Soul Edge",
            "Soul Harvester", "Soul Stealer", "Soulrender", "Star's Edge", "Steel Sword",
            "Stop Sign", "Storm Bringer", "Storm's Edge", "Sunbreak", "Tengen's Blade",
            "Terra Blade", "Thousand Demon Daggers", "Thunder Bringer", "Thunderbrand",
            "True Excalibur", "Vampiric Needle", "Wakizashi", "Watcher Claymore",
            "Watching Warglaive", "Waxweaver", "Whisperwind", "Wickpiercer", "Wraith Scythe", "Yoru"
    };

    private static CustomSword instance;

    private final BooleanSetting perSword = register(new BooleanSetting(
            "Для каждого меча", "Выбирать модель отдельно для каждого типа меча", false));

    private final ModeSetting modelAll = register(new ModeSetting(
            "Модель (общая)", "Модель для всех мечей", "Katana", MODELS));

    private final ModeSetting modelWooden = register(new ModeSetting(
            "Деревянный меч", "Модель для деревянного меча", NONE, buildModelsWithNone()));

    private final ModeSetting modelStone = register(new ModeSetting(
            "Каменный меч", "Модель для каменного меча", NONE, buildModelsWithNone()));

    private final ModeSetting modelIron = register(new ModeSetting(
            "Железный меч", "Модель для железного меча", NONE, buildModelsWithNone()));

    private final ModeSetting modelGolden = register(new ModeSetting(
            "Золотой меч", "Модель для золотого меча", NONE, buildModelsWithNone()));

    private final ModeSetting modelDiamond = register(new ModeSetting(
            "Алмазный меч", "Модель для алмазного меча", "Katana", buildModelsWithNone()));

    private final ModeSetting modelNetherite = register(new ModeSetting(
            "Незеритовый меч", "Модель для незеритового меча", "Katana", buildModelsWithNone()));

    {
        modelAll.visibleWhen(() -> !perSword.getValue());
        modelWooden.visibleWhen(perSword::getValue);
        modelStone.visibleWhen(perSword::getValue);
        modelIron.visibleWhen(perSword::getValue);
        modelGolden.visibleWhen(perSword::getValue);
        modelDiamond.visibleWhen(perSword::getValue);
        modelNetherite.visibleWhen(perSword::getValue);
    }

    public CustomSword() {
        super("Custom Sword", "Заменяет визуальную модель меча в руках", ModuleCategory.MISC);
        instance = this;
    }

    public static CustomSword getInstance() {
        return instance;
    }

    
    public ItemStack getRenderStack(ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty() || !stack.is(ItemTags.SWORDS)) {
            return stack;
        }

        String modelName = getModelForStack(stack);
        if (modelName == null || modelName.equals(NONE)) {
            return stack;
        }

        ItemStack copy = stack.copy();
        copy.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(),
                List.of(),
                List.of(modelName),
                List.of()
        ));
        return copy;
    }

    private String getModelForStack(ItemStack stack) {
        if (!perSword.getValue()) {
            return modelAll.getValue();
        }

        if (stack.is(Items.WOODEN_SWORD)) return modelWooden.getValue();
        if (stack.is(Items.STONE_SWORD)) return modelStone.getValue();
        if (stack.is(Items.IRON_SWORD)) return modelIron.getValue();
        if (stack.is(Items.GOLDEN_SWORD)) return modelGolden.getValue();
        if (stack.is(Items.DIAMOND_SWORD)) return modelDiamond.getValue();
        if (stack.is(Items.NETHERITE_SWORD)) return modelNetherite.getValue();

        return modelAll.getValue();
    }

    private static String[] buildModelsWithNone() {
        String[] result = new String[MODELS.length + 1];
        result[0] = NONE;
        System.arraycopy(MODELS, 0, result, 1, MODELS.length);
        return result;
    }
}

