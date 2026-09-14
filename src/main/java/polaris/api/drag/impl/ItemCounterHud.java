package polaris.api.drag.impl;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;

public class ItemCounterHud extends HudPanel {
    private static final float WIDTH = 24f;
    private static final float HEIGHT = 24f;
    private static final float FONT_SIZE = 6f;

    public enum HudType {
        GAPPLE(Items.GOLDEN_APPLE, "Gapples HUD"),
        ENCHANTED_GAPPLE(Items.ENCHANTED_GOLDEN_APPLE, "Enchanted Gapples HUD"),
        TOTEM(Items.TOTEM_OF_UNDYING, "Totems HUD"),
        FIREWORK(Items.FIREWORK_ROCKET, "Fireworks HUD"),
        CRYSTAL(Items.END_CRYSTAL, "Crystals HUD"),
        BOTTLE(Items.EXPERIENCE_BOTTLE, "XP Bottles HUD");

        private final Item targetItem;
        private final String name;

        HudType(Item targetItem, String name) {
            this.targetItem = targetItem;
            this.name = name;
        }
    }

    private final HudType type;
    private int currentCount;
    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    public ItemCounterHud(HudType type) {
        super(type.name().toLowerCase().replace(" ", "_"), type.name, 200.0F, 200.0F, WIDTH, HEIGHT);
        this.type = type;
    }

    @Override
    public void render() {
        if (mc.player == null) return;

        currentCount = getItemCount(type.targetItem);
        boolean isChatOpen = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;

        // Если предметов 0, но чат открыт — принудительно ставим 1 для визуального примера
        if (currentCount == 0 && isChatOpen) {
            currentCount = 1;
        }

        if (currentCount == 0 && !editPreview()) {
            contentVisible(false);
            return;
        }

        panelAnimation.update();
        panelAnimation.run(true ? 1.0 : 0.0, 0.24F, Easings.EXPO_OUT, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        logics();

        float x = drag.x();
        float y = drag.y();

        size(WIDTH, HEIGHT);

        int bgAlpha = (int) (235 * alpha);
        drawPanel(x, y, WIDTH, HEIGHT, bgAlpha, CORNER_MEDIUM);

        try {
            RenderItem.item(new ItemStack(type.targetItem), x + 4f, y + 2f, 16.0f);
        } catch (Throwable ignored) {}

        String countStr = String.valueOf(currentCount);
        float textW = Render2D.textWidth(TEXT_FONT, countStr, FONT_SIZE);
        int textRgb = withAlpha(TEXT_COLOR, (int)(255 * alpha));

        Render2D.text(TEXT_FONT, countStr, (x + (WIDTH / 2f)) - (textW / 2f), y + 16f, FONT_SIZE, textRgb);
    }
    private void logics() {
        size(WIDTH, HEIGHT);
    }

    public int getItemCount(Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i <= 44; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    protected boolean editPreview() {
        return false;
    }
}
