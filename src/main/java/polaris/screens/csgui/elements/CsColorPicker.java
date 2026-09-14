package polaris.screens.csgui.elements;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import polaris.api.settings.impl.ColorSetting;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.awt.Color;


public final class CsColorPicker {
    private static final CsColorPicker INSTANCE = new CsColorPicker();
    private static final float WIDTH = 132f;
    private static final float HEIGHT = 142f;
    private static final int[] HUE_COLORS = makeHueColors();

    private float x, y;
    private float hue, saturation = 1f, brightness = 1f, alphaValue = 1f;
    private boolean draggingWindow;
    private float dragOffX, dragOffY;
    private int dragTarget; 
    private boolean editingHex;
    private String hexText = "";
    private ColorSetting currentSetting;
    private boolean open;
    private final SimpleLinearAnimation animation = new SimpleLinearAnimation(180);

    private float svX, svY, svSize;
    private float hueX, hueY, hueW, hueH;
    private float alphaX, alphaY, alphaW, alphaH;
    private float hexX, hexY, hexW, hexH;
    private float previewX, previewY, previewW, previewH;

    private float clampMinX, clampMinY, clampMaxX, clampMaxY;
    private boolean hasClamp;

    private CsColorPicker() {}

    public static CsColorPicker get() {
        return INSTANCE;
    }

    public void open(ColorSetting setting, float openX, float openY) {
        this.currentSetting = setting;
        this.x = openX;
        this.y = openY;
        applyColor(setting.getRawValue());
        this.open = true;
        animation.show();
        editingHex = false;
        clampToBounds();
    }

    public void setClampBounds(float minX, float minY, float maxX, float maxY) {
        this.clampMinX = minX;
        this.clampMinY = minY;
        this.clampMaxX = maxX;
        this.clampMaxY = maxY;
        this.hasClamp = true;
        clampToBounds();
    }

    public void clearClamp() {
        hasClamp = false;
    }

    private void clampToBounds() {
        if (!hasClamp) return;
        if (x + WIDTH > clampMaxX) x = clampMaxX - WIDTH;
        if (y + HEIGHT > clampMaxY) y = clampMaxY - HEIGHT;
        if (x < clampMinX) x = clampMinX;
        if (y < clampMinY) y = clampMinY;
    }

    public void close() {
        if (open) {
            open = false;
            animation.hide();
        }
        draggingWindow = false;
        dragTarget = 0;
        editingHex = false;
    }

    public boolean isOpen() {
        return open || animation.getProgress() > 0.01f;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        float progress = animation.getProgress();
        if (progress <= 0.005f) return;
        if (open) animation.show();

        if (dragTarget != 0 || draggingWindow) {
            long w = GLFW.glfwGetCurrentContext();
            if (w != 0 && GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                if (draggingWindow) {
                    x = mouseX - dragOffX;
                    y = mouseY - dragOffY;
                    clampToBounds();
                } else if (dragTarget == 1) updateSV(mouseX, mouseY);
                else if (dragTarget == 2) updateHue(mouseY);
                else if (dragTarget == 3) updateAlpha(mouseY);
            } else {
                dragTarget = 0;
                draggingWindow = false;
            }
        }

        layout();
        int a = Math.round(255 * progress);
        int bg = ColorUtil.rgba(12, 13, 18, Math.round(240 * progress));
        int accent = CsSettingComponent.themeAccent;

        Render2D.rect(x, y, WIDTH, HEIGHT, 7f, bg);
        Render2D.outline(x, y, WIDTH, HEIGHT, 7f, 0.65f,
                ColorUtil.rgba(255, 255, 255, Math.round(24 * progress)));

        Render2D.rect(x, y, WIDTH, 17f, 7f, 7f, 0, 0, ColorUtil.rgba(255, 255, 255, Math.round(7 * progress)));
        Render2D.text(FontType.INTER_SEMI, "Color", x + 7f, y + 4.5f, 8f, ColorUtil.rgba(220, 222, 230, a));
        Render2D.text(FontType.INTER_MEDIUM, "x", x + WIDTH - 12f, y + 4f, 8f, ColorUtil.rgba(200, 90, 90, a));

        drawSV(progress);
        drawHue(progress);
        drawAlpha(progress);
        drawHex(a);
        drawPreview(progress);

        Render2D.rect(x + 7f, y + 16f, 22f, 1.1f, 0.5f, ColorUtil.withAlpha(accent, Math.round(200 * progress)));
    }

    private void layout() {
        float pad = 7f;
        float titleH = 18f;
        svSize = 84f;
        svX = x + pad;
        svY = y + titleH + 3f;

        float sliderW = 7f;
        float gap = 4f;
        hueX = svX + svSize + gap;
        hueY = svY;
        hueW = sliderW;
        hueH = svSize;

        alphaX = hueX + hueW + gap;
        alphaY = svY;
        alphaW = sliderW;
        alphaH = svSize;

        float bottomY = svY + svSize + 6f;
        hexX = svX;
        hexY = bottomY;
        hexW = 62f;
        hexH = 16f;

        previewX = hexX + hexW + 5f;
        previewY = bottomY;
        previewW = WIDTH - pad * 2f - hexW - 5f;
        previewH = 16f;
    }

    private void drawSV(float p) {
        int hueColor = Color.HSBtoRGB(hue, 1f, 1f);
        int a = Math.round(255 * p);
        int hr = ColorUtil.getRed(hueColor), hg = ColorUtil.getGreen(hueColor), hb = ColorUtil.getBlue(hueColor);
        Render2D.rect(svX, svY, svSize, svSize, 5f,
                ColorUtil.rgba(255, 255, 255, a),
                ColorUtil.rgba(hr, hg, hb, a),
                ColorUtil.rgba(hr, hg, hb, a),
                ColorUtil.rgba(255, 255, 255, a));
        Render2D.rect(svX, svY, svSize, svSize, 5f,
                0x00000000, 0x00000000,
                ColorUtil.rgba(0, 0, 0, a), ColorUtil.rgba(0, 0, 0, a));

        float sx = svX + saturation * svSize;
        float sy = svY + (1f - brightness) * svSize;
        Render2D.outline(sx - 3f, sy - 3f, 6f, 6f, 3f, 1f, ColorUtil.rgba(255, 255, 255, Math.round(240 * p)));
        Render2D.rect(sx - 1.5f, sy - 1.5f, 3f, 3f, 1.5f, ColorUtil.rgba(20, 22, 28, Math.round(200 * p)));
    }

    private void drawHue(float p) {
        float seg = hueH / (HUE_COLORS.length - 1);
        int a = Math.round(255 * p);
        for (int i = 0; i < HUE_COLORS.length - 1; i++) {
            int top = HUE_COLORS[i], bot = HUE_COLORS[i + 1];
            float y0 = hueY + i * seg;
            Render2D.rect(hueX, y0, hueW, seg + 0.6f, 0f,
                    ColorUtil.rgba(ColorUtil.getRed(top), ColorUtil.getGreen(top), ColorUtil.getBlue(top), a),
                    ColorUtil.rgba(ColorUtil.getRed(top), ColorUtil.getGreen(top), ColorUtil.getBlue(top), a),
                    ColorUtil.rgba(ColorUtil.getRed(bot), ColorUtil.getGreen(bot), ColorUtil.getBlue(bot), a),
                    ColorUtil.rgba(ColorUtil.getRed(bot), ColorUtil.getGreen(bot), ColorUtil.getBlue(bot), a));
        }
        float selY = hueY + hue * hueH;
        Render2D.rect(hueX - 1.5f, selY - 1.2f, hueW + 3f, 2.4f, 1f, ColorUtil.rgba(255, 255, 255, Math.round(255 * p)));
    }

    private void drawAlpha(float p) {
        int rgb = currentRgb();
        int r = ColorUtil.getRed(rgb), g = ColorUtil.getGreen(rgb), b = ColorUtil.getBlue(rgb);
        Render2D.rect(alphaX, alphaY, alphaW, alphaH, 2f,
                ColorUtil.rgba(r, g, b, 0),
                ColorUtil.rgba(r, g, b, Math.round(255 * p)),
                ColorUtil.rgba(r, g, b, Math.round(255 * p)),
                ColorUtil.rgba(r, g, b, 0));
        float selY = alphaY + (1f - alphaValue) * alphaH;
        Render2D.rect(alphaX - 1.5f, selY - 1.2f, alphaW + 3f, 2.4f, 1f, ColorUtil.rgba(255, 255, 255, Math.round(255 * p)));
    }

    private void drawHex(int a) {
        Render2D.rect(hexX, hexY, hexW, hexH, 4f, ColorUtil.rgba(255, 255, 255, Math.round(10 * (a / 255f))));
        Render2D.outline(hexX, hexY, hexW, hexH, 4f, 0.6f,
                editingHex
                        ? ColorUtil.withAlpha(CsSettingComponent.themeAccent, a)
                        : ColorUtil.rgba(255, 255, 255, Math.round(40 * (a / 255f))));
        String display = editingHex ? ("#" + hexText) : String.format("#%06X", currentRgb() & 0xFFFFFF);
        Render2D.text(FontType.INTER_MEDIUM, display, hexX + 5f, hexY + (hexH - 8.5f) * 0.5f, 8.5f,
                ColorUtil.rgba(210, 212, 220, a));
    }

    private void drawPreview(float p) {
        int rgb = currentRgb();
        int col = ColorUtil.rgba(ColorUtil.getRed(rgb), ColorUtil.getGreen(rgb), ColorUtil.getBlue(rgb),
                Math.round(alphaValue * 255 * p));
        Render2D.rect(previewX, previewY, previewW, previewH, 4f, col);
        Render2D.outline(previewX, previewY, previewW, previewH, 4f, 0.6f,
                ColorUtil.rgba(255, 255, 255, Math.round(40 * p)));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || button != 0) return false;
        float mx = (float) mouseX, my = (float) mouseY;
        layout();

        if (mx >= x + WIDTH - 18 && mx <= x + WIDTH && my >= y && my <= y + 20) {
            close();
            return true;
        }
        if (mx >= svX && mx <= svX + svSize && my >= svY && my <= svY + svSize) {
            dragTarget = 1;
            updateSV(mx, my);
            return true;
        }
        if (mx >= hueX && mx <= hueX + hueW && my >= hueY && my <= hueY + hueH) {
            dragTarget = 2;
            updateHue(my);
            return true;
        }
        if (mx >= alphaX && mx <= alphaX + alphaW && my >= alphaY && my <= alphaY + alphaH) {
            dragTarget = 3;
            updateAlpha(my);
            return true;
        }
        if (mx >= hexX && mx <= hexX + hexW && my >= hexY && my <= hexY + hexH) {
            editingHex = true;
            hexText = String.format("%06X", currentRgb() & 0xFFFFFF);
            return true;
        } else if (editingHex) {
            commitHex();
            editingHex = false;
        }
        if (mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEIGHT) {
            if (my <= y + 20) {
                draggingWindow = true;
                dragOffX = mx - x;
                dragOffY = my - y;
            }
            return true;
        }
        close();
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        draggingWindow = false;
        if (dragTarget != 0) {
            dragTarget = 0;
            return true;
        }
        return false;
    }

    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!editingHex) {
            if (key == GLFW.GLFW_KEY_ESCAPE && isOpen()) {
                close();
                return true;
            }
            return false;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            commitHex();
            editingHex = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !hexText.isEmpty()) {
            hexText = hexText.substring(0, hexText.length() - 1);
            return true;
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!editingHex || hexText.length() >= 6) return false;
        String c = String.valueOf(codePoint).toUpperCase();
        if (c.matches("[0-9A-F]")) {
            hexText += c;
            return true;
        }
        return false;
    }

    private void updateSV(float mx, float my) {
        saturation = clamp((mx - svX) / svSize, 0, 1);
        brightness = 1f - clamp((my - svY) / svSize, 0, 1);
        syncSetting();
    }

    private void updateHue(float my) {
        hue = clamp((my - hueY) / hueH, 0, 1);
        syncSetting();
    }

    private void updateAlpha(float my) {
        alphaValue = 1f - clamp((my - alphaY) / alphaH, 0, 1);
        syncSetting();
    }

    private void commitHex() {
        try {
            String h = hexText.replace("#", "");
            if (h.isEmpty()) return;
            int rgb = Integer.parseInt(h, 16);
            Color c = new Color(rgb);
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];
            syncSetting();
        } catch (NumberFormatException ignored) {
        }
        hexText = "";
    }

    private void applyColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alphaValue = color.getAlpha() / 255f;
    }

    private int currentRgb() {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    private void syncSetting() {
        if (currentSetting == null) return;
        int rgb = currentRgb();
        currentSetting.setSyncTheme(false);
        Color c = new Color(
                ColorUtil.getRed(rgb),
                ColorUtil.getGreen(rgb),
                ColorUtil.getBlue(rgb),
                Math.round(alphaValue * 255)
        );
        currentSetting.setValue(c);
        CsColorSetting.pushRecent(c.getRGB());
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int[] makeHueColors() {
        return new int[]{
                Color.HSBtoRGB(0f, 1f, 1f),
                Color.HSBtoRGB(1f / 6f, 1f, 1f),
                Color.HSBtoRGB(2f / 6f, 1f, 1f),
                Color.HSBtoRGB(3f / 6f, 1f, 1f),
                Color.HSBtoRGB(4f / 6f, 1f, 1f),
                Color.HSBtoRGB(5f / 6f, 1f, 1f),
                Color.HSBtoRGB(1f, 1f, 1f)
        };
    }
}
