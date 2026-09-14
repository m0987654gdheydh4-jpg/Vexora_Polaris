package polaris.api.drag.impl;

import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.ModuleManager;
import polaris.manager.Manager;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ScissorUtil;
import polaris.utils.render.ui.Render2DCoordinateSpace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HotKeys extends HudPanel {
    private static final float ROW_STEP = 14.0F;
    private static final float PANEL_ANIM = 0.30F;
    private static final float HEADER_HEIGHT = 20f;
    private static final Comparator<Module> MODULE_NAME_COMPARATOR = Comparator.comparing(Module::getName);
    private static final Comparator<RowState> ROW_STATE_COMPARATOR = Comparator.comparingDouble(RowState::offset);

    private final List<RowEntry> rowEntries = new ArrayList<>();
    private final List<Module> activeModules = new ArrayList<>();
    private final List<RowState> rowStates = new ArrayList<>();
    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private final SmoothAnimation iconAlphaAnimation = new SmoothAnimation();
    private boolean iconAlphaForward = true;

    public HotKeys() {
        super("hotkeys", "HotKeys", 300.0F, 40.0F, 80.0F, 23.0F);
    }

    @Override
    public void render() {
        HotKeysState state = logics();
        if (state == null) return;
        renderPanel(state);
    }

    private HotKeysState logics() {
        activeModules.clear();
        ModuleManager manager = Manager.getModules();
        if (manager != null) {
            for (Module module : manager.getModules()) {
                if (module.isEnabled() && !module.isHidden() && module.getBind() != null && module.getBind().isBound()) {
                    activeModules.add(module);
                }
            }
        }
        if (activeModules.size() > 1) {
            activeModules.sort(MODULE_NAME_COMPARATOR);
        }

        boolean preview = activeModules.isEmpty() && editPreview();
        boolean targetVisible = !activeModules.isEmpty() || preview;

        drag.hitExpansion(0.0F, 17.0F, 0.0F, 0.0F);
        panelAnimation.update();
        panelAnimation.run(targetVisible ? 1.0 : 0.0, PANEL_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        iconAlphaAnimation.update();
        if (!iconAlphaAnimation.isAlive()) {
            iconAlphaAnimation.run(iconAlphaForward ? 1.0 : 0.0, 1.55, Easings.EXPO_IN_OUT);
            iconAlphaForward = !iconAlphaForward;
        }

        for (RowEntry entry : rowEntries) {
            entry.active = false;
            entry.alpha.update();
            entry.y.update();
            entry.slide.update();
        }

        int targetRows = 0;
        if (preview) {
            RowEntry entry = row("__preview", "Пример Модуля", "R", ModuleCategory.COMBAT.icon(), 6.0F, 0.0F);
            markRowActive(entry, 0.0F);
            targetRows = 1;
        } else {
            for (Module module : activeModules) {
                String bind = shortBind(module.getBind());
                String icon = module.getCategory() != null ? module.getCategory().icon() : ModuleCategory.MISC.icon();
                float targetY = targetRows * ROW_STEP;
                RowEntry entry = row(module.getName(), module.getName(), bind, icon, 6.0F, targetY);
                markRowActive(entry, targetY);
                targetRows++;
            }
        }

        for (RowEntry entry : rowEntries) {
            if (!entry.active) {
                entry.alpha.run(0.0, ROW_LEAVE, Easings.EXPO_IN, true);
                entry.slide.run(ROW_SLIDE_OUT, ROW_LEAVE, Easings.EXPO_IN, true);
            }
        }
        rowEntries.removeIf(entry -> !entry.active && entry.alpha.get() <= 0.01F && !entry.alpha.isAlive());

        float panelAlpha = panelAnimation.get();
        boolean visible = targetVisible || panelAlpha > 0.01F || !rowEntries.isEmpty();
        contentVisible(visible);
        if (!visible) return null;

        float width = 80.0F;
        float contentRows = 0f;
        for (RowEntry entry : rowEntries) {
            float a = entry.alpha.get();
            if (a > 0.01F || entry.active) {
                contentRows += a;
                width = Math.max(width, Render2D.textWidth(TEXT_FONT, entry.name, 6.0F)
                        + Render2D.textWidth(TEXT_FONT, entry.bind, 6.0F) + 64.0F);
            }
        }

        float totalHeight = HEADER_HEIGHT + Math.max(contentRows, 0.15f) * ROW_STEP + 4f;
        size(width, totalHeight);

        rowStates.clear();
        for (RowEntry entry : rowEntries) {
            float alpha = entry.alpha.get();
            if (alpha > 0.01F || entry.active) {
                rowStates.add(new RowState(entry.name, entry.bind, entry.icon, entry.bindSize,
                        entry.y.get(), entry.slide.get(), alpha));
            }
        }
        if (rowStates.size() > 1) {
            rowStates.sort(ROW_STATE_COMPARATOR);
        }

        return new HotKeysState(rowStates, iconAlphaAnimation.get(), panelAlpha, drag.x(), drag.y(), drag.width(), drag.height());
    }

    private void markRowActive(RowEntry entry, float targetY) {
        entry.active = true;
        entry.alpha.run(1.0, ROW_APPEAR, Easings.EXPO_OUT, true);
        entry.y.run(targetY, ROW_MOVE, Easings.EXPO_OUT, true);
        entry.slide.run(0.0, ROW_APPEAR, Easings.EXPO_OUT, true);
    }

    private void renderPanel(HotKeysState state) {
        float x = state.x;
        float y = state.y;
        float w = state.width;
        float h = state.height;
        int bgAlpha = (int) (255 * state.alpha);

        
        drawPanel(x, y, w, h, bgAlpha, CORNER_MEDIUM);
        drawIosIndicator(x, y, w, h, bgAlpha);

        
        ScissorUtil.push(
                Render2DCoordinateSpace.toGuiInt(x),
                Render2DCoordinateSpace.toGuiInt(y),
                Render2DCoordinateSpace.toGuiInt(x + w),
                Render2DCoordinateSpace.toGuiInt(y + h)
        );

        
        drawPanelHeader(x, y, w, "Бинды", "C", bgAlpha);

        
        float rowY = y + HEADER_HEIGHT;

        for (RowState row : state.rows) {
            float rowAlpha = state.alpha * row.alpha;
            int rowAlphaInt = (int) (255 * rowAlpha);
            float currentY = rowY + row.offset + rowAppearLift(row.alpha);
            float currentX = x + row.slide;

            int textColor = withAlpha(TEXT_COLOR, rowAlphaInt);
            int accentText = withAlpha(TEXT_TERTIARY, rowAlphaInt);
            int iconColor = withAlpha(accentColor(), rowAlphaInt);

            
            float iconSize = 7f;
            float iconWidth = Render2D.textWidth(FontType.MAINMENUSCREEN, row.icon, iconSize);
            float iconX = currentX + 8f;
            float iconY = currentY + (ROW_STEP - iconSize) / 2f;
            Render2D.text(FontType.MAINMENUSCREEN, row.icon, iconX, iconY, iconSize, iconColor);

            
            float nameSize = 6f;
            float nameX = iconX + iconWidth + 5f;
            Render2D.text(TEXT_FONT, row.name, nameX, currentY + (ROW_STEP - nameSize) / 2f - 0.5f, nameSize, textColor);

            
            float bindTextSize = 6f;
            float bindWidth = Render2D.textWidth(TEXT_FONT, row.bind, bindTextSize);
            float boxW = Math.max(13f, bindWidth + 9f);
            float boxH = 9f;
            float boxRadius = 2.5f;
            float boxX = currentX + w - boxW - 6f;
            float boxY = currentY + (ROW_STEP - boxH) / 2f;

            int boxAlpha = (int) (rowAlphaInt * 0.85f);
            HudTheme theme = HudTheme.current();
            int boxColor = theme.backgroundWithAlpha(Math.round(boxAlpha * theme.hudOpacity));
            HudRenderCompat.background(boxX, boxY, boxW, boxH, boxRadius, HudTheme.BLUR_RADIUS, HudTheme.BLUR_SMOOTHNESS, boxColor);
            if (!polaris.api.module.impl.visual.Hud.isGlassMode()) {
                Render2D.outline(boxX, boxY, boxW, boxH, boxRadius, OUTLINE_THICKNESS, theme.outlineWithAlpha((int) (boxAlpha * 0.55f)));
            }

            Render2D.text(TEXT_FONT, row.bind,
                    boxX + (boxW - bindWidth) / 2f,
                    boxY + (boxH - bindTextSize) / 2f - 0.5f,
                    bindTextSize, accentText);
        }

        ScissorUtil.pop();
    }

    private RowEntry row(String key, String name, String bind, String icon, float bindSize, float targetY) {
        for (RowEntry entry : rowEntries) {
            if (entry.key.equals(key)) {
                entry.name = name;
                entry.bind = bind;
                entry.icon = icon;
                entry.bindSize = bindSize;
                return entry;
            }
        }
        RowEntry entry = new RowEntry(key, name, bind, icon, bindSize);
        entry.alpha.set(0.0);
        entry.y.set(targetY + ROW_SPAWN_Y);
        entry.slide.set(-ROW_SLIDE_IN);
        rowEntries.add(entry);
        return entry;
    }

    private static final class RowEntry {
        private final String key;
        private final SmoothAnimation alpha = new SmoothAnimation();
        private final SmoothAnimation y = new SmoothAnimation();
        private final SmoothAnimation slide = new SmoothAnimation();
        private String name;
        private String bind;
        private String icon;
        private float bindSize;
        private boolean active;

        private RowEntry(String key, String name, String bind, String icon, float bindSize) {
            this.key = key;
            this.name = name;
            this.bind = bind;
            this.icon = icon;
            this.bindSize = bindSize;
        }
    }

    private record RowState(String name, String bind, String icon, float bindSize, float offset, float slide, float alpha) {
    }

    private record HotKeysState(List<RowState> rows, float iconAlpha, float alpha, float x, float y, float width, float height) {
    }
}

