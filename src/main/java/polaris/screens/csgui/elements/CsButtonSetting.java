package polaris.screens.csgui.elements;

import polaris.api.settings.impl.ButtonSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public class CsButtonSetting extends CsSettingComponent<ButtonSetting> {
    private static final float TEXT_SIZE = 8.5f;
    private static final float BTN_H = 16f;
    private final SimpleLinearAnimation pressAnim = new SimpleLinearAnimation(200);

    public CsButtonSetting(ButtonSetting setting, float width) {
        super(setting, width);
        this.height = sc(BTN_H);
    }

    @Override
    public float getHeight() {
        return sc(BTN_H);
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float pad = sc(2f);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        boolean h = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= y && mouseY <= y + height;
        updateHoverState(h);
        float hp = hoverAnimation.getProgress();
        float pp = pressAnim.getProgress();
        if (pp >= 1f) pressAnim.hide();

        float textSz = sc(TEXT_SIZE);
        float scale = 1f + 0.012f * hp - 0.025f * pp;
        float dw = rowW * scale;
        float dh = height * scale;
        float dx = rowX + (rowW - dw) * 0.5f;
        float dy = y + (height - dh) * 0.5f;
        float radius = sc(5f);

        int bg = blend(rgba(255, 255, 255, Math.round(alpha * 0.07f)),
                themeAccent(Math.round(alpha * 0.78f)), hp);
        Render2D.rect(dx, dy, dw, dh, radius, bg);
        Render2D.outline(dx, dy, dw, dh, radius, 0.9f,
                blend(rgba(255, 255, 255, Math.round(alpha * 0.14f)),
                        themeAccent(Math.round(alpha * 0.60f)), hp));

        String label = setting.getName();
        int tc = blend(withAlpha(0xFFCED0D5, alpha), rgba(255, 255, 255, alpha), hp);
        float maxW = Math.max(sc(8f), dw - sc(8f));
        float tw = textWidth(FontType.INTER_SEMI, label, textSz);
        if (tw <= maxW) {
            Render2D.text(FontType.INTER_SEMI, label, dx + (dw - tw) / 2f, dy + (dh - textSz) / 2f, textSz, tc);
        } else {
            drawClippedText(FontType.INTER_SEMI, label, dx + sc(4f), dy + (dh - textSz) / 2f, maxW, textSz, tc);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        float pad = sc(2f);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        if (button == 0 && mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= y && mouseY <= y + height) {
            setting.press();
            pressAnim.show();
        }
    }
}
