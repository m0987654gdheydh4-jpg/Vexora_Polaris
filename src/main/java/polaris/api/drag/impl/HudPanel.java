package polaris.api.drag.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import polaris.api.drag.core.ElementComponent;
import polaris.api.drag.core.ElementManager;
import polaris.api.drag.core.ElementScreen;
import polaris.api.drag.core.HudElement;
import polaris.api.settings.bind.KeyBind;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

public abstract class HudPanel implements HudElement {
    protected static final FontType TITLE_FONT = FontType.BOLD;
    protected static final FontType TEXT_FONT = FontType.BOLD;

    
    protected static final int BG_PRIMARY = ColorUtil.rgba(18, 18, 18, 235);
    protected static final int BG_SECONDARY = ColorUtil.rgba(18, 18, 18, 235);
    protected static final int BORDER_COLOR = ColorUtil.rgba(70, 70, 70, 235);
    protected static final int TEXT_COLOR = ColorUtil.rgba(255, 255, 255, 255);
    protected static final int TEXT_SECONDARY = ColorUtil.rgba(155, 155, 155, 255);
    protected static final int TEXT_TERTIARY = ColorUtil.rgba(165, 165, 165, 255);
    protected static final int BAR_BG = ColorUtil.rgba(45, 45, 45, 255);

    
    protected static int accentColor() {
        return HudTheme.current().accentColor;
    }

    
    protected static final float CORNER_LARGE = 8f;
    protected static final float CORNER_MEDIUM = 5f;
    protected static final float CORNER_SMALL = 3f;
    protected static final float CORNER_TINY = 2f;
    protected static final float OUTLINE_THICKNESS = 0.5f;

    protected static final int MUTED_COLOR = ColorUtil.rgba(155, 155, 155, 215);
    private static final float CONTENT_ANIM = 0.24F;
    private static final String[] ASCII_CHARS = new String[128];

    static {
        for (int i = 0; i < ASCII_CHARS.length; i++) {
            ASCII_CHARS[i] = Character.toString((char) i);
        }
    }

    protected final Minecraft mc = Minecraft.getInstance();
    protected final ElementComponent drag;
    private final String elementName;
    private float animatedWidth;
    private float animatedHeight;
    private long lastFrameMs = System.currentTimeMillis();
    private final SmoothAnimation contentAnimation = new SmoothAnimation();
    private boolean enabled = true;

    protected HudPanel(String id, String title, float defaultX, float defaultY, float width, float height) {
        this.elementName = title;
        this.drag = ElementManager.getInstance()
                .register("hud." + id, title, defaultX, defaultY)
                .minimumSize(12.0F, 12.0F);
        this.animatedWidth = width;
        this.animatedHeight = height;
    }

    public String elementName() {
        return elementName;
    }

    public void setHudVisible(boolean visible) {
        enabled = visible;
        drag.visible(visible);
    }

    protected boolean selected() {
        return enabled;
    }

    protected void contentVisible(boolean visible) {
        drag.visible(enabled && visible);
    }

    protected float contentAlpha(boolean targetVisible) {
        contentAnimation.update();
        contentAnimation.run(targetVisible ? 1.0 : 0.0, CONTENT_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        float alpha = contentAnimation.get();
        contentVisible(targetVisible || alpha > 0.01F || contentAnimation.isAlive());
        return alpha;
    }

    protected boolean editPreview() {
        return mc.screen instanceof ChatScreen;
    }

    
    protected static final float ROW_APPEAR = 0.36F;
    protected static final float ROW_LEAVE = 0.28F;
    protected static final float ROW_MOVE = 0.32F;
    protected static final float ROW_SLIDE_IN = 16.0F;
    protected static final float ROW_SLIDE_OUT = 10.0F;
    protected static final float ROW_SPAWN_Y = 12.0F;

    protected void size(float targetWidth, float targetHeight) {
        float delta = deltaSeconds();
        
        float wSpeed = targetWidth > animatedWidth ? 10.0F : 8.0F;
        float hSpeed = targetHeight > animatedHeight ? 11.0F : 8.5F;
        animatedWidth = smooth(animatedWidth, targetWidth, delta, wSpeed);
        animatedHeight = smooth(animatedHeight, targetHeight, delta, hSpeed);
        if (Math.abs(animatedWidth - targetWidth) < 0.2F) {
            animatedWidth = targetWidth;
        }
        if (Math.abs(animatedHeight - targetHeight) < 0.2F) {
            animatedHeight = targetHeight;
        }
        drag.size((float) Math.ceil(animatedWidth), (float) Math.ceil(animatedHeight));
        drag.clamp(ElementScreen.current());
    }

    
    protected static float rowAppearLift(float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha));
        return (1f - a) * 6f;
    }

    protected String trimToWidth(String text, FontType font, float size, float width) {
        if (text == null) {
            return "";
        }
        if (Render2D.textWidth(font, text, size) <= width) {
            return text;
        }
        String suffix = "..";
        if (Render2D.textWidth(font, suffix, size) > width) {
            return suffix;
        }

        int low = 0;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            String candidate = text.substring(0, mid) + suffix;
            if (Render2D.textWidth(font, candidate, size) <= width) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best <= 0 ? suffix : text.substring(0, best) + suffix;
    }

    protected String formatDurationTicks(int ticks) {
        if (ticks < 0) {
            return "inf";
        }
        int totalSeconds = Math.max(0, ticks / 20);
        return totalSeconds / 60 + ":" + twoDigits(totalSeconds % 60);
    }

    protected static String twoDigits(int value) {
        int safe = Math.max(0, Math.min(99, value));
        return ASCII_CHARS['0' + safe / 10] + ASCII_CHARS['0' + safe % 10];
    }

    protected static String timerText(int ticks) {
        if (ticks < 0) {
            return "**:**";
        }
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = Math.min(99, totalSeconds / 60);
        int seconds = totalSeconds % 60;
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    protected static String charText(char c) {
        return c < ASCII_CHARS.length ? ASCII_CHARS[c] : Character.toString(c);
    }

    protected String shortBind(KeyBind bind) {
        if (bind == null || !bind.isBound()) {
            return "None";
        }
        return bind.getDisplayName()
                .replace("MOUSE ", "M")
                .replace("L SHIFT", "LSH")
                .replace("R SHIFT", "RSH")
                .replace("L CTRL", "LCT")
                .replace("R CTRL", "RCT")
                .replace("SPACE", "SPC");
    }

    protected static float smooth(float current, float target, float deltaSeconds, float speed) {
        float factor = (float) (1.0D - Math.pow(0.001D, Math.max(0.0F, deltaSeconds) * speed));
        return current + (target - current) * factor;
    }

    protected static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    
    protected static void drawPanel(float x, float y, float width, float height, int alpha, float radius) {
        drawGlow(x, y, width, height, alpha, radius);
        drawBackground(x, y, width, height, alpha, radius);
        drawShine(x, y, width, height, radius, Math.max(0, Math.min(255, alpha)) / 255f);
        drawOutline(x, y, width, height, OUTLINE_THICKNESS, alpha, radius);
    }

    protected static void drawGlow(float x, float y, float width, float height, int alpha, float radius) {
        if (alpha <= 0 || width <= 1f || height <= 1f) return;
        if (!polaris.api.module.impl.visual.Hud.isGlowEnabled()) return;

        float glowSize = polaris.api.module.impl.visual.Hud.themeGlowSize();
        float glowIntensity = polaris.api.module.impl.visual.Hud.themeGlowIntensity();
        if (glowSize <= 0.01f || glowIntensity <= 0.001f) return;

        int accent = accentColor();
        int aR = (accent >> 16) & 0xFF;
        int aG = (accent >> 8) & 0xFF;
        int aB = accent & 0xFF;

        float r = themedRadius(radius);
        int layers = 5;
        for (int i = layers; i >= 1; i--) {
            float expand = glowSize * (i / (float) layers);
            float layerAlpha = glowIntensity * (1.0f - (i - 1) / (float) layers) * 0.55f;
            int la = Math.min(255, Math.max(0, Math.round(layerAlpha * 255f * (alpha / 255f))));
            int color = ColorUtil.rgba(aR, aG, aB, la);
            Render2D.rect(x - expand, y - expand, width + expand * 2, height + expand * 2, r + expand, color);
        }
    }

    
    protected static void drawShine(float x, float y, float width, float height, float radius, float alpha) {
        if (alpha <= 0.01f || width <= 1f || height <= 1f) {
            return;
        }
        if (!polaris.api.module.impl.visual.Hud.isShineEnabled()) {
            return;
        }
        float intensity = polaris.api.module.impl.visual.Hud.themeShineIntensity();
        if (intensity <= 0.001f) {
            return;
        }
        long period = 3600L;
        float progress = (System.currentTimeMillis() % period) / (float) period;
        float r = themedRadius(radius);
        
        Render2D.shine(x, y, width, height, r, 1.2f, progress, -1.0f, -1.0f, 0.32f, alpha * intensity, 0xFFFFFFFF);
    }

    protected static void drawBackground(float x, float y, float width, float height, int alpha, float radius) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        HudTheme theme = HudTheme.current();
        int themedAlpha = Math.round(clampedAlpha * theme.hudOpacity);
        int color = theme.backgroundWithAlpha(themedAlpha);
        HudRenderCompat.background(x, y, width, height, themedRadius(radius), HudTheme.BLUR_RADIUS, HudTheme.BLUR_SMOOTHNESS, color);
    }

    protected static void drawOutline(float x, float y, float width, float height, float thickness, int alpha, float radius) {
        if (polaris.api.module.impl.visual.Hud.isGlassMode()) {
            return; 
        }
        int outlineAlpha = Math.max(0, Math.min(255, alpha));
        float th = Math.max(thickness, 0f);
        if (th <= 0f) return;
        int color = HudTheme.current().outlineWithAlpha((int)(outlineAlpha * 0.55f));
        Render2D.outline(x, y, width, height, themedRadius(radius), th, color);
    }

    protected static int withAlpha(int baseColor, int alpha) {
        return (baseColor & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    
    protected static void drawPanelHeader(float x, float y, float w, String title, String menuIcon, int alpha) {
        float titleSize = 8f;
        float padL = 8f;
        float padR = 8f;
        float headerH = 20f;
        float titleY = y + 6.5f;
        Render2D.text(TEXT_FONT, title, x + padL, titleY, titleSize, withAlpha(TEXT_COLOR, alpha));

        if (menuIcon != null && !menuIcon.isEmpty()) {
            float iconSize = 8.5f;
            float iconW = Render2D.textWidth(FontType.MAINMENUSCREEN, menuIcon, iconSize);
            float iconX = x + w - padR - iconW;
            float iconY = y + (headerH - iconSize) * 0.5f + 0.4f;
            Render2D.text(FontType.MAINMENUSCREEN, menuIcon, iconX, iconY, iconSize,
                    withAlpha(accentColor(), alpha));
        }

        
        float lineY = y + headerH - 1.0f;
        float lineH = 0.7f;
        int lineA = Math.max(0, Math.min(255, Math.round(alpha * 0.14f)));
        Render2D.rect(x, lineY, w, lineH, 0f, ColorUtil.rgba(255, 255, 255, lineA));
    }

    
    protected static void drawIosIndicator(float panelX, float panelY,
                                           float panelWidth, float panelHeight, int alpha) {
        float w = Math.min(40f, panelWidth * 0.32f);
        float h = 2.2f;
        float x = panelX + (panelWidth - w) / 2f;
        float y = panelY + panelHeight - h - 1.5f;
        int color = HudTheme.current().accentWithAlpha(Math.max(0, Math.min(255, alpha)));
        Render2D.rect(x, y, w, h, h / 2f, color);
    }

    private float deltaSeconds() {
        long now = System.currentTimeMillis();
        float delta = Math.min(0.1F, Math.max(0.0F, (now - lastFrameMs) / 1000.0F));
        lastFrameMs = now;
        return delta;
    }

    private static float themedRadius(float fallback) {
        float themeRadius = HudTheme.current().hudRounding;
        return themeRadius <= 0.0f ? fallback : themeRadius;
    }
}

