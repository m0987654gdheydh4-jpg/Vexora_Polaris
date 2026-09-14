package polaris.screens.csgui.elements;

import polaris.api.settings.Setting;
import polaris.api.settings.SettingType;
import polaris.api.settings.impl.*;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

public abstract class CsSettingComponent<T extends Setting<?>> {
    protected final T setting;
    protected float width;
    protected float height;
    protected final SimpleLinearAnimation hoverAnimation = new SimpleLinearAnimation(150);
    protected final SimpleLinearAnimation visibilityAnim = new SimpleLinearAnimation(300);
    protected boolean lastVisible = true;
    protected boolean visibilityInit = false;
    public static boolean suppressVisibilityAnim = false;
    private static int suppressFramesRemaining = 0;

    public static void requestSuppressVisibility(int frames) {
        suppressFramesRemaining = Math.max(suppressFramesRemaining, frames);
        suppressVisibilityAnim = true;
    }

    public static void tickSuppressVisibility() {
        if (suppressFramesRemaining > 0) {
            suppressFramesRemaining--;
            if (suppressFramesRemaining <= 0) {
                suppressVisibilityAnim = false;
            }
        }
    }

    public static CsSettingComponent<?> listeningBooleanComp = null;
    public static CsSettingComponent<?> listeningBindComp = null;

    
    public static int themeAccent = 0xFF78B4FF;

    
    public static float layoutScale = 1f;

    public static void setLayoutScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) {
            layoutScale = 1f;
            return;
        }
        
        layoutScale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    
    public static float sc(float value) {
        return value * layoutScale;
    }

    public static int themeAccent(int alpha) {
        return withAlpha(themeAccent, alpha);
    }

    protected static final long MARQUEE_PAUSE_MS = 3000L;
    protected static final float MARQUEE_SPEED_PX_PER_SEC = 28f;
    protected static final float MARQUEE_GAP = 24f;

    protected long hoverStartTime = 0L;
    protected boolean wasHovered = false;

    protected CsSettingComponent(T setting, float width) {
        this.setting = setting;
        this.width = width;
        this.height = 16f;
    }

    public T getSetting() {
        return setting;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public static CsSettingComponent<?> create(Setting<?> setting, float width) {
        return switch (setting.getType()) {
            case BOOLEAN -> new CsBooleanSetting((BooleanSetting) setting, width);
            case NUMBER -> new CsNumberSetting((NumberSetting) setting, width);
            case MODE -> new CsModeSetting((ModeSetting) setting, width);
            case MULTI_MODE -> new CsMultiModeSetting((MultiModeSetting) setting, width);
            case BIND -> new CsBindSetting((BindSetting) setting, width);
            case STRING -> new CsStringSetting((StringSetting) setting, width);
            case COLOR -> new CsColorSetting((ColorSetting) setting, width);
            case BUTTON -> new CsButtonSetting((ButtonSetting) setting, width);
        };
    }

    public abstract void draw(float x, float y, int mouseX, int mouseY, int alpha);

    public abstract void mouseClicked(double mouseX, double mouseY, int button, float x, float y);

    public boolean hovered(float x, float y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static float textWidth(FontType font, String text, float size) {
        return Render2D.textWidth(font, text, size);
    }

    
    public static final String ICON_BOOLEAN = "Z";
    public static final String ICON_NUMBER = "H";
    public static final String ICON_MODE = "b";
    public static final String ICON_MULTI = "I";
    public static final String ICON_BIND = "a";
    public static final String ICON_STRING = "S";
    public static final String ICON_COLOR = "R";
    public static final String ICON_BUTTON = "U";

    protected static final float SETTING_ICON_SIZE = 8f;
    protected static final float SETTING_ICON_GAP = 3f;
    protected static final float SETTING_ROW_PAD = 2.5f;
    
    protected static final float BIND_ICON_SIZE = 7f;

    protected float iconSize() { return sc(SETTING_ICON_SIZE); }
    protected float iconGap() { return sc(SETTING_ICON_GAP); }
    protected float rowPad() { return sc(SETTING_ROW_PAD); }
    protected float bindIconSize() { return sc(BIND_ICON_SIZE); }

    
    protected float contentX(float x) {
        return x + rowPad() + iconSize() + iconGap();
    }

    protected float contentWidth() {
        return Math.max(sc(8f), width - (rowPad() + iconSize() + iconGap()) - rowPad());
    }

    protected void drawSettingIcon(String icon, float x, float y, float rowH, int alpha) {
        drawSettingIcon(icon, x, y, rowH, alpha, iconSize());
    }

    protected void drawSettingIcon(String icon, float x, float y, float rowH, int alpha, float size) {
        drawSettingIcon(icon, x, y, rowH, alpha, size, FontType.MAINMENUSCREEN);
    }

    protected void drawSettingIcon(String icon, float x, float y, float rowH, int alpha, float size, FontType font) {
        float iy = y + (rowH - size) * 0.5f + sc(1.0f); 
        int col = withAlpha(0xFF8A8E98, alpha);
        Render2D.text(font, icon, x + rowPad(), iy, size, col);
    }

    
    protected static void drawClippedText(FontType font, String text, float x, float y, float maxW, float size, int color) {
        if (text == null || text.isEmpty() || maxW <= 2f) return;
        float tw = textWidth(font, text, size);
        if (tw <= maxW) {
            Render2D.text(font, text, x, y, size, color);
            return;
        }
        String ell = "…";
        float ew = textWidth(font, ell, size);
        if (ew >= maxW) {
            return;
        }
        String cut = text;
        while (cut.length() > 1 && textWidth(font, cut, size) + ew > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        Render2D.text(font, cut + ell, x, y, size, color);
    }

    
    protected static float drawKeycap(String key, float right, float midY, int alpha, boolean accented, boolean listening) {
        String display;
        if (listening) {
            display = "...";
        } else if (key == null || key.isEmpty() || key.equalsIgnoreCase("NONE") || key.equalsIgnoreCase("None")) {
            display = "—";
        } else {
            display = key;
        }

        float textSize = sc(8f);
        float padX = sc(5f);
        float boxH = sc(14f);
        float tw = textWidth(FontType.INTER_SEMI, display, textSize);
        
        float boxW = Math.max(boxH, tw + padX * 2f);
        float boxX = right - boxW;
        float boxY = midY - boxH * 0.5f;
        float radius = sc(3.5f);

        boolean empty = display.equals("—");
        int bg;
        if (listening) {
            bg = themeAccent(Math.round(alpha * 0.88f));
        } else if (accented) {
            bg = themeAccent(Math.round(alpha * 0.50f));
        } else {
            
            bg = rgba(255, 255, 255, Math.round(alpha * 0.08f));
        }
        Render2D.rect(boxX, boxY, boxW, boxH, radius, bg);
        
        int outlineCol = listening
                ? rgba(255, 255, 255, Math.round(alpha * 0.55f))
                : rgba(255, 255, 255, Math.round(alpha * (empty ? 0.28f : 0.22f)));
        Render2D.outline(boxX, boxY, boxW, boxH, radius, 1.0f, outlineCol);

        int tc;
        if (listening || accented) {
            tc = rgba(255, 255, 255, alpha);
        } else if (empty) {
            tc = withAlpha(0xFF7A7E88, alpha);
        } else {
            tc = withAlpha(0xFFD0D2DA, alpha);
        }
        Render2D.text(FontType.INTER_SEMI, display,
                boxX + (boxW - tw) * 0.5f,
                boxY + (boxH - textSize) * 0.5f,
                textSize, tc);
        return boxW;
    }

    protected void drawAnimatedSettingText(FontType font, String text, float x, float y, float availableWidth, float size, int color) {
        float textW = textWidth(font, text, size);
        if (textW <= availableWidth) {
            Render2D.text(font, text, x, y, size, color);
            return;
        }
        float offset = getMarqueeOffset(textW, availableWidth);
        Render2D.pushScissor(null, x, y, availableWidth, size * 1.5f);
        Render2D.text(font, text, x - offset, y, size, color);
        if (offset > 0) {
            Render2D.text(font, text, x - offset + textW + MARQUEE_GAP, y, size, color);
        }
        Render2D.popScissor(null);
    }

    protected float getMarqueeOffset(float textWidth, float availableWidth) {
        if (textWidth <= availableWidth) return 0f;
        long elapsed = System.currentTimeMillis() - hoverStartTime;
        if (elapsed < MARQUEE_PAUSE_MS) return 0f;
        float scrollTime = (elapsed - MARQUEE_PAUSE_MS) / 1000f;
        float totalScroll = textWidth + MARQUEE_GAP;
        float offset = scrollTime * MARQUEE_SPEED_PX_PER_SEC;
        return offset % totalScroll;
    }

    public void updateHoverState(boolean hovered) {
        if (hovered) {
            hoverAnimation.show();
            if (!wasHovered) {
                wasHovered = true;
                hoverStartTime = System.currentTimeMillis();
            }
        } else {
            hoverAnimation.hide();
            wasHovered = false;
        }
    }

    public void updateVisibility(boolean visible) {
        if (!visibilityInit) {
            visibilityInit = true;
            lastVisible = visible;
            if (suppressVisibilityAnim) {
                visibilityAnim.setImmediate(visible);
            } else {
                if (visible) {
                    visibilityAnim.show();
                } else {
                    visibilityAnim.hide();
                }
            }
            return;
        }
        if (visible != lastVisible) {
            lastVisible = visible;
            if (suppressVisibilityAnim) {
                visibilityAnim.setImmediate(visible);
            } else {
                if (visible) {
                    visibilityAnim.show();
                } else {
                    visibilityAnim.hide();
                }
            }
        }
    }

    public float getVisibilityAlpha() {
        return visibilityAnim.getProgress();
    }

    public static int rgba(int r, int g, int b, int a) {
        return ColorUtil.rgba(r, g, b, a);
    }

    public static int withAlpha(int color, int alpha) {
        return ColorUtil.withAlpha(color, alpha);
    }

    public static int scaleAlpha(int color, float alphaMultiplier) {
        return ColorUtil.scaleAlpha(color, alphaMultiplier);
    }

    public static int blend(int first, int second, float factor) {
        return ColorUtil.lerpColor(first, second, factor);
    }

    
    protected static void drawCheckMark(float boxX, float boxY, float boxW, float boxH, int color) {
        float minSide = Math.min(boxW, boxH);
        if (minSide < 4f) return;

        
        float x1 = boxX + boxW * 0.22f;
        float y1 = boxY + boxH * 0.52f;
        float x2 = boxX + boxW * 0.42f;
        float y2 = boxY + boxH * 0.70f;
        float x3 = boxX + boxW * 0.78f;
        float y3 = boxY + boxH * 0.30f;

        float thickness = Math.max(1.35f, minSide * 0.16f);
        drawThickSegment(x1, y1, x2, y2, thickness, color);
        drawThickSegment(x2, y2, x3, y3, thickness, color);
    }

    private static void drawThickSegment(float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;

        
        int steps = Math.max(8, Math.round(len * 2.2f));
        float r = thickness * 0.5f;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float px = x1 + dx * t;
            float py = y1 + dy * t;
            Render2D.rect(px - r, py - r, thickness, thickness, r, color);
        }
    }
}
