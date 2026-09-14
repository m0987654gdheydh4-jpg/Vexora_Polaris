package polaris.screens.csgui.elements;

import org.lwjgl.glfw.GLFW;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.Locale;


public class CsNumberSetting extends CsSettingComponent<NumberSetting> {
    private static final float NAME_SIZE = 8.5f;
    private static final float VALUE_SIZE = 8f;
    private static final float TRACK_H = 3f;
    private static final float KNOB_R = 4f;

    private boolean dragging;
    private double visualPercent;

    public CsNumberSetting(NumberSetting setting, float width) {
        super(setting, width);
        this.height = sc(28f);
        this.visualPercent = percent();
    }

    @Override
    public float getHeight() {
        return sc(28f);
    }

    private float percent() {
        double min = setting.getMin();
        double max = setting.getMax();
        double range = max - min;
        if (range <= 0) return 0f;
        return (float) Math.max(0, Math.min(1, (setting.getValue() - min) / range));
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float vis = getVisibilityAlpha();
        int a = Math.round(alpha * vis);
        if (a <= 0) return;

        float target = percent();
        visualPercent += (target - visualPercent) * 0.28;
        float p = (float) visualPercent;

        float nameSz = sc(NAME_SIZE);
        float valueSz = sc(VALUE_SIZE);
        float trackH = sc(TRACK_H);
        float knobR = sc(KNOB_R);

        drawSettingIcon(ICON_NUMBER, x, y, sc(13f), a, iconSize(), FontType.GUI_ICONS);

        float rowX = contentX(x);
        float rowW = contentWidth();

        if (dragging) {
            long w = GLFW.glfwGetCurrentContext();
            if (w == 0 || GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                dragging = false;
            } else {
                setFromMouse(mouseX, rowX, rowW);
                p = percent();
                visualPercent = p;
            }
        }

        float barY = y + sc(15f);
        boolean barHov = mouseX >= rowX && mouseX <= rowX + rowW
                && mouseY >= barY - sc(6f) && mouseY <= barY + trackH + sc(8f);
        updateHoverState(barHov || dragging);
        float hp = hoverAnimation.getProgress();

        String valStr = formatValue(setting.getValue(), setting.getStep());
        float vw = textWidth(FontType.INTER_SEMI, valStr, valueSz);
        drawClippedText(FontType.INTER_MEDIUM, setting.getName(),
                rowX, y + sc(1.5f), Math.max(sc(8f), rowW - vw - sc(8f)), nameSz, withAlpha(0xFFCED0D5, a));
        Render2D.text(FontType.INTER_SEMI, valStr, rowX + rowW - vw, y + sc(2f), valueSz, themeAccent(a));

        float barX = rowX;
        float barW = rowW;

        int trackBg = rgba(255, 255, 255, Math.round(a * 0.12f));
        Render2D.rect(barX, barY, barW, trackH, trackH * 0.5f, trackBg);

        double step = setting.getStep();
        double min = setting.getMin();
        double max = setting.getMax();
        double range = max - min;
        if (step > 0 && range > 0) {
            int count = (int) Math.round(range / step);
            if (count > 0 && count <= 50) {
                int tickCol = rgba(255, 255, 255, Math.round(a * 0.14f));
                for (int i = 1; i < count; i++) {
                    float t = i / (float) count;
                    Render2D.rect(barX + barW * t - 0.5f, barY + sc(0.6f), 1f, trackH - sc(1.2f), 0.5f, tickCol);
                }
            }
        }

        int fill = blend(themeAccent(a), themeAccent(Math.round(a * 0.85f)), hp);
        float fw = barW * p;
        if (fw > 0.5f) Render2D.rect(barX, barY, fw, trackH, trackH * 0.5f, fill);

        float kr = knobR + sc(1.0f) * hp + (dragging ? sc(0.6f) : 0f);
        float kx = barX + fw;
        float ky = barY + trackH * 0.5f;
        Render2D.rect(kx - kr - 1f, ky - kr - 1f, (kr + 1f) * 2f, (kr + 1f) * 2f, kr + 1f,
                rgba(0, 0, 0, Math.round(a * 0.2f)));
        Render2D.rect(kx - kr, ky - kr, kr * 2f, kr * 2f, kr, rgba(245, 246, 250, a));

        float labelY = barY + trackH + sc(3.5f);
        float ls = sc(7f);
        int lc = rgba(150, 154, 166, Math.round(a * 0.85f));
        String minS = formatValue(min, step);
        String midS = formatValue(min + range * 0.5, step);
        String maxS = formatValue(max, step);
        Render2D.text(FontType.INTER_MEDIUM, minS, barX, labelY, ls, lc);
        float midW = textWidth(FontType.INTER_MEDIUM, midS, ls);
        Render2D.text(FontType.INTER_MEDIUM, midS, barX + barW * 0.5f - midW * 0.5f, labelY, ls, lc);
        float maxW = textWidth(FontType.INTER_MEDIUM, maxS, ls);
        Render2D.text(FontType.INTER_MEDIUM, maxS, barX + barW - maxW, labelY, ls, lc);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        float rowX = contentX(x);
        float rowW = contentWidth();
        if (button == 0) {
            float barY = y + sc(15f);
            float trackH = sc(TRACK_H);
            if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= barY - sc(6f) && mouseY <= barY + trackH + sc(8f)) {
                dragging = true;
                setFromMouse(mouseX, rowX, rowW);
            }
        } else if (button == 2) {
            setting.reset();
        }
    }

    private void setFromMouse(double mx, float barX, float barW) {
        double min = setting.getMin();
        double max = setting.getMax();
        double range = max - min;
        if (range <= 0 || barW <= 0) return;
        double pct = Math.max(0, Math.min(1, (mx - barX) / barW));
        double value = min + range * pct;
        double step = setting.getStep();
        if (step > 0) value = Math.round(value / step) * step;
        setting.setValue(value);
    }

    private static String formatValue(double value, double step) {
        if (step >= 1.0) return String.valueOf(Math.round(value));
        String s = String.format(Locale.US, "%.2f", value);
        while (s.contains(".") && s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
