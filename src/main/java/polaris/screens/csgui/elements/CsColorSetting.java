package polaris.screens.csgui.elements;

import polaris.api.settings.impl.ColorSetting;
import polaris.screens.csgui.CsMenuAssets;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;


public class CsColorSetting extends CsSettingComponent<ColorSetting> {
    private static final float NAME_SIZE = 8.5f;
    private static final float BAR_H = 13f;
    private static final float BAR_GAP = 3f;
    private static final float SWATCH_R = 4f;
    private static final float SWATCH_STEP = 12f;
    private static final int MAX_RECENT = 6;

    private static final List<Integer> RECENT = new ArrayList<>();
    private static final int[] PRESETS = {
            0xFF7EB6FF, 0xFFFFD700, 0xFF8673FA, 0xFFC5C6C8, 0xFFC34040, 0xFF98D8C8,
    };

    private final SimpleLinearAnimation pressAnim = new SimpleLinearAnimation(160);

    public CsColorSetting(ColorSetting setting, float width) {
        super(setting, width);
        this.height = sc(NAME_SIZE + BAR_GAP + BAR_H + 1f);
    }

    @Override
    public float getHeight() {
        return sc(NAME_SIZE + BAR_GAP + BAR_H + 1f);
    }

    public static void pushRecent(int argb) {
        int rgb = argb | 0xFF000000;
        RECENT.removeIf(c -> (c & 0xFFFFFF) == (rgb & 0xFFFFFF));
        RECENT.add(0, rgb);
        while (RECENT.size() > MAX_RECENT) {
            RECENT.remove(RECENT.size() - 1);
        }
    }

    private List<Integer> swatchColors() {
        List<Integer> list = new ArrayList<>();
        list.add(themeAccent | 0xFF000000);
        for (int p : PRESETS) {
            if (list.stream().noneMatch(c -> (c & 0xFFFFFF) == (p & 0xFFFFFF))) list.add(p);
        }
        for (int r : RECENT) {
            if (list.stream().noneMatch(c -> (c & 0xFFFFFF) == (r & 0xFFFFFF))) list.add(r);
        }
        return list;
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float va = getVisibilityAlpha();
        int a = Math.round(alpha * va);
        if (a <= 0) return;

        float pad = sc(2f);
        float nameSz = sc(NAME_SIZE);
        float barH = sc(BAR_H);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);

        drawClippedText(FontType.INTER_MEDIUM, setting.getName(),
                rowX, y, rowW, nameSz, withAlpha(0xFFCED0D5, a));

        float barY = y + nameSz + sc(BAR_GAP);
        boolean hov = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= barY && mouseY <= barY + barH;
        updateHoverState(hov);
        float hp = hoverAnimation.getProgress();

        int bg = blend(rgba(18, 19, 24, Math.round(a * 0.92f)), rgba(26, 28, 34, Math.round(a * 0.95f)), hp);
        int ol = rgba(255, 255, 255, Math.round(a * (0.10f + 0.05f * hp)));
        float radius = sc(5f);
        Render2D.rect(rowX, barY, rowW, barH, radius, bg);
        Render2D.outline(rowX, barY, rowW, barH, radius, 0.8f, ol);

        Color display = setting.getValue();
        int rgb = display.getRGB() & 0xFFFFFF;
        int colA = Math.round(display.getAlpha() * (a / 255f));

        float previewW = sc(28f);
        int tint = (colA << 24) | rgb;
        try {
            Render2D.image(CsMenuAssets.CONTAINER, rowX, barY, previewW, barH, radius, tint);
        } catch (Throwable t) {
            Render2D.rect(rowX, barY, previewW, barH, radius, 0f, 0f, radius, tint);
        }
        float brush = sc(10f);
        CsMenuAssets.iconCentered(CsMenuAssets.BRUSH,
                rowX + previewW * 0.5f, barY + barH * 0.5f, brush,
                rgba(255, 255, 255, Math.round(a * 0.9f)));

        float divX = rowX + previewW;
        Render2D.rect(divX, barY + sc(2.5f), 1f, barH - sc(5f), rgba(255, 255, 255, Math.round(a * 0.10f)));

        
        List<Integer> swatches = swatchColors();
        float sx = divX + sc(7f);
        float mid = barY + barH * 0.5f;
        float step = sc(SWATCH_STEP);
        float r = sc(SWATCH_R);
        float maxRight = rowX + rowW - sc(6f);
        int maxSw = 0;
        for (int i = 0; i < swatches.size(); i++) {
            if (sx + i * step + r > maxRight) break;
            maxSw++;
        }
        maxSw = Math.min(maxSw, swatches.size());
        for (int i = 0; i < maxSw; i++) {
            int scolor = swatches.get(i);
            float cx = sx + i * step;
            boolean selected = (scolor & 0xFFFFFF) == rgb;
            float rr = r + (selected ? sc(1f) : 0f);
            Render2D.rect(cx - rr, mid - rr, rr * 2f, rr * 2f, rr, scolor | 0xFF000000);
            if (selected) {
                CsMenuAssets.iconCentered(CsMenuAssets.CHECKMARK, cx, mid, sc(7f), rgba(20, 22, 28, a));
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        if (button != 0 && button != 2) return;
        if (!hovered(x, y, (int) mouseX, (int) mouseY)) return;

        float pad = sc(2f);
        float nameSz = sc(NAME_SIZE);
        float barH = sc(BAR_H);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        float barY = y + nameSz + sc(BAR_GAP);

        if (button == 2) {
            setting.reset();
            setting.setSyncTheme(false);
            return;
        }

        float previewW = sc(28f);
        float divX = rowX + previewW;
        float sx = divX + sc(7f);
        float mid = barY + barH * 0.5f;
        float step = sc(SWATCH_STEP);
        float r = sc(SWATCH_R) + sc(2f);
        float maxRight = rowX + rowW - sc(6f);
        List<Integer> swatches = swatchColors();
        for (int i = 0; i < swatches.size(); i++) {
            float cx = sx + i * step;
            if (cx + r > maxRight) break;
            if (mouseX >= cx - r && mouseX <= cx + r && mouseY >= mid - r && mouseY <= mid + r) {
                int c = swatches.get(i);
                Color cur = setting.getValue();
                setting.setSyncTheme(false);
                setting.setValue(new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, cur.getAlpha()));
                pushRecent(c);
                pressAnim.show();
                return;
            }
        }

        CsColorPicker.get().open(setting, (float) mouseX + 6f, (float) mouseY - 10f);
        pushRecent(setting.getValue().getRGB());
        pressAnim.show();
    }
}
