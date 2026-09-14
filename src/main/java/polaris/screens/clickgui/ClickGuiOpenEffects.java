package polaris.screens.clickgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import polaris.api.drag.impl.HudTheme;
import polaris.api.module.impl.misc.ClickGuiModule;
import polaris.api.module.impl.visual.frageffect.FragEffectScanRenderer;
import polaris.manager.Manager;
import polaris.utils.render.RenderCompatibility;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.post.holoblur.HoloBlurRenderer;
import polaris.utils.render.ui.Render2D;


public final class ClickGuiOpenEffects {
    
    public static final float CAMERA_MODIFIER = 4.0f;
    
    public static final float OPEN_SCALE_START = 0.5f;
    
    public static final int DARKNESS_MAX_ALPHA = 128;
    private static final float SCAN_SPEED = 2.0f;

    private ClickGuiOpenEffects() {
    }

    public static ClickGuiModule module() {
        var modules = Manager.getModules();
        if (modules == null) {
            return null;
        }
        return modules.getByType(ClickGuiModule.class).orElse(null);
    }

    public static boolean openScaleEnabled() {
        ClickGuiModule m = module();
        return m == null || Boolean.TRUE.equals(m.openScaleSetting().getValue());
    }

    public static boolean openDarknessEnabled() {
        ClickGuiModule m = module();
        return m == null || Boolean.TRUE.equals(m.openDarknessSetting().getValue());
    }

    public static boolean openCameraEnabled() {
        ClickGuiModule m = module();
        return m == null || Boolean.TRUE.equals(m.openCameraSetting().getValue());
    }

    
    private static float backdropProgress;
    private static float backdropCenterX = 0.5f;
    private static float backdropCenterY = 0.5f;
    private static long backdropFrameMs;

    
    public static void reportBackdrop(float progress) {
        backdropProgress = clamp01(progress);
        backdropFrameMs = System.currentTimeMillis();
    }

    
    public static void captureBackdropCenter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
            backdropCenterX = 0.5f;
            backdropCenterY = 0.5f;
            return;
        }
        int w = mc.getWindow().getScreenWidth();
        int h = mc.getWindow().getScreenHeight();
        backdropCenterX = w <= 0 ? 0.5f : clamp01((float) (mc.mouseHandler.xpos() / w));
        backdropCenterY = h <= 0 ? 0.5f : clamp01((float) (mc.mouseHandler.ypos() / h));
    }

    
    public static void renderBackdrop() {
        ClickGuiModule module = module();
        if (module == null || !module.isHologramBackground() || HoloBlurRenderer.isDisabledAfterError()) {
            return;
        }
        
        if (System.currentTimeMillis() - backdropFrameMs > 120L || backdropProgress <= 0.01f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.mouseHandler == null) {
            return;
        }
        int w = mc.getWindow().getScreenWidth();
        int h = mc.getWindow().getScreenHeight();
        float mouseX = w <= 0 ? 0.5f : clamp01((float) (mc.mouseHandler.xpos() / w));
        float mouseY = h <= 0 ? 0.5f : clamp01((float) (mc.mouseHandler.ypos() / h));
        float time = (System.currentTimeMillis() % 3_600_000L) / 1000.0f;

        HoloBlurRenderer.apply(mouseX, mouseY, backdropProgress, backdropCenterX, backdropCenterY,
                time, module.holoShaderParams(backdropProgress));
    }

    
    public static boolean hologramBackdropActive() {
        ClickGuiModule module = module();
        return module != null && module.isHologramBackground() && !HoloBlurRenderer.isDisabledAfterError();
    }

    public static boolean moduleAppearEnabled() {
        ClickGuiModule m = module();
        return m == null || Boolean.TRUE.equals(m.moduleAppearSetting().getValue());
    }

    public static boolean openScanEnabled() {
        ClickGuiModule m = module();
        return m != null && Boolean.TRUE.equals(m.openScanSetting().getValue());
    }

    
    public static void onGuiOpening() {
        if (openScanEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc != null ? mc.player : null;
            if (player != null && !RenderCompatibility.shouldDisableFragEffectScanShader()
                    && !FragEffectScanRenderer.isDisabledAfterError()) {
                
                Vec3 center = player.position().add(0.0, 0.12, 0.0);
                FragEffectScanRenderer.pingMenuOpen(center);
            }
        } else {
            FragEffectScanRenderer.clearMenuWaves();
        }
    }

    
    public static void onGuiClosing() {
        
        FragEffectScanRenderer.clearMenuWaves();
    }

    
    public static void renderScanIfNeeded(RenderTarget target, Matrix4f projection, Matrix4f view, Vec3 cameraPos) {
        if (RenderCompatibility.shouldDisableFragEffectScanShader()
                || FragEffectScanRenderer.isDisabledAfterError()) {
            return;
        }
        if (!FragEffectScanRenderer.hasActiveWaves()) {
            return;
        }

        int accent = HudTheme.current().accentColor;
        int outer = lighten(accent, 0.45f);
        int mid = lighten(accent, 0.18f);
        int inner = darken(accent, 0.18f);
        int scanline = lighten(accent, 0.6f);
        int flickOuter = lighten(accent, 0.7f);
        int flickMid = ColorUtil.interpolateColor(accent, ColorUtil.WHITE, 0.28f);
        int flickInner = darken(accent, 0.08f);
        int flickScan = darken(accent, 0.35f);

        FragEffectScanRenderer.render(
                target, projection, view, cameraPos, SCAN_SPEED,
                outer, mid, inner, scanline,
                flickOuter, flickMid, flickInner, flickScan
        );
    }

    private static int lighten(int color, float amount) {
        float c = Math.max(0f, Math.min(1f, amount));
        int r = ColorUtil.getRed(color);
        int g = ColorUtil.getGreen(color);
        int b = ColorUtil.getBlue(color);
        return ColorUtil.rgba(
                Math.round(r + (255 - r) * c),
                Math.round(g + (255 - g) * c),
                Math.round(b + (255 - b) * c),
                255
        );
    }

    private static int darken(int color, float amount) {
        float c = Math.max(0f, Math.min(1f, amount));
        return ColorUtil.rgba(
                Math.round(ColorUtil.getRed(color) * (1f - c)),
                Math.round(ColorUtil.getGreen(color) * (1f - c)),
                Math.round(ColorUtil.getBlue(color) * (1f - c)),
                255
        );
    }

    
    public static float openScale(float progress) {
        float p = clamp01(progress);
        if (!openScaleEnabled()) {
            return 1.0f;
        }
        return OPEN_SCALE_START + (1.0f - OPEN_SCALE_START) * p;
    }

    
    public static int darknessAlpha(float progress) {
        if (!openDarknessEnabled()) {
            return 0;
        }
        float p = clamp01(progress);
        float ease = p * p * (3.0f - 2.0f * p);
        return Math.round(DARKNESS_MAX_ALPHA * ease);
    }

    
    public static void renderDarkness(float progress, float width, float height) {
        int alpha = darknessAlpha(progress);
        if (hologramBackdropActive()) {
            
            alpha = Math.round(14 * clamp01(progress));
        }
        if (alpha <= 1) {
            return;
        }
        Render2D.rect(-5f, -5f, width + 10f, height + 10f, 0f, ColorUtil.rgba(0, 0, 0, alpha));
    }

    
    public static float[] captureLook() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) {
            return new float[]{0f, 0f};
        }
        return new float[]{player.getYRot(), player.getXRot()};
    }

    
    public static float cameraOffsetX(float baseYaw) {
        if (!openCameraEnabled()) {
            return 0f;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) {
            return 0f;
        }
        return (baseYaw - player.getYRot()) * CAMERA_MODIFIER;
    }

    public static float cameraOffsetY(float basePitch) {
        if (!openCameraEnabled()) {
            return 0f;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) {
            return 0f;
        }
        return (basePitch - player.getXRot()) * CAMERA_MODIFIER;
    }

    
    public static double unprojectX(double mouseX, float camX, float centerX, float scale) {
        float s = scale <= 1.0e-4f ? 1.0f : scale;
        return ((mouseX - camX) - centerX) / s + centerX;
    }

    public static double unprojectY(double mouseY, float camY, float centerY, float scale) {
        float s = scale <= 1.0e-4f ? 1.0f : scale;
        return ((mouseY - camY) - centerY) / s + centerY;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
