package polaris.screens.clickgui.dropdown;

import polaris.IMinecraft;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.Setting;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ButtonSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.blur.BuiltBlur;
import polaris.utils.render.ui.font.FontType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DropDownCategoryPanel implements IMinecraft {
    private static final float HEADER_HEIGHT = 18f;
    private static final float MODULE_HEIGHT = 14f;
    private static final float SETTING_PADDING = 5f;

    private static final float FONT_HEADER = 7f;
    private static final float FONT_MODULE = 6f;
    private static final float FONT_SETTING = 5.5f;
    private static final float FONT_SMALL = 5f;
    private static final float FONT_ICON = 6f;

    private static final int ACCENT_FALLBACK = new Color(123, 92, 250).getRGB();
    private static final int BG_PANEL = new Color(22, 22, 26, 235).getRGB();
    private static final int BG_HEADER = new Color(28, 28, 33, 250).getRGB();
    private static final int BG_ROW = new Color(30, 30, 35).getRGB();
    private static final int BG_SETTING = new Color(38, 38, 44).getRGB();
    private static final int OUTLINE = new Color(48, 48, 56, 180).getRGB();
    private static final int TEXT = new Color(225, 225, 230).getRGB();
    private static final int TEXT_DIM = new Color(140, 140, 150).getRGB();
    
    
    private static final int BG_PANEL_BLUR = new Color(10, 10, 12, 250).getRGB();
    private static final int BG_HEADER_BLUR = new Color(12, 12, 15, 255).getRGB();

    private static int getAccent() {
        try {
            return polaris.api.drag.impl.HudTheme.current().accentColor;
        } catch (Throwable ignored) {
        }
        return ACCENT_FALLBACK;
    }

    private final DropDownScreen screen;
    private final ModuleCategory category;
    private final int panelIndex;
    private final List<ModuleData> moduleComponents = new ArrayList<>();

    private float x, y;
    private float animatedX, animatedY;
    private final float width, height;

    private float modulesScroll, modulesScrollTarget;
    private float modulesMaxScroll;
    private float settingsScroll, settingsScrollTarget;
    private float settingsMaxScroll;

    private float swapAnim;
    private int panelMouseX, panelMouseY;

    private ModuleData lastSelected;
    private ModuleData selectedModule;

    private Module bindingModule;
    private BindSetting bindingSetting;
    private StringSetting focusedText;
    private NumberSetting draggingSlider;

    private ColorSetting expandedColor;
    private boolean draggingPalette, draggingHue, draggingAlpha;
    private final Map<String, Float> animMap = new HashMap<>();
    private float panelOpenAnim = 0f;
    private long lastRenderMs;
    private float currentDt = 1f / 60f;

    private String searchQuery = "";

    private ModuleData hoveredModule;
    private float hoveredX, hoveredY;

    private static final Map<ModuleCategory, String> PERSISTED_SELECTED_MODULE = new HashMap<>();
    private static final Map<ModuleCategory, Float> PERSISTED_MODULES_SCROLL = new HashMap<>();
    private static final Map<ModuleCategory, Float> PERSISTED_SETTINGS_SCROLL = new HashMap<>();
    private static final Map<String, String> PERSISTED_EXPANDED_COLOR = new HashMap<>();

    private static float smooth(float current, float target, float speed, float dt) {
        float t = 1f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }

    public DropDownCategoryPanel(DropDownScreen screen, ModuleCategory category, int panelIndex,
                                 float x, float y, float width, float height) {
        this.screen = screen;
        this.category = category;
        this.panelIndex = panelIndex;
        this.x = x;
        this.y = y;
        this.animatedX = x;
        this.animatedY = y;
        this.width = width;
        this.height = height;
        rebuildModules();
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.toLowerCase();
    }

    public ModuleData getHoveredModule() {
        return hoveredModule;
    }

    public float getHoveredX() {
        return hoveredX;
    }

    public float getHoveredY() {
        return hoveredY;
    }

    public String getHoveredDescription() {
        if (hoveredModule == null) return null;
        String desc = hoveredModule.module.getDescription();
        return (desc == null || desc.isEmpty()) ? null : desc;
    }

    public String getHoveredName() {
        return hoveredModule == null ? null : hoveredModule.module.getName();
    }

    private boolean moduleMatches(ModuleData data) {
        if (searchQuery.isEmpty()) return true;
        return data.module.getName().toLowerCase().contains(searchQuery);
    }

    public void updateLayout(float newX, float newY) {
        this.x = newX;
        this.y = newY;
    }

    public void resetForOpen() {
        panelOpenAnim = 0f;
        lastRenderMs = 0L;
        animatedX = x;
        animatedY = y + 8f;

        String selectedName = PERSISTED_SELECTED_MODULE.get(category);
        ModuleData restored = null;
        if (selectedName != null) {
            for (ModuleData md : moduleComponents) {
                if (md.module.getName().equals(selectedName)) {
                    restored = md;
                    break;
                }
            }
        }
        selectedModule = restored;
        lastSelected = restored;
        swapAnim = restored != null ? 1f : 0f;

        modulesScroll = PERSISTED_MODULES_SCROLL.getOrDefault(category, 0f);
        modulesScrollTarget = modulesScroll;
        settingsScroll = PERSISTED_SETTINGS_SCROLL.getOrDefault(category, 0f);
        settingsScrollTarget = settingsScroll;

        expandedColor = null;
        if (restored != null) {
            String expandedKey = PERSISTED_EXPANDED_COLOR.get(persistKey(restored.module.getName()));
            if (expandedKey != null) {
                for (Setting<?> s : restored.module.getSettings()) {
                    if (s instanceof ColorSetting cs && cs.getName().equals(expandedKey)) {
                        expandedColor = cs;
                        break;
                    }
                }
            }
        }

        focusedText = null;
        bindingModule = null;
        bindingSetting = null;
    }

    private String persistKey(String moduleName) {
        return category.name() + "::" + moduleName;
    }

    public void persistState() {
        if (selectedModule != null) {
            PERSISTED_SELECTED_MODULE.put(category, selectedModule.module.getName());
        } else {
            PERSISTED_SELECTED_MODULE.remove(category);
        }
        PERSISTED_MODULES_SCROLL.put(category, modulesScroll);
        PERSISTED_SETTINGS_SCROLL.put(category, settingsScroll);

        if (selectedModule != null) {
            String key = persistKey(selectedModule.module.getName());
            if (expandedColor != null) {
                PERSISTED_EXPANDED_COLOR.put(key, expandedColor.getName());
            } else {
                PERSISTED_EXPANDED_COLOR.remove(key);
            }
        }
    }

    private void rebuildModules() {
        moduleComponents.clear();
        List<Module> modules = new ArrayList<>(screen.getModulesForCategory(category));
        modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        for (Module module : modules) {
            moduleComponents.add(new ModuleData(module));
        }
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta, float alpha) {
        panelMouseX = mouseX;
        panelMouseY = mouseY;

        long now = System.currentTimeMillis();
        float dt;
        if (lastRenderMs == 0L) {
            dt = 1f / 60f;
            panelOpenAnim = 0f;
        } else {
            dt = (now - lastRenderMs) / 1000f;
        }
        lastRenderMs = now;
        dt = Mth.clamp(dt, 0.001f, 0.1f);
        currentDt = dt;

        final float SLIDE_SPEED = 12f;
        final float SCROLL_SPEED = 14f;
        final float SWAP_SPEED = 11f;
        final float OPEN_SPEED = 10f;

        panelOpenAnim = smooth(panelOpenAnim, 1f, OPEN_SPEED, dt);
        animatedX = smooth(animatedX, x, SLIDE_SPEED, dt);
        animatedY = smooth(animatedY, y + (1f - alpha) * 8f, SLIDE_SPEED, dt);
        modulesScroll = smooth(modulesScroll, modulesScrollTarget, SCROLL_SPEED, dt);
        settingsScroll = smooth(settingsScroll, settingsScrollTarget, SCROLL_SPEED, dt);
        swapAnim = smooth(swapAnim, selectedModule != null ? 1f : 0f, SWAP_SPEED, dt);

        float delay = Math.min(0.24f, panelIndex * 0.045f);
        float reveal = Mth.clamp((alpha - delay) / Math.max(0.001f, 1f - delay), 0f, 1f);
        float openEase = 1f - (float) Math.pow(1f - reveal, 3f);
        float appear = Mth.clamp(openEase * panelOpenAnim, 0f, 1f);
        float slideY = (1f - appear) * 12f;

        float rx = animatedX;
        float ry = animatedY + slideY;
        int aA = (int) (255 * appear);

        
        Render2D.blur(rx, ry, width, height, 6f, 16f, 1f, applyAlpha(BG_PANEL_BLUR, appear));
        Render2D.outline(rx, ry, width, height, 6f, 0.5f, applyAlpha(OUTLINE, appear));

        
        Render2D.blur(new BuiltBlur(rx, ry, width, HEADER_HEIGHT, 
                6f, 6f, 0f, 0f, 
                1f, 16f, applyAlpha(BG_HEADER_BLUR, appear)));

        Render2D.text(FontType.SEMIBOLD, screen.getCategoryLabel(category),
                rx + 7f, ry + 6f, FONT_HEADER, applyAlpha(TEXT, appear));

        String icon = screen.getCategoryIcon(category);
        float iconW = Render2D.textWidth(FontType.MAINMENUSCREEN, icon, FONT_ICON);
        Render2D.text(FontType.MAINMENUSCREEN, icon,
                rx + width - iconW - 7f, ry + 6f, FONT_ICON,
                applyAlpha(getAccent(), appear));

        Render2D.rect(rx + 1f, ry + HEADER_HEIGHT, width - 2f, 0.5f,
                0f, applyAlpha(OUTLINE, appear));

        if (selectedModule != null) lastSelected = selectedModule;

        float swapEased;
        if (swapAnim < 0.5f) {
            swapEased = 4f * swapAnim * swapAnim * swapAnim;
        } else {
            float f = 2f * swapAnim - 2f;
            swapEased = 1f + f * f * f / 2f;
        }

        float contentY = ry + HEADER_HEIGHT + 2f;
        float contentH = height - HEADER_HEIGHT - 4f;
        Render2D.pushScissor(context, rx + 1f, contentY, width - 2f, contentH);

        if (swapAnim < 0.99f) {
            renderModulesPage(context, rx + width * swapEased, ry, mouseX, mouseY, appear);
        }
        if (swapAnim > 0.01f && lastSelected != null) {
            renderSettingsPage(context, rx + width * (1f - swapEased), ry, lastSelected, appear);
        }

        Render2D.popScissor(context);

        if (draggingSlider != null) updateSlider(mouseX, draggingSlider);
        if (expandedColor != null) updateColorDrag(mouseX, mouseY);
    }

    private void renderModulesPage(GuiGraphics context, float pageX, float pageY,
                                    int mouseX, int mouseY, float appear) {
        float contentY = pageY + HEADER_HEIGHT + 2f;
        float contentH = height - HEADER_HEIGHT - 4f;

        hoveredModule = null;

        float rowY = contentY + modulesScroll;
        float total = 0f;
        for (ModuleData data : moduleComponents) {
            float visTarget = moduleMatches(data) ? 1f : 0f;
            data.filterAnim = smooth(data.filterAnim, visTarget, 14f, currentDt);

            float effectiveH = MODULE_HEIGHT * data.filterAnim;
            if (data.filterAnim > 0.01f
                    && rowY + effectiveH >= contentY && rowY <= contentY + contentH) {
                renderModuleRow(data, pageX, rowY, mouseX, mouseY, appear * data.filterAnim);
            }
            rowY += effectiveH;
            total += effectiveH;
        }
        modulesMaxScroll = Math.max(0f, total - contentH);
    }

    private void renderModuleRow(ModuleData data, float pageX, float rowY,
                                  int mouseX, int mouseY, float appear) {
        Module module = data.module;
        boolean hovered = isHover(mouseX, mouseY, pageX, rowY, width, MODULE_HEIGHT);
        if (hovered && data.filterAnim > 0.5f) {
            hoveredModule = data;
            hoveredX = pageX + width;
            hoveredY = rowY;
        }
        data.hoverAnim = smooth(data.hoverAnim, hovered ? 1f : 0f, 14f, currentDt);
        data.enableAnim = smooth(data.enableAnim, module.isEnabled() ? 1f : 0f, 14f, currentDt);

        if (data.hoverAnim > 0.01f) {
            Render2D.rect(pageX + 3f, rowY + 1f, width - 6f, MODULE_HEIGHT - 2f,
                    3f, applyAlpha(BG_ROW, data.hoverAnim * 0.6f * appear));
        }

        if (data.enableAnim > 0.01f) {
            Render2D.rect(pageX + 3f, rowY + 3f, 1.5f, MODULE_HEIGHT - 6f,
                    1f, applyAlpha(getAccent(), data.enableAnim * appear));
        }

        String text = module.getName();
        int textColor = blendColor(TEXT_DIM, TEXT, data.enableAnim);
        Render2D.text(FontType.SEMIBOLD, text, pageX + 8f, rowY + 4f, FONT_MODULE,
                applyAlpha(textColor, appear));

        float rightX = pageX + width - 6f;

        if (!module.getSettings().isEmpty()) {
            Render2D.text(FontType.SEMIBOLD, "...", rightX - 5f, rowY + 1f, 6f,
                    applyAlpha(TEXT_DIM, 0.6f * appear));
            rightX -= 9f;
        }

        if (bindingModule == module) {
            String display = "[...]";
            float bindW = Render2D.textWidth(FontType.SEMIBOLD, display, FONT_SMALL);
            Render2D.text(FontType.SEMIBOLD, display, rightX - bindW, rowY + 4.5f,
                    FONT_SMALL, applyAlpha(getAccent(), appear));
        } else if (module.getBind() != null && module.getBind().isBound()) {
            String bindText = "[" + KeyDisplayHelper.getShortKeyName(module.getBind()) + "]";
            float bindW = Render2D.textWidth(FontType.SEMIBOLD, bindText, FONT_SMALL);
            Render2D.text(FontType.SEMIBOLD, bindText, rightX - bindW, rowY + 4.5f,
                    FONT_SMALL, applyAlpha(TEXT_DIM, appear));
        }
    }

    private void renderSettingsPage(GuiGraphics context, float pageX, float pageY,
                                     ModuleData data, float appear) {
        float topY = pageY + HEADER_HEIGHT + 2f;
        Render2D.text(FontType.GUI_ICONS, "M", pageX + 5f, topY + 1f, 8f, applyAlpha(TEXT, appear));
        Render2D.text(FontType.SEMIBOLD, data.module.getName(), pageX + 16f, topY + 3f,
                FONT_MODULE, applyAlpha(TEXT, appear));
        Render2D.rect(pageX + 1f, topY + 12f, width - 2f, 0.5f, applyAlpha(OUTLINE, appear));

        float contentY = topY + 14f;
        float contentH = height - (contentY - pageY) - 2f;

        float sy = contentY + settingsScroll;
        float startY = sy;
        for (Setting<?> setting : data.module.getSettings()) {
            if (!setting.isVisible()) continue;
            sy = renderSetting(setting, pageX + 4f, sy, width - 8f, appear);
        }
        settingsMaxScroll = Math.max(0f, (sy - startY) - contentH + 4f);
    }

    private float renderSetting(Setting<?> setting, float sx, float sy, float sw, float appear) {
        if (setting instanceof BooleanSetting b) return renderBoolean(b, sx, sy, sw, appear);
        if (setting instanceof NumberSetting s) return renderSlider(s, sx, sy, sw, appear);
        if (setting instanceof ModeSetting s) {
            return renderSelect(s.getName(), s.getValue(), s.getModes(), s, null, sx, sy, sw, appear);
        }
        if (setting instanceof MultiModeSetting m) return renderMultiSelect(m, sx, sy, sw, appear);
        if (setting instanceof BindSetting b) return renderBind(b, sx, sy, sw, appear);
        if (setting instanceof StringSetting t) return renderText(t, sx, sy, sw, appear);
        if (setting instanceof ColorSetting c) return renderColor(c, sx, sy, sw, appear);
        if (setting instanceof ButtonSetting b) return renderButton(b, sx, sy, sw, appear);
        return sy + 12f;
    }

    private float renderBoolean(BooleanSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "T", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, setting.getName(), sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));

        String key = "bool:" + setting.getName();
        float anim = animate(key, setting.getValue() ? 1f : 0f);

        float toggleW = 14f, toggleH = 7f;
        float toggleX = sx + sw - toggleW - 3f;
        float toggleY = sy + 2.5f;

        int bg = setting.getValue() ? blendColor(BG_SETTING, getAccent(), anim)
                : applyAlpha(BG_SETTING, 0.7f);
        Render2D.rect(toggleX, toggleY, toggleW, toggleH, toggleH / 2f, applyAlpha(bg, appear));

        float knob = toggleH - 2f;
        float knobX = toggleX + 1f + (toggleW - knob - 2f) * anim;
        Render2D.rect(knobX, toggleY + 1f, knob, knob, knob / 2f, applyAlpha(TEXT, appear));

        return sy + 13f;
    }

    private float renderSlider(NumberSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "H", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, setting.getName(), sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));
        String value = formatSliderValue(setting);
        float vw = Render2D.textWidth(FontType.SEMIBOLD, value, FONT_SMALL);
        Render2D.text(FontType.SEMIBOLD, value, sx + sw - vw - 3f, sy + 3f, FONT_SMALL,
                applyAlpha(TEXT_DIM, appear));

        float barX = sx + 3f, barY = sy + 10f, barW = sw - 6f, barH = 2.5f;
        double range = setting.getMax() - setting.getMin();
        float pct = Mth.clamp(
                (float) ((setting.getValue() - setting.getMin()) / Math.max(0.0001, range)),
                0f, 1f);

        Render2D.rect(barX, barY, barW, barH, 1.25f, applyAlpha(BG_SETTING, appear));
        Render2D.rect(barX, barY, barW * pct, barH, 1.25f, applyAlpha(getAccent(), appear));

        float knobS = 5f;
        float knobX = barX + barW * pct - knobS / 2f;
        float knobY = barY + barH / 2f - knobS / 2f;
        Render2D.rect(knobX, knobY, knobS, knobS, knobS / 2f, applyAlpha(TEXT, appear));

        return sy + 16f;
    }

    private float renderSelect(String name, String selected, List<String> values,
                                ModeSetting selectRef, BindSetting radioRef,
                                float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "J", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, name, sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));

        float panelY = sy + 11f;
        float panelH = values.size() * 8f + 4f;
        Render2D.rect(sx + 3f, panelY, sw - 6f, panelH, 3f, applyAlpha(BG_SETTING, appear * 0.8f));

        float oy = panelY + 2f;
        for (String value : values) {
            boolean active = value.equals(selected);
            String key = "sel:" + name + ":" + value;
            float a = animate(key, active ? 1f : 0f);
            int color = blendColor(TEXT_DIM, TEXT, a);

            if (a > 0.01f) {
                Render2D.rect(sx + 4f, oy, 1f, 7f, 0.5f, applyAlpha(getAccent(), a * appear));
            }
            Render2D.text(FontType.SEMIBOLD, value, sx + 7f, oy + 1.5f, FONT_SMALL,
                    applyAlpha(color, appear));
            if (a > 0.5f) {
                Render2D.text(FontType.GUI_ICONS, "o", sx + sw - 14f, oy - 1f, 9f,
                        applyAlpha(getAccent(), a * appear));
            }
            oy += 8f;
        }

        return sy + 12f + panelH + 2f;
    }

    private float renderMultiSelect(MultiModeSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "I", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        String label = setting.getName() + " (" + setting.selectedCount() + "/" + setting.getModes().size() + ")";
        Render2D.text(FontType.SEMIBOLD, label, sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));

        float panelY = sy + 11f;
        float panelH = setting.getModes().size() * 8f + 4f;
        Render2D.rect(sx + 3f, panelY, sw - 6f, panelH, 3f, applyAlpha(BG_SETTING, appear * 0.8f));

        float oy = panelY + 2f;
        for (String value : setting.getModes()) {
            boolean active = setting.isSelected(value);
            String key = "ms:" + setting.getName() + ":" + value;
            float a = animate(key, active ? 1f : 0f);
            int color = blendColor(TEXT_DIM, TEXT, a);

            if (a > 0.01f) {
                Render2D.rect(sx + 4f, oy, 1f, 7f, 0.5f, applyAlpha(getAccent(), a * appear));
            }
            Render2D.text(FontType.SEMIBOLD, value, sx + 7f, oy + 1.5f, FONT_SMALL,
                    applyAlpha(color, appear));
            if (a > 0.5f) {
                Render2D.text(FontType.GUI_ICONS, "o", sx + sw - 14f, oy - 1f, 9f,
                        applyAlpha(getAccent(), a * appear));
            }
            oy += 8f;
        }

        return sy + 12f + panelH + 2f;
    }

    private float renderBind(BindSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "L", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, setting.getName(), sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));
        String key = bindingSetting == setting ? "..."
                : KeyDisplayHelper.getShortKeyName(setting.getValue());
        String display = "[" + key + "]";
        float kw = Render2D.textWidth(FontType.SEMIBOLD, display, FONT_SMALL);

        Render2D.rect(sx + sw - kw - 8f, sy + 2.5f, kw + 5f, 7f, 2f, applyAlpha(BG_SETTING, appear));
        Render2D.text(FontType.SEMIBOLD, display, sx + sw - kw - 5.5f, sy + 4f, FONT_SMALL,
                applyAlpha(bindingSetting == setting ? getAccent() : TEXT, appear));
        return sy + 12f;
    }

    private float renderText(StringSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "S", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, setting.getName(), sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));
        Render2D.rect(sx + 3f, sy + 9f, sw - 6f, 8f, 2f, applyAlpha(BG_SETTING, appear));
        String value = setting.getValue() == null ? "" : setting.getValue();
        if (focusedText == setting && System.currentTimeMillis() % 1000L < 500L) value += "|";
        String shown = value.isEmpty() ? "Type..." : value;
        int color = value.isEmpty() ? TEXT_DIM : TEXT;
        Render2D.text(FontType.SEMIBOLD, shown, sx + 5f, sy + 11f, FONT_SMALL,
                applyAlpha(color, appear));
        return sy + 19f;
    }

    private float renderColor(ColorSetting setting, float sx, float sy, float sw, float appear) {
        Render2D.text(FontType.GUI_ICONS, "R", sx + 2f, sy + 2f, 7f, applyAlpha(TEXT_DIM, appear));
        Render2D.text(FontType.SEMIBOLD, setting.getName(), sx + 11f, sy + 3f, FONT_SETTING,
                applyAlpha(TEXT, appear));

        float pw = 9f, ph = 9f;
        float px = sx + sw - pw - 3f, py = sy + 1.5f;
        boolean previewHover = isHover(panelMouseX, panelMouseY, px, py, pw, ph);
        String previewKey = "colorpreview:" + setting.getName();
        float prevAnim = animate(previewKey, previewHover ? 1f : 0f);

        Render2D.rect(px - 0.5f, py - 0.5f, pw + 1f, ph + 1f,
                pw / 2f, applyAlpha(0xFF7D7D7D, appear));
        Render2D.rect(px, py, pw, ph, pw / 2f, applyAlpha(setting.getValue().getRGB(), appear));

        float baseH = 12f;
        if (expandedColor != setting) return sy + baseH;

        float pkX = sx + 1f;
        float pkY = sy + baseH + 1f;
        float pkW = sw - 2f;

        float spacing = 3f;
        float sliderW = 6f;
        float palH = 60f;
        float slidersW = sliderW * 2 + spacing;
        float palW = pkW - slidersW - spacing * 3f;

        float pickerH = palH + spacing * 2 + 14f + 12f;

        Render2D.rect(pkX, pkY, pkW, pickerH, 4f, applyAlpha(BG_SETTING, appear));
        Render2D.outline(pkX, pkY, pkW, pickerH, 4f, 0.5f, applyAlpha(OUTLINE, appear));

        float contentX = pkX + spacing;
        float contentY = pkY + spacing;

        renderHueSlider(contentX, contentY, sliderW, palH, appear);
        renderHueHandle(contentX, contentY, sliderW, palH, getHue(setting), appear);

        float alphaX = contentX + sliderW + spacing;
        renderAlphaSlider(alphaX, contentY, sliderW, palH, setting, appear);
        renderAlphaHandle(alphaX, contentY, sliderW, palH, setting.getValue().getAlpha() / 255f, appear);

        float palX = alphaX + sliderW + spacing;
        renderColorPalette(palX, contentY, palW, palH, getHue(setting), appear);
        renderPaletteHandle(palX, contentY, palW, palH, getSaturation(setting), getBrightness(setting), appear);

        float hexY = contentY + palH + spacing;
        Render2D.rect(contentX, hexY, pkW - spacing * 2, 12f,
                2.5f, applyAlpha(0xFF1A1A1F, appear));
        Render2D.text(FontType.GUI_ICONS, "V", contentX + 2f, hexY + 1.5f, 9f,
                applyAlpha(TEXT_DIM, appear));
        int color = setting.getValue().getRGB();
        String hex = String.format("#%02X%02X%02X%02X",
                (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF,
                (color >> 24) & 0xFF);
        Render2D.text(FontType.SEMIBOLD, hex, contentX + 12f, hexY + 3.5f, FONT_SMALL,
                applyAlpha(TEXT, appear));

        float miniSize = 8f;
        float miniX = contentX + (pkW - spacing * 2) - miniSize - 3f;
        float miniY = hexY + (12f - miniSize) / 2f;
        Render2D.rect(miniX - 0.5f, miniY - 0.5f, miniSize + 1f, miniSize + 1f,
                miniSize / 2f, applyAlpha(0xFF888888, appear));
        Render2D.rect(miniX, miniY, miniSize, miniSize, miniSize / 2f, applyAlpha(setting.getValue().getRGB(), appear));

        
        float syncY = hexY + 12f + 1f;
        float boxSize = 8f;
        boolean syncOn = setting.isSyncTheme();
        Render2D.rect(contentX, syncY, boxSize, boxSize, 2f,
                applyAlpha(syncOn ? getAccent() : 0xFF1A1A1F, appear));
        Render2D.outline(contentX, syncY, boxSize, boxSize, 2f, 0.5f, applyAlpha(OUTLINE, appear));
        if (syncOn) {
            Render2D.text(FontType.GUI_ICONS, "o", contentX + 0.5f, syncY - 1.5f, 9f,
                    applyAlpha(0xFFFFFFFF, appear));
        }
        Render2D.text(FontType.SEMIBOLD, "Sync theme", contentX + boxSize + 4f, syncY + 1.5f, FONT_SMALL,
                applyAlpha(syncOn ? TEXT : TEXT_DIM, appear));

        return sy + baseH + pickerH + 2f;
    }

    private void renderColorPalette(float x, float y, float w, float h, float hue, float appear) {
        int pureHue = Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
        int white = applyAlpha(0xFFFFFFFF, appear);
        int black = applyAlpha(0xFF000000, appear);
        int hueA = applyAlpha(pureHue, appear);
        
        Render2D.rect(x, y, w, h, 2f, white, hueA, black, black);
        Render2D.outline(x, y, w, h, 2f, 0.4f, applyAlpha(OUTLINE, appear * 0.6f));
    }

    private void renderPaletteHandle(float px, float py, float pw, float ph,
                                      float sat, float bri, float appear) {
        float hx = px + pw * sat;
        float hy = py + ph * (1f - bri);
        float hs = 5f;
        Render2D.rect(hx - hs / 2f, hy - hs / 2f, hs, hs,
                hs / 2f, applyAlpha(0xFFFFFFFF, appear));
    }

    private void renderHueSlider(float x, float y, float w, float h, float appear) {
        int red = applyAlpha(0xFFFF0000, appear);
        int yellow = applyAlpha(0xFFFFFF00, appear);
        int green = applyAlpha(0xFF00FF00, appear);
        int cyan = applyAlpha(0xFF00FFFF, appear);
        int blue = applyAlpha(0xFF0000FF, appear);
        int magenta = applyAlpha(0xFFFF00FF, appear);
        int[] hues = {red, yellow, green, cyan, blue, magenta, red};
        float segH = h / 6f;
        for (int i = 0; i < 6; i++) {
            int top = hues[i];
            int bot = hues[i + 1];
            float ry = y + i * segH;
            float rTL = i == 0 ? 2f : 0f;
            float rTR = i == 0 ? 2f : 0f;
            float rBR = i == 5 ? 2f : 0f;
            float rBL = i == 5 ? 2f : 0f;
            
            Render2D.rect(x, ry, w, segH + 0.5f, rTL, rTR, rBR, rBL,
                    top, top, bot, bot);
        }
        Render2D.outline(x, y, w, h, 2f, 0.4f, applyAlpha(OUTLINE, appear * 0.6f));
    }

    private void renderHueHandle(float x, float y, float w, float h, float hue, float appear) {
        float hY = y + h * hue;
        float hH = 2.5f;
        Render2D.rect(x - 1f, hY - hH / 2f, w + 2f, hH,
                hH / 2f, applyAlpha(0xFFFFFFFF, appear));
    }

    private void renderAlphaSlider(float x, float y, float w, float h,
                                    ColorSetting setting, float appear) {
        
        int dark = applyAlpha(0xFF333333, appear);
        int light = applyAlpha(0xFF555555, appear);
        Render2D.rect(x, y, w, h, 2f, dark);
        float cell = 3f;
        int rows = (int) Math.ceil(h / cell);
        int cols = (int) Math.ceil(w / cell);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (((r + c) & 1) == 0) continue;
                float cx = x + c * cell;
                float cy = y + r * cell;
                float cw = Math.min(cell, x + w - cx);
                float ch = Math.min(cell, y + h - cy);
                if (cw > 0 && ch > 0) {
                    Render2D.rect(cx, cy, cw, ch, 0f, light);
                }
            }
        }

        int solid = applyAlpha(setting.getValue().getRGB() | 0xFF000000, appear);
        int transparent = setting.getValue().getRGB() & 0x00FFFFFF;
        
        Render2D.rect(x, y, w, h, 2f, solid, solid, transparent, transparent);
        Render2D.outline(x, y, w, h, 2f, 0.4f, applyAlpha(OUTLINE, appear * 0.6f));
    }

    private void renderAlphaHandle(float x, float y, float w, float h, float alpha, float appear) {
        float hY = y + h * (1f - alpha);
        float hH = 2.5f;
        Render2D.rect(x - 1f, hY - hH / 2f, w + 2f, hH,
                hH / 2f, applyAlpha(0xFFFFFFFF, appear));
    }

    private float renderButton(ButtonSetting setting, float sx, float sy, float sw, float appear) {
        String name = setting.getName();
        boolean hovered = isHover(panelMouseX, panelMouseY, sx + 3f, sy + 1f, sw - 6f, 11f);
        String key = "btn:" + name;
        float ha = animate(key, hovered ? 1f : 0f);
        int bg = blendColor(BG_SETTING, getAccent(), ha * 0.5f);
        Render2D.rect(sx + 3f, sy + 1f, sw - 6f, 11f, 2.5f, applyAlpha(bg, appear));

        float iconW = 7f;
        float textW = Render2D.textWidth(FontType.SEMIBOLD, name, FONT_SETTING);
        float totalW = iconW + 2f + textW;
        float startX = sx + (sw - totalW) / 2f;
        Render2D.text(FontType.GUI_ICONS, "U", startX, sy + 0.5f, 8f, applyAlpha(TEXT, appear));
        Render2D.text(FontType.SEMIBOLD, name, startX + iconW + 2f, sy + 3.5f, FONT_SETTING,
                applyAlpha(TEXT, appear));
        return sy + 14f;
    }

    public boolean isBinding() {
        return bindingModule != null || bindingSetting != null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindingModule != null) {
            if (button == 1) {
                bindingModule.setBind(polaris.api.settings.bind.KeyBind.NONE);
            } else {
                bindingModule.setBind(polaris.api.settings.bind.KeyBind.mouse(button));
            }
            bindingModule = null;
            return true;
        }
        if (bindingSetting != null) {
            if (button == 1) {
                bindingSetting.setValue(polaris.api.settings.bind.KeyBind.NONE);
            } else {
                bindingSetting.setValue(polaris.api.settings.bind.KeyBind.mouse(button));
            }
            bindingSetting = null;
            return true;
        }

        if (!isHover(mouseX, mouseY, animatedX, animatedY, width, height)) {
            focusedText = null;
            expandedColor = null;
            return false;
        }

        if (selectedModule == null) {
            float contentY = animatedY + HEADER_HEIGHT + 2f;
            float rowY = contentY + modulesScroll;
            for (ModuleData data : moduleComponents) {
                float effectiveH = MODULE_HEIGHT * data.filterAnim;
                if (data.filterAnim > 0.5f
                        && isHover(mouseX, mouseY, animatedX, rowY, width, MODULE_HEIGHT)) {
                    if (button == 0) {
                        if (isCtrlDown()) {
                            data.module.setStarred(!data.module.isStarred());
                            playSliderTick();
                            return true;
                        }
                        data.module.toggle();
                        return true;
                    }
                    if (button == 1) {
                        if (!data.module.getSettings().isEmpty()) {
                            selectedModule = data;
                            settingsScroll = 0f;
                            settingsScrollTarget = 0f;
                            playClickSound();
                        }
                        return true;
                    }
                    if (button == 2) {
                        
                        bindingModule = data.module;
                        playClickSound();
                        return true;
                    }
                }
                rowY += effectiveH;
            }
            return false;
        }

        float topY = animatedY + HEADER_HEIGHT + 2f;
        if (button == 0 && isHover(mouseX, mouseY, animatedX, topY, width, 12f)) {
            selectedModule = null;
            expandedColor = null;
            settingsScroll = 0f;
            settingsScrollTarget = 0f;
            playClickSound();
            return true;
        }

        float sx = animatedX + 4f, sw = width - 8f;
        float sy = topY + 14f + settingsScroll;
        for (Setting<?> setting : selectedModule.module.getSettings()) {
            if (!setting.isVisible()) continue;
            float next = settingNextY(setting, sy);
            if (handleSettingClick(setting, sx, sy, sw, mouseX, mouseY, button)) return true;
            sy = next;
        }
        focusedText = null;
        return false;
    }

    private boolean handleSettingClick(Setting<?> setting, float sx, float sy, float sw,
                                       double mx, double my, int button) {
        if (setting instanceof BooleanSetting b && button == 0
                && isHover(mx, my, sx, sy, sw, 13f)) {
            b.setValue(!b.getValue());
            return true;
        }
        if (setting instanceof NumberSetting s && button == 0
                && isHover(mx, my, sx, sy + 8f, sw, 8f)) {
            draggingSlider = s;
            updateSlider(mx, s);
            return true;
        }
        if (setting instanceof ModeSetting s && button == 0) {
            float py = sy + 11f;
            float oy = py + 2f;
            for (String value : s.getModes()) {
                if (isHover(mx, my, sx + 3f, oy, sw - 6f, 8f)) {
                    s.setValue(value);
                    return true;
                }
                oy += 8f;
            }
        }
        if (setting instanceof MultiModeSetting m && button == 0) {
            float py = sy + 11f;
            float oy = py + 2f;
            for (String value : m.getModes()) {
                if (isHover(mx, my, sx + 3f, oy, sw - 6f, 8f)) {
                    m.setSelected(value, !m.isSelected(value));
                    return true;
                }
                oy += 8f;
            }
        }
        if (setting instanceof BindSetting b && button == 0
                && isHover(mx, my, sx, sy, sw, 12f)) {
            bindingSetting = b;
            return true;
        }
        if (setting instanceof StringSetting t && button == 0
                && isHover(mx, my, sx + 3f, sy + 9f, sw - 6f, 8f)) {
            focusedText = t;
            return true;
        }
        if (setting instanceof ColorSetting c && button == 0) {
            if (isHover(mx, my, sx + sw - 12f, sy + 1.5f, 9f, 9f)) {
                expandedColor = (expandedColor == c) ? null : c;
                return true;
            }
            if (expandedColor == c) {
                float pkX = sx + 1f;
                float pkY = sy + 12f + 1f;
                float pkW = sw - 2f;
                float spacing = 3f;
                float sliderW = 6f;
                float palH = 60f;
                float slidersW = sliderW * 2 + spacing;
                float palW = pkW - slidersW - spacing * 3f;

                float contentX = pkX + spacing;
                float contentY = pkY + spacing;

                if (isHover(mx, my, contentX, contentY, sliderW, palH)) {
                    draggingHue = true;
                    updateHue(c, my, contentY, palH);
                    return true;
                }
                float alphaX = contentX + sliderW + spacing;
                if (isHover(mx, my, alphaX, contentY, sliderW, palH)) {
                    draggingAlpha = true;
                    updateAlpha(c, my, contentY, palH);
                    return true;
                }
                float palX = alphaX + sliderW + spacing;
                if (isHover(mx, my, palX, contentY, palW, palH)) {
                    draggingPalette = true;
                    updatePalette(c, mx, my, palX, contentY, palW, palH);
                    return true;
                }

                float hexY = contentY + palH + spacing;
                float syncY = hexY + 12f + 1f;
                if (isHover(mx, my, contentX, syncY - 1f, pkW - spacing * 2f, 10f)) {
                    c.setSyncTheme(!c.isSyncTheme());
                    return true;
                }
            }
        }
        if (setting instanceof ButtonSetting b && button == 0
                && isHover(mx, my, sx + 3f, sy + 1f, sw - 6f, 11f)) {
            b.press();
            return true;
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        draggingPalette = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isHover(mouseX, mouseY, animatedX, animatedY, width, height)) return false;
        float step = (float) amount * 14f;
        if (selectedModule != null) {
            settingsScrollTarget = Mth.clamp(
                    settingsScrollTarget + step, -settingsMaxScroll, 0f);
        } else {
            modulesScrollTarget = Mth.clamp(
                    modulesScrollTarget + step, -modulesMaxScroll, 0f);
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            bindingModule.setBind(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    ? polaris.api.settings.bind.KeyBind.NONE
                    : polaris.api.settings.bind.KeyBind.keyboard(keyCode));
            bindingModule = null;
            return true;
        }
        if (bindingSetting != null) {
            bindingSetting.setValue(keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
                    ? polaris.api.settings.bind.KeyBind.NONE
                    : polaris.api.settings.bind.KeyBind.keyboard(keyCode));
            bindingSetting = null;
            return true;
        }
        if (focusedText != null) {
            String value = focusedText.getValue() == null ? "" : focusedText.getValue();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                focusedText = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !value.isEmpty()) {
                focusedText.setValue(value.substring(0, value.length() - 1));
                playTypingSound();
                return true;
            }
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_V) {
                try {
                    
                    long handle = 0L;
                    try {
                        var method = mc.getWindow().getClass().getMethod("getHandle");
                        handle = (long) method.invoke(mc.getWindow());
                    } catch (Exception e1) {
                        try {
                            var method = mc.getWindow().getClass().getMethod("getWindow");
                            handle = (long) method.invoke(mc.getWindow());
                        } catch (Exception e2) {
                            try {
                                var field = mc.getWindow().getClass().getDeclaredField("window");
                                field.setAccessible(true);
                                handle = (long) field.get(mc.getWindow());
                            } catch (Exception e3) {
                                
                                return true;
                            }
                        }
                    }
                    String clipboard = GLFW.glfwGetClipboardString(handle);
                    if (clipboard != null) {
                    focusedText.setValue(value + clipboard);
                    playTypingSound();
                }
                } catch (Exception e) {
                    
                }
                return true;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (focusedText == null || Character.isISOControl(chr)) return false;
        String value = focusedText.getValue() == null ? "" : focusedText.getValue();
        focusedText.setValue(value + chr);
        playTypingSound();
        return true;
    }

    private void playTypingSound() {
        float pitch = 0.92f + (float) Math.random() * 0.16f;
        polaris.utils.sounds.SoundManager.playSoundDirect(
                polaris.utils.sounds.SoundManager.SEARCH_TYPING, 0.6f, pitch);
    }

    private void playClickSound() {
        polaris.utils.sounds.SoundManager.playSoundDirect(
                polaris.utils.sounds.SoundManager.SLIDER, 0.45f, 1.4f);
    }

    private float settingNextY(Setting<?> setting, float sy) {
        if (setting instanceof BooleanSetting) return sy + 13f;
        if (setting instanceof NumberSetting) return sy + 16f;
        if (setting instanceof ModeSetting s) {
            float panelH = s.getModes().size() * 8f + 4f;
            return sy + 12f + panelH + 2f;
        }
        if (setting instanceof MultiModeSetting m) {
            float panelH = m.getModes().size() * 8f + 4f;
            return sy + 12f + panelH + 2f;
        }
        if (setting instanceof BindSetting) return sy + 12f;
        if (setting instanceof StringSetting) return sy + 19f;
        if (setting instanceof ColorSetting) {
            if (expandedColor == setting) {
                float baseH = 12f;
                float palH = 60f;
                float pickerH = palH + 3f * 2 + 14f + 12f;
                return sy + baseH + pickerH + 2f;
            }
            return sy + 12f;
        }
        if (setting instanceof ButtonSetting) return sy + 14f;
        return sy + 12f;
    }

    private void updateSlider(double mx, NumberSetting setting) {
        Module module = selectedModule != null ? selectedModule.module
                : (lastSelected != null ? lastSelected.module : null);
        if (module == null) return;

        float sx = animatedX + 4f, sw = width - 8f;
        float sy = animatedY + HEADER_HEIGHT + 2f + 14f + settingsScroll;
        for (Setting<?> cur : module.getSettings()) {
            if (!cur.isVisible()) continue;
            if (cur == setting) {
                float barX = sx + 3f;
                float barW = sw - 6f;
                float pct = Mth.clamp((float) ((mx - barX) / barW), 0f, 1f);
                double raw = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
                double clamped = Math.max(setting.getMin(), Math.min(setting.getMax(), raw));
                double stepped = Math.round(clamped / setting.getStep()) * setting.getStep();
                if (stepped != setting.getValue()) {
                    setting.setValue(stepped);
                    playSliderTick();
                }
                return;
            }
            sy = settingNextY(cur, sy);
        }
    }

    private long lastSliderSoundMs = 0L;

    private void playSliderTick() {
        long now = System.currentTimeMillis();
        if (now - lastSliderSoundMs < 35L) return;
        lastSliderSoundMs = now;
        float pitch = 1.4f + (float) Math.random() * 0.3f;
        polaris.utils.sounds.SoundManager.playSoundDirect(
                polaris.utils.sounds.SoundManager.SLIDER, 0.35f, pitch);
    }

    private void updateColorDrag(int mx, int my) {
        if (expandedColor == null) return;
        if (!draggingPalette && !draggingHue && !draggingAlpha) return;

        Module module = selectedModule != null ? selectedModule.module
                : (lastSelected != null ? lastSelected.module : null);
        if (module == null) return;

        float sx = animatedX + 4f, sw = width - 8f;
        float sy = animatedY + HEADER_HEIGHT + 2f + 14f + settingsScroll;
        for (Setting<?> cur : module.getSettings()) {
            if (!cur.isVisible()) continue;
            if (cur == expandedColor) {
                float pkX = sx + 1f;
                float pkY = sy + 12f + 1f;
                float pkW = sw - 2f;
                float spacing = 3f;
                float sliderW = 6f;
                float palH = 60f;
                float slidersW = sliderW * 2 + spacing;
                float palW = pkW - slidersW - spacing * 3f;

                float contentX = pkX + spacing;
                float contentY = pkY + spacing;

                if (draggingHue) updateHue(expandedColor, my, contentY, palH);
                else if (draggingAlpha) updateAlpha(expandedColor, my, contentY, palH);
                else if (draggingPalette) {
                    float palX = contentX + sliderW + spacing + sliderW + spacing;
                    updatePalette(expandedColor, mx, my, palX, contentY, palW, palH);
                }
                return;
            }
            sy = settingNextY(cur, sy);
        }
    }

    private void updateHue(ColorSetting setting, double my, float contentY, float palH) {
        float h = Mth.clamp((float) ((my - contentY) / palH), 0f, 1f);
        Color current = setting.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        int rgb = Color.HSBtoRGB(h, hsb[1], hsb[2]);
        setting.setValue(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
    }

    private void updateAlpha(ColorSetting setting, double my, float contentY, float palH) {
        float a = Mth.clamp(1f - (float) ((my - contentY) / palH), 0f, 1f);
        Color current = setting.getValue();
        setting.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(), Math.round(a * 255)));
    }

    private void updatePalette(ColorSetting setting, double mx, double my, float palX, float palY, float palW, float palH) {
        float sat = Mth.clamp((float) ((mx - palX) / palW), 0f, 1f);
        float bri = 1f - Mth.clamp((float) ((my - palY) / palH), 0f, 1f);
        Color current = setting.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        int rgb = Color.HSBtoRGB(hsb[0], sat, bri);
        setting.setValue(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
    }

    private float getHue(ColorSetting setting) {
        Color c = setting.getValue();
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return hsb[0];
    }

    private float getSaturation(ColorSetting setting) {
        Color c = setting.getValue();
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return hsb[1];
    }

    private float getBrightness(ColorSetting setting) {
        Color c = setting.getValue();
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return hsb[2];
    }

    private String formatSliderValue(NumberSetting setting) {
        double v = setting.getValue();
        if (setting.getStep() >= 1.0) {
            return String.valueOf((long) v);
        }
        return String.format("%.2f", v);
    }

    private float animate(String key, float target) {
        Float current = animMap.get(key);
        float cur = current == null ? target : current;
        float next = smooth(cur, target, 14f, currentDt);
        animMap.put(key, next);
        return next;
    }

    private static int applyAlpha(int color, float alphaMultiplier) {
        return ColorUtil.scaleAlpha(color, alphaMultiplier);
    }

    private static int blendColor(int first, int second, float factor) {
        return ColorUtil.lerpColor(first, second, factor);
    }

    private static boolean isHover(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean isCtrlDown() {
        try {
            
            long handle = 0L;
            try {
                var method = mc.getWindow().getClass().getMethod("getHandle");
                handle = (long) method.invoke(mc.getWindow());
            } catch (Exception e1) {
                try {
                    var method = mc.getWindow().getClass().getMethod("getWindow");
                    handle = (long) method.invoke(mc.getWindow());
                } catch (Exception e2) {
                    try {
                        var field = mc.getWindow().getClass().getDeclaredField("window");
                        field.setAccessible(true);
                        handle = (long) field.get(mc.getWindow());
                    } catch (Exception e3) {
                        return false;
                    }
                }
            }
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }

    public static class ModuleData {
        public final Module module;
        public float filterAnim = 0f;
        public float hoverAnim = 0f;
        public float enableAnim = 0f;

        public ModuleData(Module module) {
            this.module = module;
        }
    }
}

