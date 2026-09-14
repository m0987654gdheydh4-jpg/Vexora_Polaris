package polaris.screens.csgui.elements;

import polaris.api.settings.impl.StringSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public class CsStringSetting extends CsSettingComponent<StringSetting> {
    public static CsStringSetting editing;

    private final TextInputField textField;
    private static final float NAME_SIZE = 9.5f;
    private static final float INPUT_H = 18f;
    private final SimpleLinearAnimation focusAnim = new SimpleLinearAnimation(150);

    public CsStringSetting(StringSetting setting, float width) {
        super(setting, width);
        this.height = sc(28f);
        this.textField = new TextInputField(0, 0, width, sc(INPUT_H), sc(5f));
        textField.setBackgroundColor(rgba(255, 255, 255, 20));
        textField.setChangeCallback(v -> setting.setValue(v));
    }

    @Override
    public float getHeight() {
        return sc(28f);
    }

    @Override
    public void setWidth(float width) {
        super.setWidth(width);
        float pad = sc(2.5f);
        textField.setSize(Math.max(sc(20f), width - pad * 2f), sc(INPUT_H));
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float nameSz = sc(NAME_SIZE);
        float inputH = sc(INPUT_H);
        float pad = sc(2.5f);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);

        drawClippedText(FontType.INTER_SEMI, setting.getName(),
                rowX, y + sc(0.5f), rowW, nameSz, withAlpha(0xFFCED0D5, alpha));

        float inputY = y + height - inputH;
        boolean focused = editing == this;
        if (focused) focusAnim.show();
        else focusAnim.hide();
        float fp = focusAnim.getProgress();

        int bg = blend(rgba(255, 255, 255, Math.round(alpha * 0.06f)),
                themeAccent(Math.round(alpha * 0.14f)), fp);
        Render2D.rect(rowX, inputY, rowW, inputH, sc(6f), bg);
        Render2D.outline(rowX, inputY, rowW, inputH, sc(6f), 1.1f,
                blend(rgba(255, 255, 255, Math.round(alpha * 0.14f)),
                        themeAccent(Math.round(alpha * 0.55f)), fp));

        if (focused) {
            textField.setPosition(rowX, inputY);
            textField.setSize(rowW, inputH);
            textField.setTextSize(sc(9f));
            textField.render(null);
        } else {
            String val = setting.getValue();
            if (val == null || val.isEmpty()) {
                float phSz = sc(8f);
                drawClippedText(FontType.INTER_SEMI, "Click to edit…", rowX + sc(7f),
                        inputY + (inputH - phSz) * 0.5f, rowW - sc(14f), phSz, withAlpha(0xFF969AA6, alpha));
            } else {
                float vSz = sc(9f);
                drawClippedText(FontType.INTER_SEMI, val, rowX + sc(7f),
                        inputY + (inputH - vSz) * 0.5f, rowW - sc(14f), vSz, withAlpha(0xFFC8CAD2, alpha));
            }
        }
        updateHoverState(hovered(x, y, mouseX, mouseY));
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        if (button != 0) return;
        float pad = sc(2.5f);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        float inputH = sc(INPUT_H);
        float inputY = y + height - inputH;

        if (editing == this) {
            if (mouseX < rowX || mouseX > rowX + rowW || mouseY < inputY || mouseY > inputY + inputH) {
                editing = null;
                textField.setFocused(false);
            } else {
                textField.mouseClicked(mouseX, mouseY, button);
            }
            return;
        }
        if (mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= inputY && mouseY <= inputY + inputH) {
            if (editing != null) editing.textField.setFocused(false);
            editing = this;
            textField.setText(setting.getValue() != null ? setting.getValue() : "");
            textField.setPosition(rowX, inputY);
            textField.setSize(rowW, inputH);
            textField.setFocused(true);
        }
    }

    public boolean keyPressed(int key, int scan, int mods) {
        if (editing != this) return false;
        if (key == 256 || key == 257 || key == 335) {
            editing = null;
            textField.setFocused(false);
            return true;
        }
        return textField.keyPressed(key, scan, mods);
    }

    public boolean charTyped(char c, int mods) {
        if (editing != this) return false;
        return textField.charTyped(c, mods);
    }
}
