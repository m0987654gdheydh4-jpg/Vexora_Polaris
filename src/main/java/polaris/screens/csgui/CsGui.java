package polaris.screens.csgui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import polaris.api.drag.impl.HudTheme;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.ModuleManager;
import polaris.api.module.impl.misc.ClickGuiModule;
import polaris.api.module.impl.visual.Hud;
import polaris.api.settings.Setting;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.ColorSetting;
import polaris.manager.Manager;
import polaris.screens.clickgui.ClickGuiOpenEffects;
import polaris.screens.csgui.elements.*;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import java.util.Calendar;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CsGui extends Screen {


    private static final float REF_DESIGN_W = 960f;
    private static final float REF_DESIGN_H = 540f;

    private static final float PANEL_W_BASE = 720f;
    private static final float PANEL_H_BASE = 380f;
    private static final float ROUNDING = 12f;
    private static final float MINI_H_BASE = 56f;
    private static final float HEADER_H = 44f;
    private static final int MODULE_COLUMNS = 3;

    private static final float SEPARATOR_A = 55;
    private static final float CONTENT_PAD = 8f;
    private static final float SCROLLBAR_W = 4f;
    private static final float SCROLLBAR_GAP = 5f;

    private static final String[] CLIENT_CATS = {"Themes", "Config", "Cosmetics"};
    private static final String[] CLIENT_CAT_ICON_PATHS = {
            CsMenuAssets.THEMES_TAB, CsMenuAssets.CONFIGS_TAB, CsMenuAssets.COSMETICS_TAB
    };
    private static final int AUTOBUY_CAT_INDEX = ModuleCategory.AUTOBUY.ordinal();
    private static final int THEMES_CAT_INDEX = ModuleCategory.values().length;
    private static final int CONFIG_CAT_INDEX = ModuleCategory.values().length + 1;
    private static final int COSMETICS_CAT_INDEX = ModuleCategory.values().length + 2;
    private static final int TOTAL_CATS = ModuleCategory.values().length + CLIENT_CATS.length;


    private static int savedCategory = 0;
    private static final float[] savedModuleScrollByCat = new float[TOTAL_CATS];
    private static float savedThemesScroll = 0f;
    private static float savedConfigScroll = 0f;
    private static float savedCosmeticsScroll = 0f;
    private static float savedAutoBuyScroll = 0f;
    private static float savedAutoBuyRulesScroll = 0f;

    private final Minecraft mc;
    private final ModuleManager mm;
    private final ClickGuiModule cgm;
    private final Screen previous;

    private int selectedCategory = 0;
    private final List<float[]> catHitBoxes = new ArrayList<>();
    private final List<Float> catRectPositions = new ArrayList<>();
    private final List<SimpleLinearAnimation> catColorAnims = new ArrayList<>();
    private float fromRectY = 0f, toRectY = 0f;
    private boolean rectPositionInitialized = false;
    private final SimpleLinearAnimation rectSlideAnim = new SimpleLinearAnimation(200);
    private final SimpleLinearAnimation guiScaleAnim = new SimpleLinearAnimation(400);
    private final SimpleLinearAnimation guiOpenAnim = new SimpleLinearAnimation(240);
    private float moduleScroll = 0f;
    private float moduleTargetScroll = 0f;

    private float pendingModuleScroll = -1f;
    private int pendingModuleScrollFrames = 0;


    private Module focusModule;
    private boolean focusScrollDone;
    private long focusFlashUntilMs;
    private int focusWaitFrames = 0;
    private static final int FOCUS_MAX_WAIT = 10;
    private Module bindingModule = null;
    private final Map<Module, SimpleLinearAnimation> moduleToggleAnims = new HashMap<>();

    private float themesScroll = 0f, themesTargetScroll = 0f;
    private float configScroll = 0f, configTargetScroll = 0f;

    private float catScroll = 0f;
    private float catTargetScroll = 0f;

    private static final int SB_NONE = 0, SB_MODULES = 1, SB_THEMES = 2, SB_CONFIG = 3;
    private int draggingScrollbar = SB_NONE;
    private float sbDragOffset = 0f, sbDragTrackY = 0f, sbDragTrackH = 0f, sbDragThumbH = 0f;
    private double sbDragMaxScroll = 0;

    private static class ScrollbarGeometry {
        float trackX, trackY, trackW, trackH;
        float thumbY, thumbH;
        float totalContentH, viewportH;
        boolean visible;

        void clear() { visible = false; }
        void set(float tx, float ty, float tw, float th, float thY, float thH, float total, float view) {
            this.trackX = tx; this.trackY = ty; this.trackW = tw; this.trackH = th;
            this.thumbY = thY; this.thumbH = thH; this.totalContentH = total; this.viewportH = view;
            this.visible = true;
        }
        boolean hitThumb(double mx, double my) {
            return visible && mx >= trackX - 8 && mx <= trackX + trackW + 8 && my >= thumbY && my <= thumbY + thumbH;
        }
        boolean hitTrack(double mx, double my) {
            return visible && mx >= trackX - 8 && mx <= trackX + trackW + 8 && my >= trackY && my <= trackY + trackH;
        }
    }

    private final ScrollbarGeometry sbModules = new ScrollbarGeometry();
    private final ScrollbarGeometry sbThemes = new ScrollbarGeometry();
    private final ScrollbarGeometry sbConfig = new ScrollbarGeometry();

    private final TextInputField configNameInput;
    private final TextInputField searchInput;
    private final CsThemeSettings themeSettings = new CsThemeSettings();
    private final CsConfigPanel configPanel;
    private final CsAutoBuyPanel autoBuyPanel = new CsAutoBuyPanel();
    private final CsCosmeticsPanel cosmeticsPanel = new CsCosmeticsPanel();
    private final SimpleLinearAnimation contentAnim = new SimpleLinearAnimation(430);

    private float contentEnterDir = 1f;
    private final SimpleLinearAnimation searchExpandAnim = new SimpleLinearAnimation(220);
    private boolean searchOpen = false;
    private int lastContentCategory = -1;

    private String prevSearchQuery = "";
    private final Map<Module, Map<Setting<?>, CsSettingComponent<?>>> moduleSettingComps = new HashMap<>();


    private static final class SearchHit {
        final Module module;
        final Setting<?> setting;
        final boolean exact;
        final boolean startsWith;

        SearchHit(Module module, Setting<?> setting, boolean exact, boolean startsWith) {
            this.module = module;
            this.setting = setting;
            this.exact = exact;
            this.startsWith = startsWith;
        }

        boolean isSetting() {
            return setting != null;
        }
    }

    private static final String SEARCH_HINT = "click to search";
    private static final float SEARCH_H = 24f;
    private static final float SEARCH_ICON = 9.5f;
    private static final float SEARCH_PAD = 5f;
    private static final float SEARCH_ROUND = 3f;
    private static final float SEARCH_MIN_W = 95f;
    private static final float SEARCH_WIDTH_RATIO = 0.23f;

    private static final float SEARCH_SIDE_RESERVE = 96f;


    private static final float TEXT_OPTICAL_NUDGE = 1.0f;

    private static final float CARD_PAD = 8f;


    private static final float APPEAR_COL_DELAY = 0.10f;
    private static final float APPEAR_ROW_DELAY = 0.085f;
    private static final float APPEAR_DELAY_CAP = 0.62f;
    private static final float APPEAR_SLIDE = 26f;

    private static final float APPEAR_UNFOLD_SPLIT = 0.45f;

    private static final float SETTINGS_GAP = 3.0f;
    private static final float HEADER_TO_SETTINGS_GAP = 1f;
    private static final float SETTINGS_BOTTOM_PADDING = 4f;
    private static final float SETTINGS_SIDE_INSET = 5f;
    private static final float BASE_MODULE_H = 28f;
    private static final float MODULE_GAP_X = 5f;
    private static final float MODULE_GAP_Y = 5f;

    private static final float TOGGLE_W = 22f;
    private static final float TOGGLE_H = 13f;

    private static final float ICON_NUDGE_Y = 1.4f;

    private static final float MODULE_ICON_NUDGE_Y = 3.2f;

    private float animProgress = 0f;

    private float openYaw;
    private float openPitch;

    private float openEffectScale = 1f;
    private float openCamX;
    private float openCamY;


    private int accentColor = ColorUtil.rgba(120, 180, 255, 255);

    private boolean protectedMode = false;
    private final SimpleLinearAnimation protectAnim = new SimpleLinearAnimation(180);
    private float[] protectHitBox = new float[4];
    private static final String PROTECT_FILE_NAME = "protected_mode.txt";

    public CsGui(Screen previous) {
        super(Component.literal("CsGui"));
        this.previous = previous;
        this.mc = Minecraft.getInstance();
        ModuleManager m = Manager.getModules();
        this.mm = m;
        this.cgm = m != null ? m.getByType(ClickGuiModule.class).orElse(null) : null;

        this.selectedCategory = clampCategory(savedCategory);
        float[] look = ClickGuiOpenEffects.captureLook();
        this.openYaw = look[0];
        this.openPitch = look[1];

        ClickGuiOpenEffects.captureBackdropCenter();
        ClickGuiOpenEffects.onGuiOpening();
        guiScaleAnim.show();
        guiOpenAnim.show();
        initCategoryAnimations();

        this.searchInput = new TextInputField(0, 0, 140, 18, 8f);
        searchInput.setTextSize(8f);
        searchInput.setPlaceholder("Search…");
        searchInput.setBackgroundColor(ColorUtil.rgba(0, 0, 0, 0));
        searchInput.setTextColor(ColorUtil.rgba(230, 232, 238, 255));
        searchInput.setPlaceholderColor(ColorUtil.rgba(120, 122, 132, 200));
        searchInput.setCursorColor(ColorUtil.rgba(230, 232, 238, 220));
        searchExpandAnim.setImmediate(false);

        this.configNameInput = new TextInputField(0, 0, 100, 22, 5f);
        configNameInput.setTextSize(8.5f);
        configNameInput.setPlaceholder("Имя…");
        configNameInput.setBackgroundColor(ColorUtil.rgba(0, 0, 0, 0));
        configNameInput.setTextColor(ColorUtil.rgba(230, 232, 238, 255));
        configNameInput.setPlaceholderColor(ColorUtil.rgba(120, 122, 132, 200));
        configNameInput.setCursorColor(ColorUtil.rgba(230, 232, 238, 220));
        this.configPanel = new CsConfigPanel(configNameInput);
        restoreScrollForCategory(selectedCategory);
        contentAnim.show();
        lastContentCategory = selectedCategory;

        loadProtectedMode();
        if (protectedMode) protectAnim.show(); else protectAnim.hide();
    }

    private void loadProtectedMode() {
        try {
            Path path = mc.gameDirectory.toPath().resolve("polaris").resolve(PROTECT_FILE_NAME);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                protectedMode = "true".equalsIgnoreCase(content);
            }
        } catch (Throwable ignored) {
        }
    }

    private void saveProtectedMode() {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("polaris");
            Files.createDirectories(dir);
            Path path = dir.resolve(PROTECT_FILE_NAME);
            Files.writeString(path, protectedMode ? "true" : "false");
        } catch (Throwable ignored) {
        }
    }

    private static int clampCategory(int index) {
        if (index < 0) {
            return 0;
        }
        if (index >= TOTAL_CATS) {
            return TOTAL_CATS - 1;
        }
        return index;
    }

    private void persistUiState() {


        if (autoBuyPanel != null) {
            autoBuyPanel.onGuiClosed();
        }
        savedCategory = clampCategory(selectedCategory);
        storeScrollForCategory(selectedCategory);
        saveProtectedMode();
    }

    private void storeScrollForCategory(int category) {
        category = clampCategory(category);
        if (category == THEMES_CAT_INDEX) {
            if (themeSettings != null) {
                savedThemesScroll = themeSettings.getScroll();
            }
            return;
        }
        if (category == CONFIG_CAT_INDEX) {
            if (configPanel != null) {
                savedConfigScroll = configPanel.getScroll();
            }
            return;
        }
        if (category == COSMETICS_CAT_INDEX) {
            savedCosmeticsScroll = cosmeticsPanel.getScroll();
            return;
        }
        if (category == AUTOBUY_CAT_INDEX) {
            if (autoBuyPanel != null) {
                savedAutoBuyScroll = autoBuyPanel.getScroll();
                savedAutoBuyRulesScroll = autoBuyPanel.getRulesScroll();
            }
            return;
        }

        savedModuleScrollByCat[category] = Math.max(moduleTargetScroll, moduleScroll);
    }

    private void restoreScrollForCategory(int category) {
        category = clampCategory(category);
        if (category == THEMES_CAT_INDEX) {
            if (themeSettings != null) {
                themeSettings.setScroll(savedThemesScroll);
            }
            moduleScroll = moduleTargetScroll = 0f;
            return;
        }
        if (category == CONFIG_CAT_INDEX) {
            if (configPanel != null) {
                configPanel.setScroll(savedConfigScroll);
            }
            moduleScroll = moduleTargetScroll = 0f;
            return;
        }
        if (category == COSMETICS_CAT_INDEX) {
            cosmeticsPanel.setScroll(savedCosmeticsScroll);
            moduleScroll = moduleTargetScroll = 0f;
            return;
        }
        if (category == AUTOBUY_CAT_INDEX) {
            if (autoBuyPanel != null) {
                autoBuyPanel.setScroll(savedAutoBuyScroll);
                autoBuyPanel.setRulesScroll(savedAutoBuyRulesScroll);
            }
            moduleScroll = moduleTargetScroll = 0f;
            return;
        }
        float scroll = Math.max(0f, savedModuleScrollByCat[category]);
        moduleScroll = moduleTargetScroll = scroll;
        if (scroll > 0f) {
            pendingModuleScroll = scroll;
            pendingModuleScrollFrames = 12;
        } else {
            cancelPendingModuleScroll();
        }
    }

    private void onCategoryChanged(int from, int to) {


        storeScrollForCategory(from);
        restoreScrollForCategory(to);

        contentEnterDir = to >= from ? 1f : -1f;
        contentAnim.setImmediate(false);
        contentAnim.show();
        if (to == COSMETICS_CAT_INDEX) {


            cosmeticsPanel.reset();
            cosmeticsPanel.setScroll(savedCosmeticsScroll);
        }
        if (to == THEMES_CAT_INDEX && themeSettings != null) {
            themeSettings.mouseReleased();
        }
        CsColorPicker.get().close();
    }

    private float contentAnimProgress() {
        return contentAnim.getProgress();
    }

    private float contentSlideOffset() {
        float p = easeOutCubic(contentAnimProgress());
        return (1f - p) * 10f * contentEnterDir;
    }

    private static float easeOutCubic(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    public boolean isAnyTextInputFocused() {
        return searchOpen || searchInput.isFocused() || configNameInput.isFocused()
                || autoBuyPanel.isSearchFocused()
                || CsStringSetting.editing != null || TextInputField.focusedField != null;
    }

    private void openSearch() {
        searchOpen = true;
        searchExpandAnim.show();
        searchInput.setFocused(true);
    }

    private void closeSearch() {
        searchOpen = false;
        searchExpandAnim.hide();
        searchInput.setFocused(false);
        searchInput.setText("");
        prevSearchQuery = "";
    }

    private float searchExpandProgress() {
        return searchExpandAnim.getProgress();
    }


    private void syncClientTheme() {
        try {
            HudTheme theme = HudTheme.current();
            accentColor = theme.accentColor;
            CsSettingComponent.themeAccent = accentColor;
        } catch (Throwable ignored) {
            accentColor = ColorUtil.rgba(120, 180, 255, 255);
            CsSettingComponent.themeAccent = accentColor;
        }
    }

    private void initCategoryAnimations() {
        for (int i = 0; i < TOTAL_CATS; i++) {
            catColorAnims.add(new SimpleLinearAnimation(250));
            catColorAnims.get(i).hide();
        }
        if (!catColorAnims.isEmpty()) {
            SimpleLinearAnimation first = catColorAnims.get(selectedCategory);
            first.show();
        }
    }

    private String getSelectedCategoryName() {
        if (selectedCategory < ModuleCategory.values().length)
            return ModuleCategory.values()[selectedCategory].getDisplayName();
        int ci = selectedCategory - ModuleCategory.values().length;
        return ci >= 0 && ci < CLIENT_CATS.length ? CLIENT_CATS[ci] : "";
    }

    private String getSelectedCategoryIcon() {
        if (selectedCategory < ModuleCategory.values().length)
            return ModuleCategory.values()[selectedCategory].icon();
        return "";
    }

    private String getSelectedClientIconPath() {
        int ci = selectedCategory - ModuleCategory.values().length;
        return ci >= 0 && ci < CLIENT_CAT_ICON_PATHS.length ? CLIENT_CAT_ICON_PATHS[ci] : null;
    }

    private int alpha(float ap) { return Math.round(255 * ap); }


    private float designW() {
        return Render2D.getFixedScaledWidth();
    }

    private float designH() {
        return Render2D.getFixedScaledHeight();
    }


    private float uiScale() {
        float user = 1f;
        if (cgm != null) {
            try {
                user = (float) (cgm.scaleSetting().getValue() / 100.0);
            } catch (Throwable ignored) {
            }
        }
        float fit = Math.min(designW() / REF_DESIGN_W, designH() / REF_DESIGN_H);

        fit = Math.max(0.62f, Math.min(1.55f, fit));
        return fit * user;
    }

    private float panelW() {
        float w = PANEL_W_BASE * uiScale();

        float max = Math.max(280f, designW() - 32f);
        return Math.min(w, max);
    }

    private float panelH() {
        float h = PANEL_H_BASE * uiScale();
        float max = Math.max(220f, designH() - 32f);
        return Math.min(h, max);
    }

    private float miniH() {
        float h = MINI_H_BASE * uiScale();
        return Math.max(48f * uiScale(), Math.min(h, 64f * uiScale()));
    }


    private static int pickColumn(float[] colYs) {
        int best = 0;
        for (int i = 1; i < colYs.length; i++) {
            if (colYs[i] < colYs[best]) {
                best = i;
            }
        }
        return best;
    }

    private static float maxColumnY(float[] colYs) {
        float m = colYs[0];
        for (int i = 1; i < colYs.length; i++) {
            m = Math.max(m, colYs[i]);
        }
        return m;
    }

    private float headerH() {
        return HEADER_H * uiScale();
    }

    private float rounding() {
        return ROUNDING * Math.max(0.85f, Math.min(1.2f, uiScale()));
    }

    private float panelX() {
        return (designW() - panelW()) * 0.5f;
    }

    private float panelY() {
        return (designH() - panelH()) * 0.5f;
    }

    private float fixedMouseX(double guiX) {
        return mapMouseX(Render2D.guiToFixed(guiX));
    }

    private float fixedMouseY(double guiY) {
        return mapMouseY(Render2D.guiToFixed(guiY));
    }

    private float[] contentArea(float sx, float sy) {
        float miniH = miniH();
        float headerH = headerH();
        float pw = panelW();
        float ph = panelH();
        float pad = CONTENT_PAD * uiScale();

        float cornerInset = rounding() * 0.45f;
        float areaX = sx + 1.5f;
        float areaY = sy + headerH + 1.5f;
        float areaW = pw - 1.5f - cornerInset;
        float areaH = ph - headerH - miniH - 1.5f - cornerInset;
        float contentX = areaX + pad;
        float contentY = areaY + pad;
        float contentW = areaW - pad * 2f - SCROLLBAR_W - SCROLLBAR_GAP;
        float contentH = areaH - pad * 2f;
        return new float[]{contentX, contentY, contentW, contentH, areaX, areaY, areaW, areaH};
    }

    private void smoothScrollToward(float target, boolean modules) {
        if (modules) {
            if (Math.abs(target - moduleScroll) < 0.15f) moduleScroll = target;
            else moduleScroll += (target - moduleScroll) * 0.22f;
        }
    }

    private void smoothThemesScroll(float target) {
        if (Math.abs(target - themesScroll) < 0.15f) themesScroll = target;
        else themesScroll += (target - themesScroll) * 0.22f;
    }

    private void smoothConfigScroll(float target) {
        if (Math.abs(target - configScroll) < 0.15f) configScroll = target;
        else configScroll += (target - configScroll) * 0.22f;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        animProgress = guiOpenAnim.getProgress();

        ClickGuiOpenEffects.reportBackdrop(animProgress);
        syncClientTheme();

        float dw = designW();
        float dh = designH();
        float cx = dw * 0.5f;
        float cy = dh * 0.5f;
        openEffectScale = ClickGuiOpenEffects.openScale(animProgress);
        openCamX = ClickGuiOpenEffects.cameraOffsetX(openYaw);
        openCamY = ClickGuiOpenEffects.cameraOffsetY(openPitch);


        Render2D.beginFrame(g);
        ClickGuiOpenEffects.renderDarkness(animProgress, dw, dh);
        Render2D.flush();

        float x = panelX();
        float y = panelY();


        CsColorPicker.get().setClampBounds(x + 4f, y + 4f, x + panelW() - 4f, y + panelH() - 4f);

        g.pose().pushMatrix();
        g.pose().translate(openCamX, openCamY);
        g.pose().translate(cx, cy);
        g.pose().scale(openEffectScale, openEffectScale);
        g.pose().translate(-cx, -cy);

        Render2D.beginFrame(g);
        renderBackground(g, x, y);
        renderMiniRects(x, y);
        renderCategoryTitle(x, y);
        renderSearchWidget(g, x, y);
        renderCategoryList(x, y);
        renderModules(g, x, y);


        if (CsColorPicker.get().isOpen()) {
            float smx = fixedMouseX(
                    mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
            float smy = fixedMouseY(
                    mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
            CsColorPicker.get().render(g, (int) smx, (int) smy);
        }

        Render2D.flush();
        g.pose().popMatrix();

        CsSettingComponent.tickSuppressVisibility();
    }


    private float mapMouseX(double fixedX) {
        return (float) ClickGuiOpenEffects.unprojectX(fixedX, openCamX, designW() * 0.5f, openEffectScale);
    }

    private float mapMouseY(double fixedY) {
        return (float) ClickGuiOpenEffects.unprojectY(fixedY, openCamY, designH() * 0.5f, openEffectScale);
    }

    private void renderBackground(GuiGraphics g, float x, float y) {
        float a = animProgress;
        float pw = panelW();
        float ph = panelH();
        float r = rounding();

        float blurR = 32f;
        try {
            blurR = Math.max(28f, HudTheme.BLUR_RADIUS);
        } catch (Throwable ignored) {
        }
        int bg = ColorUtil.rgba(3, 3, 5, Math.round(248 * a));

        if (Hud.isGlassMode()) {
            Hud.renderHudBackground(x, y, pw, ph, r, blurR, HudTheme.BLUR_SMOOTHNESS, bg);
        } else {
            Render2D.blur(x, y, pw, ph, r, blurR, 1.25f, bg);
        }

        Render2D.rect(x, y, pw, ph, r, ColorUtil.rgba(4, 4, 7, Math.round(190 * a)));
    }

    private void renderMiniRects(float x, float y) {
        float a = animProgress;
        float miniH = miniH();
        float headerH = headerH();
        float pw = panelW();
        float ph = panelH();
        float r = rounding();

        float inset = 0.85f;
        float ri = Math.max(0f, r - inset);


        int bottomBg = ColorUtil.rgba(0, 0, 0, Math.round(25 * a));
        Render2D.rect(x + inset, y + ph - miniH, pw - inset * 2f, miniH - inset,
                0f, 0f, ri, ri,
                bottomBg);


        int headerBg = ColorUtil.rgba(0, 0, 0, Math.round(85 * a));
        Render2D.rect(x + inset, y + inset, pw - inset * 2f, headerH - inset,
                ri, ri, 0f, 0f,
                headerBg);


        float bodyY = y + headerH;
        float bodyH = ph - headerH - miniH;
        int bodyBg = ColorUtil.rgba(0, 0, 0, Math.round(48 * a));
        Render2D.rect(x + inset, bodyY, pw - inset * 2f, bodyH,
                0f, 0f, 0f, 0f,
                bodyBg);


        float vPad = Math.max(r * 0.7f, 7f);
        float hPad = Math.max(r * 0.55f, 5f);
        int sep = ColorUtil.rgba(95, 97, 105, Math.round(SEPARATOR_A * a));
        Render2D.rect(x + hPad, y + headerH,
                Math.max(1f, pw - hPad * 2f), 1f, sep);
        Render2D.rect(x + hPad, y + ph - miniH,
                Math.max(1f, pw - hPad * 2f), 1f, sep);
    }

    private void renderCategoryTitle(float x, float y) {
        float headerH = headerH();
        float s = uiScale();
        String name = getSelectedCategoryName();

        float titleP = easeOutCubic(contentAnimProgress());
        float iconX = contentArea(x, y)[0] + (1f - titleP) * 10f * s;
        float iconSize = 11f * s;

        float midY = y + headerH * 0.5f + (1f - titleP) * 4f * s * contentEnterDir;
        float iconW = iconSize;
        String clientIcon = getSelectedClientIconPath();
        if (clientIcon == null && selectedCategory == AUTOBUY_CAT_INDEX) {
            clientIcon = CsMenuAssets.GRAB;
        }
        float titleAlpha = animProgress * titleP;
        if (clientIcon != null) {
            CsMenuAssets.icon(clientIcon, iconX, midY - iconSize * 0.5f, iconSize,
                    withAlpha(accentColor, alpha(titleAlpha)));
        } else {
            String icon = getSelectedCategoryIcon();
            iconW = Render2D.textWidth(FontType.MAINMENUSCREEN, icon, 9f * s);
            float iconNudge = ICON_NUDGE_Y * s;
            Render2D.text(FontType.MAINMENUSCREEN, icon, iconX, midY - 9f * s * 0.5f + iconNudge, 9f * s,
                    withAlpha(accentColor, alpha(titleAlpha)));
        }
        float textX = iconX + iconW + 6f * s;
        float nameSize = 11.5f * s;
        Render2D.text(FontType.INTER_SEMI, name, textX, centerTextY(midY, nameSize), nameSize,
                ColorUtil.rgba(240, 240, 245, alpha(titleAlpha)));
    }


    private float centerTextY(float middleY, float textSize) {
        return middleY - textSize * 0.5f + TEXT_OPTICAL_NUDGE * uiScale();
    }

    private void renderClock(float centerX, float centerY, float s) {
        float a = animProgress;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        String time = String.format(Locale.US, "%02d:%02d", hour, minute);

        float timeSize = 36f * s;
        float timeW = Render2D.textWidth(FontType.INTER_BOLD, time, timeSize);
        float timeX = centerX - timeW * 0.5f;
        float timeY = centerY - timeSize * 0.35f;
        Render2D.text(FontType.INTER_BOLD, time, timeX, timeY, timeSize,
                ColorUtil.rgba(240, 242, 248, alpha(a)));
    }

    private void renderDateRight(float x, float topY, float s) {
        float a = animProgress;

        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
        String monthName = monthNames[calendar.get(Calendar.MONTH)];

        String[] dayNames = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String dayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1];

        String dateStr = day + " " + monthName;

        // Дата на уровне верха часов
        float dateSize = 9f * s;
        float dateX = x;
        float dateY = topY;
        Render2D.text(FontType.INTER_MEDIUM, dateStr, dateX, dateY, dateSize,
                ColorUtil.rgba(200, 202, 210, alpha(a)));

        // День недели под датой
        float daySize = 8f * s;
        float dayX = x;
        float dayY = dateY + dateSize + 1f * s;
        Render2D.text(FontType.INTER_MEDIUM, dayName, dayX, dayY, daySize,
                ColorUtil.rgba(160, 162, 170, alpha(a)));
    }
    private void renderCategoryList(float sx, float sy) {
        catHitBoxes.clear();
        catRectPositions.clear();
        float s = uiScale();

        float miniH = miniH();
        float pw = panelW();
        float ph = panelH();

        float y = sy + ph - miniH;
        float itemStep = 48f * s;
        float fontSize = 10f * s;

        float padX = 8f * s;
        float startX = sx + padX;

        float cardW = 48f * s;
        float cardH = miniH - 14f * s;
        float cardX = sx + pw - cardW - padX;
        float cardY = y + 4f * s;

        float clipW = cardX - startX - 8f * s;
        float totalCatW = TOTAL_CATS * itemStep;
        float maxCatScroll = Math.max(0f, totalCatW - clipW);
        catTargetScroll = Math.max(0f, Math.min(catTargetScroll, maxCatScroll));
        catScroll += (catTargetScroll - catScroll) * 0.22f;

        Render2D.pushScissor(null, sx + 1f, y + 1f, pw - 2f, miniH - 2f);

        while (catColorAnims.size() < TOTAL_CATS)
            catColorAnims.add(new SimpleLinearAnimation(250));

        float x = startX - catScroll;
        int idx = 0;
        for (ModuleCategory cat : ModuleCategory.values()) {
            if (cat == ModuleCategory.AUTOBUY) {
                renderClientCategoryItemH(x, y, idx, cat.getDisplayName(),
                        CsMenuAssets.GRAB, fontSize, s);
            } else {
                renderCategoryItemH(x, y, idx, cat.getDisplayName(), cat.icon(), fontSize, s);
            }
            x += itemStep;
            idx++;
        }

        x += 8f * s;

        for (int i = 0; i < CLIENT_CATS.length; i++) {
            renderClientCategoryItemH(x, y, idx, CLIENT_CATS[i], CLIENT_CAT_ICON_PATHS[i], fontSize, s);
            x += itemStep;
            idx++;
        }

        Render2D.popScissor(null);
        float lastCatX = x - 8f * s;
        float clockAreaW = cardX - lastCatX;
        float clockCenterX = lastCatX + clockAreaW * 0.5f - 44f;
        float clockCenterY = y + miniH * 0.5f - 6f;
        renderClock(clockCenterX, clockCenterY, s);

        float dateAreaX = clockCenterX + 36f * s - 32f + 24f + 25;
        float dateAreaY = clockCenterY - 15f * s + 10f;
        renderDateRight(dateAreaX, dateAreaY, s);

        renderUserCardH(cardX, cardY, cardW, cardH);

        if (!catRectPositions.isEmpty() && selectedCategory >= 0 && selectedCategory < catRectPositions.size()) {
            float targetX = catRectPositions.get(selectedCategory);
            if (!rectPositionInitialized) {
                fromRectY = toRectY = targetX;
                rectPositionInitialized = true;
            } else if (Math.abs(toRectY - targetX) > 0.5f) {
                fromRectY = fromRectY + (toRectY - fromRectY) * rectSlideAnim.getProgress();
                toRectY = targetX;
                rectSlideAnim.show();
            }
        }
    }
    private void renderUserCardH(float cardX, float cardY, float cardW, float cardH) {
        float s = uiScale();
        float a = animProgress;
        float r = 7f * s;

        float toggleW = 38f * s;
        float toggleH = 14f * s;
        float toggleX = cardX - toggleW - 6f * s;
        float toggleY = cardY + (cardH - toggleH) * 0.5f;
        protectHitBox = new float[]{toggleX, toggleY, toggleW, toggleH};

        float protectP = protectAnim.getProgress();
        int fillOff = ColorUtil.rgba(255, 255, 255, Math.round(12 * a));
        int fillOn = withAlpha(accentColor, Math.round(180 * a));
        int fill = ColorUtil.lerpColor(fillOff, fillOn, protectP);
        Render2D.rect(toggleX, toggleY, toggleW, toggleH, toggleH * 0.5f, fill);
        Render2D.outline(toggleX, toggleY, toggleW, toggleH, toggleH * 0.5f, 0.85f,
                ColorUtil.lerpColor(
                        ColorUtil.rgba(255, 255, 255, Math.round(18 * a)),
                        withAlpha(accentColor, Math.round(140 * a)),
                        protectP));

        float pad = 2f;
        float kn = toggleH - pad * 2f;
        float travel = Math.max(0f, toggleW - pad * 2f - kn);
        float kx = toggleX + pad + travel * protectP;
        float ky = toggleY + pad;
        int knCol = ColorUtil.lerpColor(
                ColorUtil.rgba(175, 178, 188, alpha(a)),
                ColorUtil.rgba(255, 255, 255, alpha(a)),
                protectP);
        Render2D.rect(kx, ky, kn, kn, kn * 0.5f, knCol);

        float labelSize = 7f * s;
        String label = "Protect";
        float labelW = Render2D.textWidth(FontType.INTER_SEMI, label, labelSize);
        float labelX = toggleX + (toggleW - labelW) * 0.5f;
        float labelY = toggleY - labelSize - 2f * s;
        Render2D.text(FontType.INTER_SEMI, label, labelX, labelY, labelSize,
                ColorUtil.rgba(180, 182, 190, alpha(a)));

        Render2D.rect(cardX, cardY, cardW, cardH, r,
                ColorUtil.rgba(255, 255, 255, Math.round(10 * a)));
        Render2D.outline(cardX, cardY, cardW, cardH, r, 0.65f,
                ColorUtil.rgba(255, 255, 255, Math.round(16 * a)));

        float face = Math.min(22f * s, Math.min(cardW, cardH) - 8f * s);
        float faceX = cardX + (cardW - face) * 0.5f;
        float faceY = cardY + (cardH - face) * 0.5f - 2f * s;
        int faceCol = ColorUtil.rgba(255, 255, 255, alpha(a));

        String nick = "Player";
        try {
            if (mc.player != null) {
                nick = mc.player.getGameProfile().name();
                if (nick == null || nick.isEmpty()) {
                    nick = mc.player.getName().getString();
                }
                String texture = mc.player.getSkin().body().texturePath().toString();

                Render2D.imageUvNearest(texture, faceX, faceY, face, face, 3.5f * s, 1f,
                        8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, faceCol);
                Render2D.imageUvNearest(texture, faceX, faceY, face, face, 3.5f * s, 1f,
                        40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, faceCol);
            } else {
                Render2D.rect(faceX, faceY, face, face, 3.5f * s,
                        ColorUtil.rgba(60, 62, 70, Math.round(200 * a)));
                String letter = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.US);
                float letterSize = 9f * s;
                float lw = Render2D.textWidth(FontType.INTER_SEMI, letter, letterSize);
                Render2D.text(FontType.INTER_SEMI, letter,
                        faceX + (face - lw) * 0.5f, faceY + face * 0.28f, letterSize,
                        ColorUtil.rgba(220, 222, 230, alpha(a)));
            }
        } catch (Throwable t) {
            Render2D.rect(faceX, faceY, face, face, 3.5f * s,
                    ColorUtil.rgba(60, 62, 70, Math.round(200 * a)));
        }

        String displayNick = protectedMode ? "Protected" : nick;
        float nickSize = 7.5f * s;
        float nickW = Render2D.textWidth(FontType.INTER_SEMI, displayNick, nickSize);
        float nickX = cardX + (cardW - nickW) * 0.5f;
        float nickY = cardY + cardH + 1f * s;
        int nickColor = protectedMode
                ? withAlpha(accentColor, alpha(a))
                : ColorUtil.rgba(220, 222, 230, alpha(a));
        Render2D.text(FontType.INTER_SEMI, displayNick, nickX, nickY, nickSize, nickColor);
    }

    private void drawClippedText(FontType font, String text, float x, float y, float size, int color, float maxW) {
        if (text == null || text.isEmpty() || maxW <= 4f) return;
        float tw = Render2D.textWidth(font, text, size);
        if (tw <= maxW) {
            Render2D.text(font, text, x, y, size, color);
            return;
        }

        String ell = "...";
        float ew = Render2D.textWidth(font, ell, size);
        String cut = text;
        while (cut.length() > 1 && Render2D.textWidth(font, cut, size) + ew > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        Render2D.text(font, cut + ell, x, y, size, color);
    }

    private void renderCategoryItemH(float x, float y, int idx, String name, String icon, float fontSize, float s) {
        float itemW = 40f * s;
        float itemH = miniH() - 14f * s;
        float padY = 4f * s;
        float rectX = x;
        float rectY = y + padY;
        catRectPositions.add(rectX);
        catHitBoxes.add(new float[]{rectX, rectY, itemW, itemH});

        if (selectedCategory == idx) {
            float progress = rectSlideAnim.getProgress();
            float animX = fromRectY + (toRectY - fromRectY) * progress;
            Render2D.rect(animX, rectY, itemW, itemH, 7f,
                    ColorUtil.rgba(255, 255, 255, Math.round(12 * animProgress)));
            float barW = 12f * s;
            Render2D.rect(animX + (itemW - barW) * 0.5f, rectY + itemH - 1f, barW, 1.5f,
                    0f, 0f, 2.5f, 2.5f, withAlpha(accentColor, alpha(animProgress)));
        }

        float animP = catColorAnims.get(idx).getProgress();
        int grayColor = ColorUtil.rgba(130, 132, 140, alpha(animProgress));
        int iconColor = ColorUtil.lerpColor(grayColor, withAlpha(accentColor, alpha(animProgress)), animP);

        float iconSize = 13f * s;
        float iconW = Render2D.textWidth(FontType.MAINMENUSCREEN, icon, iconSize);
        float mid = rectX + itemW * 0.5f;
        float iconX = mid - iconW * 0.5f;
        float iconY = rectY + (itemH - iconSize) * 0.5f - 4f * s;
        float iconNudge = ICON_NUDGE_Y * s;
        Render2D.text(FontType.MAINMENUSCREEN, icon, iconX, iconY + iconNudge, iconSize, iconColor);

        float nameSize = 7f * s;
        float nameW = Render2D.textWidth(FontType.INTER_MEDIUM, name, nameSize);
        float nameX = mid - nameW * 0.5f;
        float nameY = rectY + itemH - nameSize - 3f * s;
        Render2D.text(FontType.INTER_MEDIUM, name, nameX, nameY, nameSize,
                ColorUtil.rgba(180, 182, 190, alpha(animProgress)));
    }

    private void renderClientCategoryItemH(float x, float y, int idx, String name, String iconPath, float fontSize, float s) {
        float itemW = 40f * s;
        float itemH = miniH() - 14f * s;
        float padY = 4f * s;
        float rectX = x;
        float rectY = y + padY;
        catRectPositions.add(rectX);
        catHitBoxes.add(new float[]{rectX, rectY, itemW, itemH});

        if (selectedCategory == idx) {
            float progress = rectSlideAnim.getProgress();
            float animX = fromRectY + (toRectY - fromRectY) * progress;
            Render2D.rect(animX, rectY, itemW, itemH, 7f,
                    ColorUtil.rgba(255, 255, 255, Math.round(12 * animProgress)));
            float barW = 12f * s;
            Render2D.rect(animX + (itemW - barW) * 0.5f, rectY + itemH - 1f, barW, 1.5f,
                    0f, 0f, 2.5f, 2.5f, withAlpha(accentColor, alpha(animProgress)));
        }

        float animP = catColorAnims.get(idx).getProgress();
        int grayColor = ColorUtil.rgba(130, 132, 140, alpha(animProgress));
        int iconColor = ColorUtil.lerpColor(grayColor, withAlpha(accentColor, alpha(animProgress)), animP);

        float iconSize = 14f * s;
        float mid = rectX + itemW * 0.5f;
        float iconX = mid - iconSize * 0.5f;
        float iconY = rectY + (itemH - iconSize) * 0.5f - 4f * s;
        CsMenuAssets.icon(iconPath, iconX, iconY, iconSize, iconColor);

        float nameSize = 7f * s;
        float nameW = Render2D.textWidth(FontType.INTER_MEDIUM, name, nameSize);
        float nameX = mid - nameW * 0.5f;
        float nameY = rectY + itemH - nameSize - 3f * s;
        Render2D.text(FontType.INTER_MEDIUM, name, nameX, nameY, nameSize,
                ColorUtil.rgba(180, 182, 190, alpha(animProgress)));
    }


    private float[] searchRect(float sx, float sy) {
        float s = uiScale();
        float h = SEARCH_H * s;
        float pad = 10f * s;
        float contentW = panelW() - pad * 2f;

        float maxW = Math.max(120f * s, contentW - SEARCH_SIDE_RESERVE * s * 2f - pad * 2f);
        float w = Math.min(maxW, Math.max(SEARCH_MIN_W * s, contentW * SEARCH_WIDTH_RATIO));
        float x = sx + (panelW() - w) * 0.5f;
        float y = sy + (headerH() - h) * 0.5f;
        return new float[]{x, y, w, h, SEARCH_ICON * s};
    }

    private void renderSearchWidget(GuiGraphics g, float sx, float sy) {
        float s = uiScale();
        float[] r = searchRect(sx, sy);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        float iconSize = r[4];
        float a = animProgress;
        float ep = searchExpandProgress();


        if (searchOpen) searchExpandAnim.show();
        else searchExpandAnim.hide();

        boolean focused = searchInput.isFocused();
        int fill = ColorUtil.rgba(255, 255, 255, Math.round((9 + 6 * ep) * a));
        int border = focused
                ? withAlpha(accentColor, Math.round(150 * a))
                : ColorUtil.rgba(255, 255, 255, Math.round((18 + 10 * ep) * a));
        Render2D.rect(x, y, w, h, SEARCH_ROUND, fill);
        Render2D.outline(x, y, w, h, SEARCH_ROUND, 0.85f, border);

        float iconX = x + SEARCH_PAD * s;
        float iconY = y + (h - iconSize) * 0.5f;
        CsMenuAssets.icon(CsMenuAssets.SEARCH, iconX, iconY, iconSize,
                withAlpha(focused ? accentColor : ColorUtil.rgba(150, 154, 165, 255), Math.round(255 * a)));

        float textPadL = SEARCH_PAD * s + iconSize + 4f * s;
        float textX = x + textPadL;
        float textSize = 8f * s;

        if (!searchOpen) {




            float ty = y + (h - textSize) * 0.5f;
            float hintW = Render2D.textWidth(FontType.INTER_MEDIUM, SEARCH_HINT, textSize);
            float hintX = x + (w - hintW) * 0.5f;
            Render2D.text(FontType.INTER_MEDIUM, SEARCH_HINT, hintX, ty, textSize,
                    ColorUtil.rgba(128, 132, 143, Math.round(215 * a)));
            return;
        }


        float closeSz = 8f * s;
        float closeX = x + w - closeSz - SEARCH_PAD * s;
        CsMenuAssets.icon(CsMenuAssets.CROSS, closeX, y + (h - closeSz) * 0.5f, closeSz,
                ColorUtil.rgba(170, 100, 100, Math.round(220 * a)));

        float textPadR = closeSz + SEARCH_PAD * s * 2f;
        searchInput.setTextSize(textSize);
        searchInput.setPlaceholder("");
        searchInput.setPosition(textX, y);
        searchInput.setSize(Math.max(12f, w - textPadL - textPadR), h);
        searchInput.render(g);


    }

    private String settingTypeIcon(Setting<?> s) {
        if (s == null) return CsMenuAssets.FRAME;
        return switch (s.getType()) {
            case BOOLEAN -> CsMenuAssets.CHECKMARK;
            case NUMBER -> CsMenuAssets.SLIDER;
            case MODE -> CsMenuAssets.ENUM;
            case MULTI_MODE -> CsMenuAssets.MULTI_ENUM;
            case BIND -> CsMenuAssets.KEYBOARD;
            case STRING -> CsMenuAssets.TEXT;
            case COLOR -> CsMenuAssets.BRUSH;
            case BUTTON -> CsMenuAssets.CLICK;
        };
    }

    private boolean handleSearchClicked(double mx, double my, int btn) {
        float sx = panelX();
        float sy = panelY();
        float s = uiScale();
        float[] r = searchRect(sx, sy);
        float x = r[0], y = r[1], w = r[2], h = r[3];
        float iconSize = r[4];
        boolean inside = mx >= x && mx <= x + w && my >= y && my <= y + h;

        if (!searchOpen) {
            if (btn == 0 && inside) {
                openSearch();
                return true;
            }
            return false;
        }


        float closeSz = 7f * s;
        float closeX = x + w - closeSz - SEARCH_PAD * s;
        if (btn == 0 && inside && mx >= closeX - 4f * s) {
            closeSearch();
            return true;
        }


        String query = searchInput.getText();
        if (query != null && !query.isBlank() && btn == 0) {
            float[] area = contentArea(sx, sy);
            float contentX = area[0], contentY = area[1], contentW = area[2], contentH = area[3];
            float rowH = 30f * s;
            float listY = contentY + 18f * s;
            List<SearchHit> hits = searchAll(query);
            int max = Math.min(hits.size(), (int) ((contentH - 20f * s) / rowH) + 2);
            for (int i = 0; i < max && i < hits.size(); i++) {
                float ry = listY + i * rowH;
                if (mx >= contentX && mx <= contentX + contentW && my >= ry && my <= ry + rowH - 2f * s) {
                    navigateToHit(hits.get(i));
                    return true;
                }
            }
        }

        if (inside) {
            float textPadL = SEARCH_PAD * s + iconSize + 4f * s;
            float textPadR = closeSz + SEARCH_PAD * s * 2f;
            searchInput.setPosition(x + textPadL, y);
            searchInput.setSize(Math.max(12f, w - textPadL - textPadR), h);
            boolean handled = searchInput.mouseClicked(mx, my, btn);
            if (!handled) searchInput.setFocused(true);
            return true;
        }


        if (btn == 0) {
            closeSearch();
            return false;
        }
        return false;
    }

    private void navigateToHit(SearchHit hit) {
        Module m = hit.module;
        ModuleCategory cat = m.getCategory();
        if (cat != null) {
            ModuleCategory[] cats = ModuleCategory.values();
            int idx = -1;
            for (int c = 0; c < cats.length; c++) {
                if (cats[c] == cat) { idx = c; break; }
            }
            if (idx >= 0) {
                int prev = selectedCategory;
                selectedCategory = idx;
                if (prev != idx) {
                    onCategoryChanged(prev, idx);
                    for (int j = 0; j < catColorAnims.size(); j++) {
                        if (j == idx) catColorAnims.get(j).show();
                        else catColorAnims.get(j).hide();
                    }
                }
            }
        }


        focusModule = m;
        focusScrollDone = false;
        focusWaitFrames = 0;
        focusFlashUntilMs = System.currentTimeMillis() + 1600L;
        cancelPendingModuleScroll();


        closeSearch();
    }

    private List<SearchHit> searchAll(String query) {
        if (mm == null || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String lq = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        List<SearchHit> out = new ArrayList<>();
        for (Module m : mm.getModules()) {
            String n = m.getName().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            boolean exact = n.equals(lq);
            boolean starts = n.startsWith(lq);
            boolean contains = n.contains(lq);
            if (exact || starts || contains) {
                out.add(new SearchHit(m, null, exact, starts));
            }
            for (Setting<?> s : m.getSettings()) {
                if (s.getName() == null) continue;
                String sn = s.getName().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                String sd = s.getDescription() == null ? "" : s.getDescription().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                boolean se = sn.equals(lq);
                boolean ss = sn.startsWith(lq);
                boolean sc = sn.contains(lq) || sd.contains(lq);
                if (se || ss || sc) {
                    out.add(new SearchHit(m, s, se, ss));
                }
            }
        }
        out.sort((a, b) -> {
            if (a.exact != b.exact) return a.exact ? -1 : 1;
            if (a.startsWith != b.startsWith) return a.startsWith ? -1 : 1;

            if (a.isSetting() != b.isSetting()) return a.isSetting() ? 1 : -1;
            String an = a.isSetting() ? a.setting.getName() : a.module.getName();
            String bn = b.isSetting() ? b.setting.getName() : b.module.getName();
            return an.compareToIgnoreCase(bn);
        });
        return out;
    }

    private List<Module> searchModules(String query) {

        return searchAll(query).stream()
                .map(h -> h.module)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Module> getModulesToRender() {

        if (searchOpen) {
            return Collections.emptyList();
        }
        if (selectedCategory >= ModuleCategory.values().length) return Collections.emptyList();
        ModuleCategory cat = ModuleCategory.values()[selectedCategory];
        return mm != null ? mm.getByCategory(cat) : Collections.emptyList();
    }


    private void renderSearchTab(GuiGraphics g, float sx, float sy) {
        float s = uiScale();
        float[] area = contentArea(sx, sy);
        float fade = contentAnimProgress() * searchExpandProgress();
        float a = animProgress * Math.max(0.2f, fade);
        float contentX = area[0];
        float contentY = area[1];
        float contentW = area[2];
        float contentH = area[3];


        Render2D.rect(contentX, contentY, contentW, contentH, 8f,
                ColorUtil.rgba(255, 255, 255, Math.round(5 * a)));

        String q = searchInput.getText();
        float titleSz = 10f * s;
        Render2D.text(FontType.INTER_SEMI, "Search", contentX + 4f * s, contentY + 2f * s, titleSz,
                ColorUtil.rgba(200, 204, 214, Math.round(255 * a)));
        String hint = (q == null || q.isBlank()) ? "Type to search modules & settings…" : "";
        if (!hint.isEmpty()) {
            Render2D.text(FontType.INTER_MEDIUM, hint, contentX + 4f * s, contentY + 16f * s, 8f * s,
                    ColorUtil.rgba(110, 114, 124, Math.round(220 * a)));
            return;
        }

        List<SearchHit> hits = searchAll(q);
        if (hits.isEmpty()) {
            Render2D.text(FontType.INTER_SEMI, "No results", contentX + 4f * s, contentY + 22f * s, 9f * s,
                    ColorUtil.rgba(140, 144, 155, Math.round(255 * a)));
            return;
        }

        float rowH = 30f * s;
        float listY = contentY + 18f * s;
        float listH = contentH - 20f * s;
        Render2D.pushScissor(g, contentX, listY, contentW, listH);

        int max = Math.min(hits.size(), (int) (listH / rowH) + 2);
        for (int i = 0; i < max && i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            float ry = listY + i * rowH;
            if (ry + rowH < listY || ry > listY + listH) continue;

            boolean settingHit = hit.isSetting();
            Render2D.rect(contentX, ry, contentW, rowH - 2f * s, 6f,
                    ColorUtil.rgba(255, 255, 255, Math.round(6 * a)));

            String icon = settingHit ? settingTypeIcon(hit.setting) : CsMenuAssets.FRAME;
            CsMenuAssets.icon(icon, contentX + 6f * s, ry + (rowH - 12f * s) * 0.45f, 12f * s,
                    withAlpha(ColorUtil.rgba(160, 164, 175, 255), Math.round(255 * a)));

            String name = settingHit ? hit.setting.getName() : hit.module.getName();
            float nameSz = 9.5f * s;
            float nameX = contentX + 24f * s;
            drawClippedText(FontType.INTER_SEMI, name, nameX, ry + 4f * s, nameSz,
                    ColorUtil.rgba(232, 234, 240, Math.round(255 * a)), contentW - 90f * s);

            String badge = settingHit ? "Setting" : "Module";
            float badgeSz = 7f * s;
            float badgeW = Render2D.textWidth(FontType.INTER_SEMI, badge, badgeSz) + 8f * s;
            float badgeH = 12f * s;
            float badgeX = contentX + contentW - badgeW - 6f * s;
            Render2D.rect(badgeX, ry + 4f * s, badgeW, badgeH, 4f,
                    withAlpha(accentColor, Math.round(45 * a)));
            float bw = Render2D.textWidth(FontType.INTER_SEMI, badge, badgeSz);
            Render2D.text(FontType.INTER_SEMI, badge, badgeX + (badgeW - bw) * 0.5f, ry + 5.5f * s, badgeSz,
                    withAlpha(accentColor, Math.round(230 * a)));

            String path = settingHit
                    ? hit.module.getCategory().getDisplayName() + "  ›  " + hit.module.getName()
                    : hit.module.getCategory().getDisplayName();
            drawClippedText(FontType.INTER_MEDIUM, path, nameX, ry + 16f * s, 7.5f * s,
                    ColorUtil.rgba(120, 124, 135, Math.round(255 * a)), contentW - 40f * s);
        }
        Render2D.popScissor(g);
    }


    private void drawToggleSwitch(float x, float y, float w, float h, float progress, float alphaMul) {
        int a = alpha(alphaMul);
        int fillOff = ColorUtil.rgba(255, 255, 255, Math.round(12 * alphaMul));
        int fillOn = withAlpha(accentColor, Math.round(220 * alphaMul));
        int fill = ColorUtil.lerpColor(fillOff, fillOn, progress);
        int outlineOff = ColorUtil.rgba(255, 255, 255, Math.round(18 * alphaMul));
        int outlineOn = withAlpha(accentColor, Math.round(160 * alphaMul));
        int outline = ColorUtil.lerpColor(outlineOff, outlineOn, progress);

        float r = h * 0.5f;
        Render2D.rect(x, y, w, h, r, fill);
        Render2D.outline(x, y, w, h, r, 0.85f, outline);

        float pad = 2f;
        float kn = h - pad * 2f;
        float travel = Math.max(0f, w - pad * 2f - kn);
        float kx = x + pad + travel * progress;
        float ky = y + pad;
        int knOff = ColorUtil.rgba(175, 178, 188, a);
        int knOn = ColorUtil.rgba(255, 255, 255, a);
        int knCol = ColorUtil.lerpColor(knOff, knOn, progress);
        Render2D.rect(kx, ky, kn, kn, kn * 0.5f, knCol);
    }

    private CsSettingComponent<?> getOrCreateSettingComp(Module mod, Setting<?> set, float width) {
        Map<Setting<?>, CsSettingComponent<?>> comps = moduleSettingComps.computeIfAbsent(mod, k -> new HashMap<>());
        CsSettingComponent<?> comp = comps.get(set);
        if (comp == null) {
            comp = CsSettingComponent.create(set, width);
            comps.put(set, comp);
        } else {
            comp.setWidth(width);
        }
        return comp;
    }

    private void updateSettingVisibility(Module mod) {
        for (Setting<?> s : mod.getSettings()) {
            CsSettingComponent<?> comp = getOrCreateSettingComp(mod, s, 100f);
            comp.updateVisibility(s.isVisible());
        }
    }

    private boolean hasRenderableSettings(Module mod) {
        for (Setting<?> s : mod.getSettings()) {
            CsSettingComponent<?> comp = getOrCreateSettingComp(mod, s, 100f);
            if (comp.getVisibilityAlpha() > 0.01f) return true;
        }
        return false;
    }

    private float getSettingsHeight(Module mod, float width) {
        float s = uiScale();
        float innerW = width - SETTINGS_SIDE_INSET * s * 2f;
        float total = 0f;
        for (Setting<?> setting : mod.getSettings()) {
            CsSettingComponent<?> comp = getOrCreateSettingComp(mod, setting, innerW);
            float va = comp.getVisibilityAlpha();
            if (va <= 0.01f) continue;
            float h = comp.getHeight() * va;
            if (h <= 0.01f) continue;
            total += h + SETTINGS_GAP * s * va;
        }
        if (total > 0f) total += SETTINGS_BOTTOM_PADDING * s;
        return total;
    }

    private void renderSettingComps(GuiGraphics g, Module mod, float mx, float my, float width, int mouseX, int mouseY, int alpha) {
        float s = uiScale();
        float sy = my;
        float innerW = width - SETTINGS_SIDE_INSET * s * 2f;
        float innerX = mx + SETTINGS_SIDE_INSET * s;
        for (Setting<?> setting : mod.getSettings()) {
            CsSettingComponent<?> comp = getOrCreateSettingComp(mod, setting, innerW);
            float va = comp.getVisibilityAlpha();
            if (va <= 0.001f) continue;
            int compAlpha = (int) (alpha * va);

            comp.draw(innerX, sy, mouseX, mouseY, compAlpha);
            sy += comp.getHeight() * va + SETTINGS_GAP * s * va;
        }
    }

    private boolean handleSettingClicked(Module mod, double mouseX, double mouseY, int button, float mx, float my, float width) {
        float s = uiScale();
        float sy = my;
        float innerW = width - SETTINGS_SIDE_INSET * s * 2f;
        float innerX = mx + SETTINGS_SIDE_INSET * s;

        for (Setting<?> setting : mod.getSettings()) {
            CsSettingComponent<?> comp = getOrCreateSettingComp(mod, setting, innerW);
            float va = comp.getVisibilityAlpha();
            if (va <= 0.01f) continue;
            float sh = comp.getHeight() * va;
            if (va >= 0.35f && mouseX >= innerX && mouseX <= innerX + innerW && mouseY >= sy && mouseY <= sy + sh) {

                comp.mouseClicked(mouseX, mouseY, button, innerX, sy);
                return true;
            }
            sy += sh + SETTINGS_GAP * s * va;
        }
        return false;
    }

    private void renderModules(GuiGraphics g, float sx, float sy) {

        float s = uiScale();
        CsSettingComponent.setLayoutScale(s);



        if (searchOpen || selectedCategory >= ModuleCategory.values().length
                || selectedCategory == AUTOBUY_CAT_INDEX) {
            sbModules.clear();
        }


        if (searchOpen) {
            renderSearchTab(g, sx, sy);
            return;
        }

        String q = "";
        if (!q.equals(prevSearchQuery)) {
            moduleScroll = moduleTargetScroll = 0f;
            prevSearchQuery = q;
        }

        List<Module> mods = getModulesToRender();
        if (selectedCategory == THEMES_CAT_INDEX) {
            renderThemesPanel(g, sx, sy);
            return;
        }
        if (selectedCategory == CONFIG_CAT_INDEX) {
            renderConfigsPanel(g, sx, sy);
            return;
        }
        if (selectedCategory == COSMETICS_CAT_INDEX) {
            renderCosmeticsPanel(g, sx, sy);
            return;
        }
        if (selectedCategory == AUTOBUY_CAT_INDEX) {
            renderAutoBuyPanel(g, sx, sy);
            return;
        }
        if (mods.isEmpty()) return;

        float[] area = contentArea(sx, sy);
        float slide = contentSlideOffset();
        float fade = contentAnimProgress();
        float contentX = area[0];
        float contentY = area[1] + slide;
        float contentW = area[2];
        float contentH = area[3];
        float moduleGap = MODULE_GAP_X * s;
        float moduleW = (contentW - moduleGap * (MODULE_COLUMNS - 1)) / MODULE_COLUMNS;
        float baseModuleH = BASE_MODULE_H * s;
        float moduleGapY = MODULE_GAP_Y * s;
        float headerToSettings = HEADER_TO_SETTINGS_GAP * s;

        float[] heights = new float[mods.size()];
        for (int i = 0; i < mods.size(); i++) {
            updateSettingVisibility(mods.get(i));
            float sh = getSettingsHeight(mods.get(i), moduleW);

            heights[i] = sh > 0f ? baseModuleH + headerToSettings + sh : baseModuleH;
        }

        float[] colYs = new float[MODULE_COLUMNS];
        java.util.Arrays.fill(colYs, contentY);
        float focusOffset = -1f;
        for (int i = 0; i < mods.size(); i++) {
            int col = pickColumn(colYs);
            if (mods.get(i) == focusModule) {

                focusOffset = colYs[col] - contentY;
            }
            colYs[col] += heights[i] + moduleGapY;
        }
        float totalContentH = maxColumnY(colYs) - contentY;
        if (mods.size() > 0) totalContentH = Math.max(0f, totalContentH - moduleGapY);
        float maxScroll = Math.max(0f, totalContentH - contentH);

        if (focusModule != null && !focusScrollDone) {
            if (focusOffset < 0f) {
                focusWaitFrames++;
                if (focusWaitFrames > FOCUS_MAX_WAIT) {
                    focusModule = null;
                }
            } else {

                moduleTargetScroll = moduleScroll =
                        Math.max(0f, Math.min(maxScroll, focusOffset - 10f * s));
                focusScrollDone = true;
            }
        }
        if (pendingModuleScrollFrames > 0) {
            pendingModuleScrollFrames--;
            moduleTargetScroll = moduleScroll = Math.max(0f, Math.min(pendingModuleScroll, maxScroll));
            if (pendingModuleScrollFrames == 0) {
                pendingModuleScroll = -1f;
            }
        }
        moduleTargetScroll = Math.max(0f, Math.min(moduleTargetScroll, maxScroll));
        smoothScrollToward(moduleTargetScroll, true);

        boolean appearEnabled = ClickGuiOpenEffects.moduleAppearEnabled();
        int[] rowInColumn = new int[MODULE_COLUMNS];


        if (draggingScrollbar == SB_MODULES && maxScroll > 0f) {
            cancelPendingModuleScroll();
            float fmy = fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
            float rel = (fmy - sbDragTrackY - sbDragOffset) / Math.max(1f, sbDragTrackH - sbDragThumbH);
            moduleTargetScroll = moduleScroll = Math.max(0f, Math.min(maxScroll, rel * maxScroll));
        }

        Render2D.pushScissor(g, contentX, contentY, contentW, contentH);
        renderScrollbar(sbModules, contentX + contentW + SCROLLBAR_GAP, contentY, SCROLLBAR_W, contentH, moduleScroll, maxScroll);

        float baseMa = animProgress * Math.max(0.15f, fade);

        float smx = fixedMouseX(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        float smy = fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());

        float nameSize = 9.5f * s;


        float packBaseY = area[1] + slide;
        java.util.Arrays.fill(colYs, packBaseY - moduleScroll);
        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            int col = pickColumn(colYs);
            int row = rowInColumn[col]++;
            float h = heights[i];
            float mx = contentX + col * (moduleW + moduleGap);
            float my = colYs[col];
            colYs[col] += h + moduleGapY;

            if (my + h < contentY - 24f || my > contentY + contentH + 24f) {
                continue;
            }


            float appear = 1f;
            float unfold = 1f;
            if (appearEnabled) {
                float delay = Math.min(APPEAR_DELAY_CAP, col * APPEAR_COL_DELAY + row * APPEAR_ROW_DELAY);
                float local = Math.max(0f, Math.min(1f, (fade - delay) / Math.max(0.12f, 1f - delay)));
                if (local <= 0.001f) {
                    continue;
                }

                float headerPhase = Math.min(1f, local / APPEAR_UNFOLD_SPLIT);
                appear = easeOutCubic(headerPhase);
                unfold = local <= APPEAR_UNFOLD_SPLIT
                        ? 0f
                        : easeOutCubic((local - APPEAR_UNFOLD_SPLIT) / (1f - APPEAR_UNFOLD_SPLIT));
                my += (1f - appear) * APPEAR_SLIDE * s * contentEnterDir;
                mx += (1f - appear) * 6f * s;
            }
            float ma = baseMa * appear;

            float drawH = h <= baseModuleH ? h : baseModuleH + (h - baseModuleH) * unfold;

            if (mod == focusModule) {
                long left = focusFlashUntilMs - System.currentTimeMillis();
                if (left <= 0L) {
                    focusModule = null;
                } else {


                    float life = Math.min(1f, left / 1600f);
                    float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 130.0);
                    float strength = life * pulse * ma;

                    for (int ring = 3; ring >= 1; ring--) {
                        float spread = ring * 2.0f * s;
                        Render2D.rect(mx - spread, my - spread, moduleW + spread * 2f, drawH + spread * 2f,
                                7f + spread, withAlpha(accentColor, Math.round(20f * strength / ring)));
                    }

                    Render2D.rect(mx, my, moduleW, drawH, 7f,
                            withAlpha(accentColor, Math.round(26 * strength)));
                    Render2D.outline(mx, my, moduleW, drawH, 7f, 1.2f,
                            withAlpha(accentColor, Math.round(220 * strength)));
                }
            }

            SimpleLinearAnimation toggleAnim = moduleToggleAnims.computeIfAbsent(mod, k -> new SimpleLinearAnimation(220));
            if (mod.isEnabled()) toggleAnim.show(); else toggleAnim.hide();
            float toggleP = toggleAnim.getProgress();
            int nameColor = ColorUtil.lerpColor(
                    ColorUtil.rgba(155, 158, 168, alpha(ma)),
                    ColorUtil.rgba(240, 242, 248, alpha(ma)),
                    0.20f + 0.80f * toggleP);


            int cardFill = ColorUtil.rgba(255, 255, 255, Math.round((7 + 5 * toggleP) * ma));
            Render2D.rect(mx, my, moduleW, drawH, 7f, cardFill);
            Render2D.outline(mx, my, moduleW, drawH, 7f, 1.0f,
                    ColorUtil.lerpColor(
                            ColorUtil.rgba(255, 255, 255, Math.round(12 * ma)),
                            withAlpha(accentColor, Math.round(50 * ma)),
                            toggleP * 0.4f));

            float headerMid = my + baseModuleH * 0.5f;

            float nameX = mx + CARD_PAD * s;


            float tw = TOGGLE_W * s * 0.92f;
            float th = TOGGLE_H * s * 0.92f;
            float toggleX = mx + moduleW - tw - CARD_PAD * s;
            float toggleY = headerMid - th * 0.5f;
            drawToggleSwitch(toggleX, toggleY, tw, th, toggleP, ma);


            boolean binding = bindingModule == mod;
            String bindRaw = mod.getBind() != null ? mod.getBind().getDisplayName() : "";
            boolean hasBind = bindRaw != null && !bindRaw.isEmpty() && !bindRaw.equalsIgnoreCase("NONE");
            float bindReserve = tw + 16f * s;
            if (binding || hasBind) {
                String bind = binding ? "..." : bindRaw;
                float keySize = 8f * s;
                float boxH = 14f * s;
                float btw = Render2D.textWidth(FontType.INTER_SEMI, bind, keySize);
                float boxW = Math.max(boxH, btw + 8f * s);
                float bindX = toggleX - boxW - 6f * s;
                float bindY = headerMid - boxH * 0.5f;
                bindReserve = (mx + moduleW - bindX) + 8f * s;
                int keyBg = binding
                        ? withAlpha(accentColor, alpha(ma))
                        : ColorUtil.rgba(255, 255, 255, Math.round(12 * ma));
                Render2D.rect(bindX, bindY, boxW, boxH, 4f * s, keyBg);
                Render2D.outline(bindX, bindY, boxW, boxH, 4f * s, 0.9f,
                        ColorUtil.rgba(255, 255, 255, Math.round((binding ? 45 : 18) * ma)));
                Render2D.text(FontType.INTER_SEMI, bind,
                        bindX + (boxW - btw) * 0.5f,
                        bindY + (boxH - keySize) * 0.5f,
                        keySize,
                        ColorUtil.rgba(240, 242, 248, alpha(ma)));
            }


            float nameMaxW = Math.max(8f * s, moduleW - (nameX - mx) - bindReserve);
            drawClippedText(FontType.INTER_SEMI, mod.getName(), nameX, centerTextY(headerMid, nameSize), nameSize, nameColor, nameMaxW);

            if (h > baseModuleH + 2f && unfold > 0.02f) {

                Render2D.rect(mx + CARD_PAD * s, my + baseModuleH - 0.5f, moduleW - CARD_PAD * s * 2f, 0.8f,
                        ColorUtil.rgba(255, 255, 255, Math.round(10 * ma * unfold)));
            }


            if (hasRenderableSettings(mod) && unfold >= 0.999f) {
                float settingsStartY = my + baseModuleH + headerToSettings;
                renderSettingComps(g, mod, mx, settingsStartY, moduleW, (int) smx, (int) smy, alpha(ma));
            }
        }

        Render2D.popScissor(g);


        if (sbModules.visible) {
            int trackCol = ColorUtil.rgba(255, 255, 255, Math.round(10 * animProgress));
            int thumbCol = ColorUtil.rgba(255, 255, 255, Math.round(45 * animProgress));
            Render2D.rect(sbModules.trackX, sbModules.trackY, sbModules.trackW, sbModules.trackH, sbModules.trackW / 2f, trackCol);
            Render2D.rect(sbModules.trackX, sbModules.thumbY, sbModules.trackW, sbModules.thumbH, sbModules.trackW / 2f, thumbCol);
        }
    }

    private void renderScrollbar(ScrollbarGeometry sb, float tx, float ty, float tw, float th, float scroll, float maxScroll) {
        if (maxScroll <= 0.5f) { sb.clear(); return; }
        float thumbH = Math.max(24f, th * (th / (th + maxScroll)));
        float travel = Math.max(1f, th - thumbH);
        float thumbY = ty + (scroll / maxScroll) * travel;
        sb.set(tx, ty, tw, th, thumbY, thumbH, th + maxScroll, th);

        int trackCol = ColorUtil.rgba(255, 255, 255, Math.round(10 * animProgress));
        int thumbCol = ColorUtil.rgba(255, 255, 255, Math.round(40 * animProgress));
        Render2D.rect(tx, ty, tw, th, tw / 2f, trackCol);
        Render2D.rect(tx, thumbY, tw, thumbH, tw / 2f, thumbCol);
    }

    private void renderThemesPanel(GuiGraphics g, float sx, float sy) {
        float[] area = contentArea(sx, sy);
        float slide = contentSlideOffset();
        float fade = contentAnimProgress();
        float contentX = area[0];
        float contentY = area[1] + slide;
        float contentW = area[2];
        float contentH = area[3];

        int mx = (int) fixedMouseX(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int my = (int) fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());


        themeSettings.render(g, contentX, contentY, contentW, contentH, animProgress * Math.max(0.2f, fade), uiScale(), mx, my);
    }

    private void renderConfigsPanel(GuiGraphics g, float sx, float sy) {
        float[] area = contentArea(sx, sy);
        float slide = contentSlideOffset();
        float fade = contentAnimProgress();
        float contentX = area[0];
        float contentY = area[1] + slide;
        float contentW = area[2];
        float contentH = area[3];

        int mx = (int) fixedMouseX(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int my = (int) fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());

        configPanel.render(contentX, contentY, contentW, contentH, animProgress * Math.max(0.2f, fade), uiScale(), accentColor, mx, my);
    }

    private void renderCosmeticsPanel(GuiGraphics g, float sx, float sy) {
        float[] area = contentArea(sx, sy);
        float slide = contentSlideOffset();
        float fade = contentAnimProgress();
        int mx = (int) fixedMouseX(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int my = (int) fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());
        cosmeticsPanel.render(g, area[0], area[1] + slide, area[2], area[3],
                animProgress * Math.max(0.2f, fade), uiScale(), accentColor, mx, my);
    }

    private void renderAutoBuyPanel(GuiGraphics g, float sx, float sy) {
        float[] area = contentArea(sx, sy);
        float slide = contentSlideOffset();
        float fade = contentAnimProgress();
        float contentX = area[0];
        float contentY = area[1] + slide;


        float contentW = area[2] + SCROLLBAR_W + SCROLLBAR_GAP;
        float contentH = area[3];

        int mx = (int) fixedMouseX(mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth());
        int my = (int) fixedMouseY(mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight());


        try {
            polaris.utils.render.item.RenderItem.beginGuiFrame();
        } catch (Throwable ignored) {
        }
        autoBuyPanel.render(g, contentX, contentY, contentW, contentH, animProgress * Math.max(0.2f, fade), uiScale(), accentColor, mx, my);
    }

    private void saveNewConfig() {
        configPanel.savePreset();
    }


    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int scan = event.scancode();
        int mods = event.modifiers();


        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindingModule.setBind(KeyBind.NONE);
            } else {
                bindingModule.setBind(KeyBind.keyboard(key));
            }
            bindingModule = null;
            return true;
        }


        if (CsSettingComponent.listeningBindComp instanceof CsBindSetting bindSetting) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindSetting.applyBind(KeyBind.NONE);
            } else {
                bindSetting.applyBind(KeyBind.keyboard(key));
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searchOpen) { closeSearch(); return true; }
            if (configNameInput.isFocused()) { configNameInput.setFocused(false); return true; }
            if (autoBuyPanel.isSearchFocused()) { autoBuyPanel.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); return true; }
            mc.setScreen(null);
            return true;
        }

        if (searchOpen && searchInput.isFocused()) return searchInput.keyPressed(key, scan, mods);
        if (configNameInput.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER) { saveNewConfig(); return true; }
            return configNameInput.keyPressed(key, scan, mods);
        }
        if (selectedCategory == AUTOBUY_CAT_INDEX) {
            if (autoBuyPanel.keyPressed(key, scan, mods)) return true;
        }

        if (CsColorPicker.get().isOpen()) {
            CsColorPicker.get().keyPressed(key, scan, mods);
            return true;
        }
        if (CsStringSetting.editing != null && CsStringSetting.editing.keyPressed(key, scan, mods))
            return true;

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char code = (char) event.codepoint();

        if (searchOpen && searchInput.isFocused()) return searchInput.charTyped(code, 0);
        if (configNameInput.isFocused()) return configNameInput.charTyped(code, 0);
        if (selectedCategory == AUTOBUY_CAT_INDEX) {
            if (autoBuyPanel.charTyped(code, 0)) return true;
        }

        if (CsColorPicker.get().isOpen()) {
            CsColorPicker.get().charTyped(code, 0);
            return true;
        }
        if (CsStringSetting.editing != null && CsStringSetting.editing.charTyped(code, 0))
            return true;

        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = fixedMouseX(event.x());
        double my = fixedMouseY(event.y());
        int btn = event.button();


        boolean wasSearchOpen = searchOpen;

        if (btn == 0 && protectHitBox != null) {
            if (mx >= protectHitBox[0] && mx <= protectHitBox[0] + protectHitBox[2]
                    && my >= protectHitBox[1] && my <= protectHitBox[1] + protectHitBox[3]) {
                protectedMode = !protectedMode;
                if (protectedMode) protectAnim.show(); else protectAnim.hide();
                return true;
            }
        }


        if (bindingModule != null) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                bindingModule = null;
            } else {
                bindingModule.setBind(KeyBind.mouse(btn));
                bindingModule = null;
            }
            return true;
        }

        if (CsSettingComponent.listeningBindComp instanceof CsBindSetting bindSetting) {
            bindSetting.mouseClicked(mx, my, btn, 0, 0);
            return true;
        }

        if (CsColorPicker.get().isOpen() && CsColorPicker.get().mouseClicked(mx, my, btn)) return true;

        if (handleSearchClicked(mx, my, btn)) return true;

        float sx = panelX();
        float sy = panelY();


        if (btn == 0) {
            if (sbModules.hitThumb(mx, my) || sbModules.hitTrack(mx, my)) {
                draggingScrollbar = SB_MODULES;
                sbDragTrackY = sbModules.trackY;
                sbDragTrackH = sbModules.trackH;
                sbDragThumbH = sbModules.thumbH;
                sbDragOffset = (float) (my - sbModules.thumbY);
                if (sbModules.hitTrack(mx, my) && !sbModules.hitThumb(mx, my)) {
                    float rel = ((float) my - sbModules.trackY - sbModules.thumbH / 2f)
                            / Math.max(1f, sbModules.trackH - sbModules.thumbH);
                    float maxScroll = Math.max(0f, sbModules.totalContentH - sbModules.viewportH);
                    moduleTargetScroll = moduleScroll = Math.max(0f, Math.min(maxScroll, rel * maxScroll));
                    sbDragOffset = sbModules.thumbH / 2f;
                }
                return true;
            }
        }

        if (btn == 0) {
            for (int i = 0; i < catHitBoxes.size(); i++) {
                float[] b = catHitBoxes.get(i);
                if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                    if (selectedCategory != i) {
                        int prev = selectedCategory;
                        selectedCategory = i;
                        fromRectY = toRectY;
                        if (i < catRectPositions.size()) toRectY = catRectPositions.get(i);
                        rectSlideAnim.show();
                        onCategoryChanged(prev, i);
                        for (int j = 0; j < catColorAnims.size(); j++) {
                            if (j == i) catColorAnims.get(j).show();
                            else catColorAnims.get(j).hide();
                        }
                    }
                    return true;
                }
            }
        }

        if (selectedCategory == THEMES_CAT_INDEX && !wasSearchOpen && (!searchOpen || searchInput.getText().isEmpty())) {
            themeSettings.mouseClicked(mx, my, btn);
            return true;
        }
        if (selectedCategory == CONFIG_CAT_INDEX && !wasSearchOpen && (!searchOpen || searchInput.getText().isEmpty())) {
            configPanel.mouseClicked(mx, my, btn);
            return true;
        }
        if (selectedCategory == COSMETICS_CAT_INDEX && !wasSearchOpen && (!searchOpen || searchInput.getText().isEmpty())) {
            cosmeticsPanel.mouseClicked(mx, my, btn);
            return true;
        }
        if (selectedCategory == AUTOBUY_CAT_INDEX && !wasSearchOpen && (!searchOpen || searchInput.getText().isEmpty())) {
            autoBuyPanel.mouseClicked(mx, my, btn);
            return true;
        }

        handleModulesClick(mx, my, btn, sx, sy);
        return super.mouseClicked(event, doubled);
    }

    private void handleModulesClick(double mx, double my, int btn, float sx, float sy) {

        if (searchOpen) {
            return;
        }


        float s = uiScale();
        CsSettingComponent.setLayoutScale(s);

        float[] area = contentArea(sx, sy);
        float contentX = area[0];

        float contentY = area[1] + contentSlideOffset();
        float contentW = area[2];
        float contentH = area[3];
        float moduleGap = MODULE_GAP_X * s;
        float moduleW = (contentW - moduleGap * (MODULE_COLUMNS - 1)) / MODULE_COLUMNS;
        float baseModuleH = BASE_MODULE_H * s;
        float moduleGapY = MODULE_GAP_Y * s;
        float headerToSettings = HEADER_TO_SETTINGS_GAP * s;

        List<Module> mods = getModulesToRender();
        if (mods.isEmpty()) return;


        float viewY = area[1];
        if (mx < contentX || mx > contentX + contentW || my < viewY || my > viewY + contentH) {
            return;
        }

        float[] colYs = new float[MODULE_COLUMNS];
        java.util.Arrays.fill(colYs, contentY - moduleScroll);
        for (int i = 0; i < mods.size(); i++) {
            Module mod = mods.get(i);
            int col = pickColumn(colYs);
            float sh = getSettingsHeight(mod, moduleW);
            float h = sh > 0f ? baseModuleH + headerToSettings + sh : baseModuleH;
            float modX = contentX + col * (moduleW + moduleGap);
            float modY = colYs[col];
            colYs[col] += h + moduleGapY;

            boolean inCard = mx >= modX && mx <= modX + moduleW && my >= modY && my <= modY + h;
            if (!inCard) continue;

            float headerMid = modY + baseModuleH * 0.5f;
            float tw = TOGGLE_W * s * 0.92f;
            float th = TOGGLE_H * s * 0.92f;
            float toggleX = modX + moduleW - tw - CARD_PAD * s;
            float toggleY = headerMid - th * 0.5f;


            if (btn == 0 && mx >= toggleX - 2f && mx <= toggleX + tw + 2f
                    && my >= toggleY - 2f && my <= toggleY + th + 2f) {
                mod.toggle();
                return;
            }


            String bind = mod.getBind() != null ? mod.getBind().getDisplayName() : "";
            boolean hasBind = bind != null && !bind.isEmpty() && !bind.equalsIgnoreCase("NONE");
            boolean isBinding = bindingModule == mod;
            if (hasBind || isBinding) {
                String shown = isBinding ? "..." : bind;
                float keySize = 8f * s;
                float boxH = 14f * s;
                float btw = Render2D.textWidth(FontType.INTER_SEMI, shown, keySize);
                float boxW = Math.max(boxH, btw + 8f * s);
                float bindX = toggleX - boxW - 6f * s;
                float bindY = headerMid - boxH * 0.5f;
                if (mx >= bindX && mx <= bindX + boxW && my >= bindY && my <= bindY + boxH) {
                    if (btn == 0 || btn == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                        bindingModule = mod;
                    } else if (btn == 1) {
                        mod.setBind(KeyBind.NONE);
                    }
                    return;
                }
            }


            if (btn != GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hasRenderableSettings(mod)) {
                float settingsStartY = modY + baseModuleH + headerToSettings;
                if (my >= settingsStartY && handleSettingClicked(mod, mx, my, btn, modX, settingsStartY, moduleW)) {
                    return;
                }
            }


            if (my >= modY && my <= modY + baseModuleH) {
                if (btn == 0) {
                    mod.toggle();
                } else if (btn == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    bindingModule = mod;
                    CsSettingComponent.listeningBindComp = null;
                } else if (btn == 1) {
                    mod.setBind(KeyBind.NONE);
                }
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = SB_NONE;
        themeSettings.mouseReleased();
        CsColorPicker.get().mouseReleased(fixedMouseX(event.x()), fixedMouseY(event.y()), event.button());
        if (selectedCategory == AUTOBUY_CAT_INDEX) {
            autoBuyPanel.mouseReleased(fixedMouseX(event.x()), fixedMouseY(event.y()), event.button());
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {


        if (selectedCategory == AUTOBUY_CAT_INDEX
                && autoBuyPanel.mouseDragged(fixedMouseX(event.x()), fixedMouseY(event.y()))) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horiz, double vert) {
        if (CsColorPicker.get().isOpen()) return true;

        float mx = fixedMouseX(mouseX);
        float my = fixedMouseY(mouseY);
        float sx = panelX();
        float sy = panelY();

        float miniH = miniH();
        float catAreaY = sy + panelH() - miniH;
        if (my >= catAreaY && my <= sy + panelH()) {
            catTargetScroll = Math.max(0f, catTargetScroll - (float) horiz * 20f);
            if (horiz == 0 && vert != 0) {
                catTargetScroll = Math.max(0f, catTargetScroll - (float) vert * 20f);
            }
            return true;
        }

        float[] area = contentArea(sx, sy);
        float scrollAreaX = area[4];
        float scrollAreaY = area[5];
        float scrollAreaW = area[6];
        float scrollAreaH = area[7];

        if (mx < scrollAreaX || mx > scrollAreaX + scrollAreaW ||
                my < scrollAreaY || my > scrollAreaY + scrollAreaH) return false;

        float step = 42f;
        if (selectedCategory == THEMES_CAT_INDEX && searchInput.getText().isEmpty()) {
            return themeSettings.mouseScrolled(vert);
        }
        if (selectedCategory == CONFIG_CAT_INDEX && searchInput.getText().isEmpty()) {
            return configPanel.mouseScrolled(vert);
        }
        if (selectedCategory == COSMETICS_CAT_INDEX && searchInput.getText().isEmpty()) {
            return cosmeticsPanel.mouseScrolled(vert);
        }
        if (selectedCategory == AUTOBUY_CAT_INDEX && searchInput.getText().isEmpty()) {


            return autoBuyPanel.mouseScrolled(fixedMouseX(mouseX), fixedMouseY(mouseY), vert);
        }

        cancelPendingModuleScroll();
        moduleTargetScroll = Math.max(0f, moduleTargetScroll - (float) vert * step);
        return true;
    }


    private void cancelPendingModuleScroll() {
        pendingModuleScroll = -1f;
        pendingModuleScrollFrames = 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        persistUiState();
        super.onClose();
        ClickGuiOpenEffects.onGuiClosing();
        CsColorPicker.get().close();
        CsColorPicker.get().clearClamp();
        if (CsStringSetting.editing != null) CsStringSetting.editing = null;
        CsSettingComponent.suppressVisibilityAnim = false;
        CsSettingComponent.tickSuppressVisibility();
    }

    @Override
    public void removed() {

        persistUiState();
        super.removed();
    }



    private static int withAlpha(int c, int a) {
        return ColorUtil.withAlpha(c, a);
    }

}