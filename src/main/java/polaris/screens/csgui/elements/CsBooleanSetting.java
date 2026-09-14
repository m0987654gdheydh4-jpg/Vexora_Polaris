package polaris.screens.csgui.elements;

import polaris.api.settings.impl.BooleanSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public class CsBooleanSetting extends CsSettingComponent<BooleanSetting> {
    private static final float NAME_SIZE = 8.5f;
    private static final float BOX = 12.5f;
    private static final String KEYBOARD_ICON = "m";
    private static final float KEY_ICON_SIZE = 9f;
    private static final float KEY_ICON_GAP = 4.5f;

    private final SimpleLinearAnimation toggleAnim = new SimpleLinearAnimation(220);
    private final SimpleLinearAnimation textColorAnim = new SimpleLinearAnimation(260);

    public CsBooleanSetting(BooleanSetting setting, float width) {
        super(setting, width);
        this.height = sc(16f);
        toggleAnim.setImmediate(setting.getValue());
        textColorAnim.setImmediate(setting.getValue());
    }

    @Override
    public float getHeight() {
        return sc(16f);
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float va = getVisibilityAlpha();
        if (va <= 0.005f) return;
        int a = Math.round(alpha * va);

        boolean on = setting.getValue();
        if (on) {
            toggleAnim.show();
            textColorAnim.show();
        } else {
            toggleAnim.hide();
            textColorAnim.hide();
        }

        boolean hov = hovered(x, y, mouseX, mouseY);
        updateHoverState(hov);
        float hp = hoverAnimation.getProgress();
        float p = toggleAnim.getProgress();
        float tp = textColorAnim.getProgress();

        float nameSz = sc(NAME_SIZE);
        float boxBase = sc(BOX);
        float keySz = sc(KEY_ICON_SIZE);
        float keyGap = sc(KEY_ICON_GAP);

        drawSettingIcon(ICON_BOOLEAN, x, y, height, a);

        float labelX = contentX(x);
        float right = x + width - rowPad();

        float box = boxBase * (1f + 0.03f * hp);
        float boxX = right - boxBase + (boxBase - box) * 0.5f;
        float boxY = y + (height - box) * 0.5f;

        float keyIconX = boxX - keyGap - keySz;
        float keyIconY = y + (height - keySz) * 0.5f + sc(1.0f);
        int keyCol = withAlpha(0xFF8A8E98, a);
        if (hov) {
            keyCol = blend(keyCol, themeAccent(a), hp * 0.55f);
        }
        Render2D.text(FontType.MAINMENUSCREEN, KEYBOARD_ICON, keyIconX, keyIconY, keySz, keyCol);

        float nameAvail = Math.max(0f, keyIconX - labelX - sc(6f));
        int nameCol = blend(withAlpha(0xFFCED0D5, a), themeAccent(a), tp);
        if (nameAvail > 0) {
            drawClippedText(FontType.INTER_MEDIUM, setting.getName(),
                    labelX, y + (height - nameSz) * 0.5f, nameAvail, nameSz, nameCol);
        }

        int bgOff = rgba(255, 255, 255, Math.round(a * (0.10f + 0.05f * hp)));
        int bgOn = themeAccent(Math.round(a * 0.95f));
        float radius = sc(3.5f);
        Render2D.rect(boxX, boxY, box, box, radius, blend(bgOff, bgOn, p));
        if (p < 0.95f) {
            Render2D.outline(boxX, boxY, box, box, radius, 0.7f,
                    rgba(255, 255, 255, Math.round(a * (0.16f + 0.08f * hp) * (1f - p))));
        }
        if (p > 0.05f) {
            float inset = box * 0.12f;
            drawCheckMark(boxX + inset, boxY + inset, box - inset * 2f, box - inset * 2f,
                    rgba(255, 255, 255, Math.round(a * p)));
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        if (!hovered(x, y, (int) mouseX, (int) mouseY)) return;
        if (button == 0) {
            boolean v = !setting.getValue();
            setting.setValue(v);
            if (v) {
                toggleAnim.show();
                textColorAnim.show();
            } else {
                toggleAnim.hide();
                textColorAnim.hide();
            }
        } else if (button == 2) {
            setting.reset();
            boolean d = setting.getValue();
            toggleAnim.setImmediate(d);
            textColorAnim.setImmediate(d);
        }
    }
}
