package polaris.api.drag.impl;

import net.minecraft.world.item.ItemStack;
import polaris.api.module.impl.world.ScaffoldModule;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.ui.Render2D;

/**
 * HUD-счётчик блоков для Scaffold (аналог ItemCounterHud, но считает
 * все валидные блоки в инвентаре и показывает иконку лучшего стека).
 */
public class ScaffoldBlockCounterHud extends HudPanel {
    private static final float WIDTH = 24f;
    private static final float HEIGHT = 24f;
    private static final float FONT_SIZE = 6f;

    private int currentCount;
    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    public ScaffoldBlockCounterHud() {
        super("scaffold_blocks", "Scaffold Blocks HUD", 200.0F, 200.0F, WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        if (mc.player == null) return;
        ScaffoldModule scaffold = ScaffoldModule.getInstance();
        if (scaffold == null || !scaffold.isEnabled()) {
            contentVisible(false);
            return;
        }

        currentCount = scaffold.getBlockCount();
        if (currentCount == 0 && !editPreview()) {
            contentVisible(false);
            return;
        }

        panelAnimation.update();
        panelAnimation.run(1.0, 0.24F, Easings.EXPO_OUT, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        size(WIDTH, HEIGHT);
        float x = drag.x();
        float y = drag.y();

        drawPanel(x, y, WIDTH, HEIGHT, (int) (235 * alpha), CORNER_MEDIUM);

        ItemStack icon = scaffold.getBestStack();
        if (!icon.isEmpty()) {
            try {
                RenderItem.item(icon, x + 4f, y + 2f, 16.0f);
            } catch (Throwable ignored) {}
        }

        String countStr = currentCount > 999 ? "999+" : String.valueOf(currentCount);
        float textW = Render2D.textWidth(TEXT_FONT, countStr, FONT_SIZE);
        Render2D.text(TEXT_FONT, countStr,
                (x + WIDTH / 2f) - textW / 2f, y + 16f, FONT_SIZE,
                withAlpha(TEXT_COLOR, (int) (255 * alpha)));
    }

    @Override
    protected boolean editPreview() {
        return false;
    }
}