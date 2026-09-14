package polaris.api.module.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.events.impl.HandledScreenEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;

import java.awt.Color;


public final class ItemHighlighter extends Module {
    private static final long PULSE_MS = 900L;

    private final BooleanSetting pulse = register(new BooleanSetting("Pulse", "Pulse highlight effect.", true));
    private final NumberSetting baseAlpha = register(new NumberSetting("Alpha", "Base highlight alpha.", 170.0, 0.0, 255.0, 5.0));

    private final BooleanSetting eyeOfEnder = register(new BooleanSetting("Eye of Ender", "Highlight Eye of Ender.", true));
    private final ColorSetting eyeColor = register(new ColorSetting("Eye Color", "Eye of Ender highlight color.", new Color(80, 220, 255, 200)));
    private final BooleanSetting fireball = register(new BooleanSetting("Fire Charge", "Highlight Fire Charge.", true));
    private final ColorSetting fireColor = register(new ColorSetting("Fire Color", "Fire Charge highlight color.", new Color(255, 100, 30, 200)));
    private final BooleanSetting sugar = register(new BooleanSetting("Sugar", "Highlight Sugar.", true));
    private final ColorSetting sugarColor = register(new ColorSetting("Sugar Color", "Sugar highlight color.", new Color(255, 255, 255, 200)));
    private final BooleanSetting totem = register(new BooleanSetting("Totem", "Highlight Totem of Undying.", true));
    private final ColorSetting totemColor = register(new ColorSetting("Totem Color", "Totem highlight color.", new Color(200, 80, 255, 200)));
    private final BooleanSetting exp = register(new BooleanSetting("Exp Bottle", "Highlight Experience Bottle.", true));
    private final ColorSetting expColor = register(new ColorSetting("Exp Color", "Exp Bottle highlight color.", new Color(120, 220, 80, 200)));
    private final BooleanSetting trapNetherite = register(new BooleanSetting("Trap Netherite", "Highlight Netherite Ingot.", true));
    private final ColorSetting trapNethColor = register(new ColorSetting("Netherite Color", "Netherite Ingot highlight color.", new Color(80, 80, 80, 200)));
    private final BooleanSetting gapple = register(new BooleanSetting("Golden Apple", "Highlight Golden Apple.", true));
    private final ColorSetting gappleColor = register(new ColorSetting("Gapple Color", "Golden Apple highlight color.", new Color(255, 200, 50, 200)));
    private final BooleanSetting enchantedGapple = register(new BooleanSetting("Enchanted Gapple", "Highlight Enchanted Golden Apple.", true));
    private final ColorSetting charkaColor = register(new ColorSetting("Ench. Gapple Color", "Enchanted Golden Apple highlight color.", new Color(255, 100, 150, 200)));
    private final BooleanSetting chorus = register(new BooleanSetting("Chorus Fruit", "Highlight Chorus Fruit.", true));
    private final ColorSetting chorusColor = register(new ColorSetting("Chorus Color", "Chorus Fruit highlight color.", new Color(200, 130, 255, 200)));
    private final BooleanSetting pearl = register(new BooleanSetting("Ender Pearl", "Highlight Ender Pearl.", true));
    private final ColorSetting pearlColor = register(new ColorSetting("Pearl Color", "Ender Pearl highlight color.", new Color(100, 180, 255, 200)));
    private final BooleanSetting snowball = register(new BooleanSetting("Snowball", "Highlight Snowball.", true));
    private final ColorSetting snowColor = register(new ColorSetting("Snow Color", "Snowball highlight color.", new Color(200, 230, 255, 200)));
    private final BooleanSetting trapChorus = register(new BooleanSetting("Popped Chorus", "Highlight Popped Chorus Fruit.", true));
    private final ColorSetting trapChorusColor = register(new ColorSetting("Popped Chorus Color", "Popped Chorus Fruit highlight color.", new Color(180, 100, 240, 200)));
    private final BooleanSetting netherStar = register(new BooleanSetting("Nether Star", "Highlight Nether Star.", true));
    private final ColorSetting starColor = register(new ColorSetting("Star Color", "Nether Star highlight color.", new Color(255, 255, 150, 200)));
    private final BooleanSetting jack = register(new BooleanSetting("Jack o'Lantern", "Highlight Jack o'Lantern.", true));
    private final ColorSetting jackColor = register(new ColorSetting("Jack Color", "Jack o'Lantern highlight color.", new Color(255, 160, 30, 200)));
    private final BooleanSetting trapPrismarine = register(new BooleanSetting("Prismarine Crystals", "Highlight Prismarine Crystals.", true));
    private final ColorSetting prismColor = register(new ColorSetting("Prismarine Color", "Prismarine Crystals highlight color.", new Color(80, 220, 200, 200)));

    public ItemHighlighter() {
        super("Item Highlighter", "Highlights important items in inventory and hotbar.", ModuleCategory.VISUAL);

        eyeColor.visible(() -> eyeOfEnder.getValue());
        fireColor.visible(() -> fireball.getValue());
        sugarColor.visible(() -> sugar.getValue());
        totemColor.visible(() -> totem.getValue());
        expColor.visible(() -> exp.getValue());
        trapNethColor.visible(() -> trapNetherite.getValue());
        gappleColor.visible(() -> gapple.getValue());
        charkaColor.visible(() -> enchantedGapple.getValue());
        chorusColor.visible(() -> chorus.getValue());
        pearlColor.visible(() -> pearl.getValue());
        snowColor.visible(() -> snowball.getValue());
        trapChorusColor.visible(() -> trapChorus.getValue());
        starColor.visible(() -> netherStar.getValue());
        jackColor.visible(() -> jack.getValue());
        prismColor.visible(() -> trapPrismarine.getValue());
    }

    @SubscribeEvent
    private void onHandledScreen(HandledScreenEvent event) {
        if (mc.player == null || mc.screen == null || event.getGraphics() == null) {
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        
        
        if (!event.isSlotsBackground()) {
            return;
        }
        drawContainerHighlightsLocal(screen, event.getGraphics());
    }

    
    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (mc.player == null || event.getGraphics() == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (event.getLayer() != DrawEvent.Layer.GAME) {
            return;
        }
        drawHotbar(event.getGraphics());
    }

    
    private void drawContainerHighlightsLocal(AbstractContainerScreen<?> screen, GuiGraphics graphics) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot == null || !slot.isActive()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            Color color = getHighlightColor(stack);
            if (color == null) {
                continue;
            }
            drawSlotHighlight(graphics, slot.x, slot.y, color);
        }
    }

    private void drawHotbar(GuiGraphics graphics) {
        Minecraft client = mc;
        int hotbarX = client.getWindow().getGuiScaledWidth() / 2 - 91;
        int hotbarY = client.getWindow().getGuiScaledHeight() - 22;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            Color color = getHighlightColor(stack);
            if (color != null) {
                drawSlotHighlight(graphics, hotbarX + i * 20 + 3, hotbarY + 3, color);
            }
        }
    }

    private void drawSlotHighlight(GuiGraphics graphics, int x, int y, Color baseColor) {
        int alpha = baseAlpha.getValue().intValue();

        if (pulse.getValue()) {
            long now = System.currentTimeMillis();
            float phase = (float) (now % PULSE_MS) / (float) PULSE_MS;
            float wave = 0.5F - 0.5F * Mth.cos(phase * (float) (Math.PI * 2.0D));
            alpha = Mth.clamp((int) (alpha * (0.55F + 0.45F * wave)), 0, 255);
        }

        int fillA = Math.max(0, Math.min(255, alpha / 3));
        int borderA = Math.max(0, Math.min(255, alpha));
        int r = baseColor.getRed();
        int g = baseColor.getGreen();
        int b = baseColor.getBlue();

        int fillColor = (fillA << 24) | (r << 16) | (g << 8) | b;
        int borderColor = (borderA << 24) | (r << 16) | (g << 8) | b;

        
        graphics.fill(x, y, x + 16, y + 16, fillColor);
        graphics.fill(x, y, x + 16, y + 1, borderColor);
        graphics.fill(x, y + 15, x + 16, y + 16, borderColor);
        graphics.fill(x, y, x + 1, y + 16, borderColor);
        graphics.fill(x + 15, y, x + 16, y + 16, borderColor);
    }

    private Color getHighlightColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();

        if (eyeOfEnder.getValue() && item == Items.ENDER_EYE) {
            return eyeColor.getValue();
        } else if (fireball.getValue() && item == Items.FIRE_CHARGE) {
            return fireColor.getValue();
        } else if (sugar.getValue() && item == Items.SUGAR) {
            return sugarColor.getValue();
        } else if (totem.getValue() && item == Items.TOTEM_OF_UNDYING) {
            return totemColor.getValue();
        } else if (exp.getValue() && item == Items.EXPERIENCE_BOTTLE) {
            return expColor.getValue();
        } else if (trapNetherite.getValue() && item == Items.NETHERITE_INGOT) {
            return trapNethColor.getValue();
        } else if (gapple.getValue() && item == Items.GOLDEN_APPLE) {
            return gappleColor.getValue();
        } else if (enchantedGapple.getValue() && item == Items.ENCHANTED_GOLDEN_APPLE) {
            return charkaColor.getValue();
        } else if (chorus.getValue() && item == Items.CHORUS_FRUIT) {
            return chorusColor.getValue();
        } else if (pearl.getValue() && item == Items.ENDER_PEARL) {
            return pearlColor.getValue();
        } else if (snowball.getValue() && item == Items.SNOWBALL) {
            return snowColor.getValue();
        } else if (trapChorus.getValue() && item == Items.POPPED_CHORUS_FRUIT) {
            return trapChorusColor.getValue();
        } else if (netherStar.getValue() && item == Items.NETHER_STAR) {
            return starColor.getValue();
        } else if (jack.getValue() && item == Items.JACK_O_LANTERN) {
            return jackColor.getValue();
        } else if (trapPrismarine.getValue() && item == Items.PRISMARINE_CRYSTALS) {
            return prismColor.getValue();
        }

        return null;
    }
}
