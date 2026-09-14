package polaris.api.module.impl.visual;

import polaris.api.drag.core.ElementManager;
import polaris.api.drag.core.ElementScreen;
import polaris.api.drag.core.HudElement;
import polaris.api.drag.impl.*;
import polaris.api.events.impl.DrawEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;
import polaris.utils.render.LoadingVisualGuard;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.blur.BuiltBlur;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.Color;
import java.util.List;

public class Hud extends Module {
    private static final float NO_BLUR_RECT_ALPHA_SCALE = 200.0F / 255.0F;

    private static final String[] ELEMENTS = {
            "Watermark",
            "DynamicIsland",
            "NotificationIsland",
            "Hotbar",
            "Armor",
            "HotKeys",
            "Binds",
            "Potions",
            "Cooldowns",
            "TargetHud",
            "Staff",
            "Info",
            "AI Status",
            "Gapples HUD",
            "Enchanted Gapples HUD",
            "Totems HUD",
            "Fireworks HUD",
            "Crystals HUD",
            "XP Bottles HUD",
            "ScaffoldBlockCounterHud"

    };
    private static Hud instance;

    private final MultiModeSetting elements = register(new MultiModeSetting("Elements", "HUD elements to render.",
            ELEMENTS, ELEMENTS));
    private final BooleanSetting blur = register(new BooleanSetting("Blur", "Use blurred HUD backgrounds.", true));
    private final BooleanSetting glow = register(new BooleanSetting("Glow", "Use glow effects on HUD elements.", true));

    public static final String STYLE_BLUR = "Blur";
    public static final String STYLE_GLASS = "Liquid Glass";
    private final ModeSetting themeStyle = register(new ModeSetting("Style", "Interface background style.", STYLE_BLUR, STYLE_BLUR, STYLE_GLASS));

    private final ColorSetting themeAccent = register(new ColorSetting("Accent", "Client theme accent color.", new Color(120, 180, 255)));
    private final ColorSetting themeBackground = register(new ColorSetting("Background", "HUD background color.", new Color(7, 7, 9)));
    private final NumberSetting themeBlur = register(new NumberSetting("Blur Strength", "HUD background blur radius.", 26.0, 0.0, 60.0, 1.0));
    private final NumberSetting themeOpacity = register(new NumberSetting("Opacity", "HUD background opacity (%).", 94.0, 20.0, 100.0, 1.0));
    private final NumberSetting themeRounding = register(new NumberSetting("Rounding", "HUD corner rounding.", 7.0, 0.0, 16.0, 0.5));
    private final NumberSetting themeGlassStrength = register(new NumberSetting("Glass Strength", "Liquid glass corner smoothness / strength.", 25.0, 1.0, 100.0, 1.0));
    private final NumberSetting themeGlassDistortion = register(new NumberSetting("Glass Distortion", "Liquid glass refraction distortion.", 0.08, -0.2, 0.2, 0.01));
    private final BooleanSetting themeShine = register(new BooleanSetting("Shine", "Animated light streak across HUD elements.", true));
    private final NumberSetting themeShineIntensity = register(new NumberSetting("Shine Intensity", "Strength of the shine streak (%).", 32.0, 0.0, 100.0, 1.0));
    private final NumberSetting themeGlowSize = register(new NumberSetting("Glow Size", "Size of the glow effect around HUD elements.", 6.0, 0.0, 20.0, 0.5));
    private final NumberSetting themeGlowIntensity = register(new NumberSetting("Glow Intensity", "Brightness of the glow effect (%).", 50.0, 0.0, 100.0, 1.0));
    private final StringSetting themeCustomColors = register(new StringSetting("Custom Colors", "User-defined accent presets (CSV of hex).", "", 4096));

    private final List<HudElement> elementsList = List.of(
            new DynamicIsland(),
            new ScaffoldBlockCounterHud(),
            new Hotbar(),
            new Armor(),
            new HotKeys(),
            new Binds(),
            new Potions(),
            new Cooldowns(),
            new TargetHud(),
            new Staff(),
            new Info(),
            new AiStatusHud(),
            new NotificationIsland(),
            new Watermark(),
            new ItemCounterHud(ItemCounterHud.HudType.GAPPLE),
            new ItemCounterHud(ItemCounterHud.HudType.ENCHANTED_GAPPLE),
            new ItemCounterHud(ItemCounterHud.HudType.TOTEM),
            new ItemCounterHud(ItemCounterHud.HudType.FIREWORK),
            new ItemCounterHud(ItemCounterHud.HudType.CRYSTAL),
            new ItemCounterHud(ItemCounterHud.HudType.BOTTLE)


    );

    public Hud() {
        super("HUD", "Renders draggable HUD elements.", ModuleCategory.VISUAL);
        instance = this;
        setEnabled(true);
        themeStyle.setVisible(false);
        themeAccent.setVisible(false);
        themeBackground.setVisible(false);
        themeBlur.setVisible(false);
        themeOpacity.setVisible(false);
        themeRounding.setVisible(false);
        themeGlassStrength.setVisible(false);
        themeGlassDistortion.setVisible(false);
        themeShine.setVisible(false);
        themeShineIntensity.setVisible(false);
        themeGlowSize.setVisible(false);
        themeGlowIntensity.setVisible(false);
        themeCustomColors.setVisible(false);
        
        try {
            polaris.theme.ThemeManager.get().loadFromHud(this);
        } catch (Throwable ignored) {
        }
    }

    public static Hud getInstance() {
        return instance;
    }

    public ColorSetting accentSetting() {
        return themeAccent;
    }

    public ColorSetting backgroundSetting() {
        return themeBackground;
    }

    public NumberSetting blurStrengthSetting() {
        return themeBlur;
    }

    public NumberSetting opacitySetting() {
        return themeOpacity;
    }

    public NumberSetting roundingSetting() {
        return themeRounding;
    }

    public ModeSetting styleSetting() {
        return themeStyle;
    }

    public NumberSetting glassStrengthSetting() {
        return themeGlassStrength;
    }

    public NumberSetting glassDistortionSetting() {
        return themeGlassDistortion;
    }

    public BooleanSetting shineSetting() {
        return themeShine;
    }

    public BooleanSetting glowSetting() {
        return glow;
    }

    public NumberSetting shineIntensitySetting() {
        return themeShineIntensity;
    }

    public NumberSetting glowSizeSetting() {
        return themeGlowSize;
    }

    public NumberSetting glowIntensitySetting() {
        return themeGlowIntensity;
    }

    public StringSetting customColorsSetting() {
        return themeCustomColors;
    }

    public static boolean isShineEnabled() {
        Hud hud = instance;
        return hud == null || hud.themeShine.getValue();
    }

    public static float themeShineIntensity() {
        Hud hud = instance;
        return hud == null ? 0.32f : (float) (hud.themeShineIntensity.getValue() / 100.0);
    }

    public static float themeGlowSize() {
        Hud hud = instance;
        return hud == null ? 6.0f : hud.themeGlowSize.getFloat();
    }

    public static float themeGlowIntensity() {
        Hud hud = instance;
        return hud == null ? 0.5f : (float) (hud.themeGlowIntensity.getValue() / 100.0);
    }

    public static Color themeAccent() {
        Hud hud = instance;
        return hud == null ? new Color(120, 180, 255) : hud.themeAccent.getRawValue();
    }

    public static Color themeBackground() {
        Hud hud = instance;
        return hud == null ? new Color(7, 7, 9) : hud.themeBackground.getRawValue();
    }

    public static float themeBlurStrength() {
        Hud hud = instance;
        return hud == null ? 26.0f : hud.themeBlur.getFloat();
    }

    public static float themeOpacity() {
        Hud hud = instance;
        return hud == null ? 0.94f : (float) (hud.themeOpacity.getValue() / 100.0);
    }

    public static float themeRounding() {
        Hud hud = instance;
        return hud == null ? 7.0f : hud.themeRounding.getFloat();
    }

    public static boolean isGlassMode() {
        Hud hud = instance;
        return hud != null && hud.themeStyle.is(STYLE_GLASS);
    }

    public static float themeGlassStrength() {
        Hud hud = instance;
        return hud == null ? 25.0f : hud.themeGlassStrength.getFloat();
    }

    public static float themeGlassDistortion() {
        Hud hud = instance;
        return hud == null ? 0.08f : hud.themeGlassDistortion.getFloat();
    }

    
    public static boolean shouldRenderCustomHotbar() {
        Hud hud = instance;
        return hud != null && hud.isEnabled() && hud.isElementEnabled("Hotbar");
    }

    
    private static boolean hotbarDrawnThisFrame;

    public static void renderCustomHotbarNow(net.minecraft.client.gui.GuiGraphics graphics, float partialTick) {
        Hud hud = instance;
        if (hud == null || !hud.isEnabled() || !hud.isElementEnabled("Hotbar")) {
            return;
        }
        if (graphics == null || LoadingVisualGuard.shouldSuppressHud(hud.mc)) {
            return;
        }
        try {
            Render2D.beginFrame(graphics);
            RenderItem.beginFrame(graphics);
            for (HudElement element : hud.elementsList) {
                if (element instanceof Hotbar hotbar) {
                    hotbar.render();
                    hotbarDrawnThisFrame = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
            
        } finally {
            try {
                RenderItem.flush();
            } catch (Throwable ignored) {
            }
            try {
                Render2D.flush();
            } catch (Throwable ignored) {
            }
        }
    }

    
    public boolean isElementEnabled(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        java.util.Set<String> selected = elements.getValue();
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        return elements.isSelected(name);
    }

    
    public void setElementEnabled(String name, boolean enabled) {
        if (name == null || name.isEmpty()) {
            return;
        }
        java.util.Set<String> selected = elements.getValue();
        if (selected == null || selected.isEmpty()) {
            for (String element : ELEMENTS) {
                elements.setSelected(element, true);
            }
        }
        elements.setSelected(name, enabled);
    }

    public static boolean isBlurEnabled() {
        Hud hud = instance;
        return hud == null || hud.blur.getValue();
    }

    public static boolean isGlowEnabled() {
        Hud hud = instance;
        return hud == null || hud.glow.getValue();
    }

    public static void renderHudBackground(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        if (isGlassMode()) {
            renderGlassBackground(x, y, width, height, radius, color);
            return;
        }
        if (isBlurEnabled()) {
            Render2D.blur(x, y, width, height, radius, blurRadius, smoothness, color);
            return;
        }
        Render2D.rect(x, y, width, height, radius, noBlurRectColor(color));
    }

    public static void renderHudBackground(BuiltBlur blur) {
        if (blur == null) {
            return;
        }
        if (isGlassMode()) {
            renderGlassBackground(
                    blur.x(),
                    blur.y(),
                    blur.width(),
                    blur.height(),
                    blur.radiusTopLeft(),
                    blur.color()
            );
            return;
        }
        if (isBlurEnabled()) {
            Render2D.blur(blur);
            return;
        }
        Render2D.rect(
                blur.x(),
                blur.y(),
                blur.width(),
                blur.height(),
                blur.radiusTopLeft(),
                blur.radiusTopRight(),
                blur.radiusBottomRight(),
                blur.radiusBottomLeft(),
                noBlurRectColor(blur.color())
        );
    }

    private static void renderGlassBackground(float x, float y, float width, float height, float radius, int color) {
        float fresnelPower = Math.max(themeGlassStrength() * 2.0f, 1.0f);
        float distortion = themeGlassDistortion();
        float globalAlpha = ((color >>> 24) & 0xFF) / 255.0f;
        Render2D.glass(
                x,
                y,
                width,
                height,
                radius,
                color,
                globalAlpha,
                fresnelPower,
                color | 0xFF000000,
                1.0f,
                true,
                0.0f,
                distortion,
                2.0f,
                0.0f
        );
    }

    public static void renderHudGlow(String texture, float x, float y, float width, float height, float radius, int color) {
        if (isGlowEnabled()) {
            Render2D.image(texture, x, y, width, height, radius, color);
        }
    }

    private static int noBlurRectColor(int color) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.round(alpha * NO_BLUR_RECT_ALPHA_SCALE);
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }

    public void renderHudLayer(DrawEvent event) {
        if (mc.player == null || mc.level == null || mc.getWindow() == null || LoadingVisualGuard.shouldSuppressHud(mc)) {
            return;
        }

        ElementScreen screen = ElementScreen.current();
        ElementManager elementManager = ElementManager.getInstance();
        elementManager.frame(screen);
        if (event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY) {
            elementManager.updateActiveElementFromMouse();
        }
        RenderItem.beginFrame(event.getGraphics());
        try {
            boolean skipHotbar = hotbarDrawnThisFrame && event.getLayer() == DrawEvent.Layer.GAME;
            for (HudElement element : elementsList) {
                String name = elementName(element);
                boolean selected = isElementEnabled(name);
                setElementVisible(element, selected);
                if (!selected) {
                    continue;
                }
                
                if (skipHotbar && element instanceof Hotbar) {
                    continue;
                }
                element.render();
            }
        } finally {
            RenderItem.flush();
            
            if (event.getLayer() == DrawEvent.Layer.GAME || event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY) {
                hotbarDrawnThisFrame = false;
            }
        }

        boolean editLayer = event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY && elementManager.canEditCurrentScreen();
        if (editLayer) {
            elementManager.renderEditorOverlay(event.getGraphics(), screen);
        }
    }

    public boolean handleMouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!isEnabled() || event == null || event.button() != 0) {
            return false;
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (elements.isSelected(elementName(element)) && element.mouseClicked(event, doubled)) {
                return true;
            }
        }
        return false;
    }

    private String elementName(HudElement element) {
        if (element instanceof HudPanel panel) {
            return panel.elementName();
        }
        return element.getClass().getSimpleName();
    }

    private void setElementVisible(HudElement element, boolean visible) {
        if (element instanceof HudPanel panel) {
            panel.setHudVisible(visible);
        }
    }
}
