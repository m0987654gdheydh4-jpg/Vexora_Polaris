package polaris.screens.csgui.elements;

import polaris.api.settings.impl.MultiModeSetting;
import polaris.screens.csgui.CsMenuAssets;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.List;


public final class CsMultiModeSetting extends CsSettingComponent<MultiModeSetting> {
    private static final float NAME_SIZE = 9.5f;
    private static final float CHIP_TEXT = 8f;
    private static final float CHIP_H = 16f;
    private static final float CHIP_GAP = 3f;
    private static final float CHIP_LINE_GAP = 3f;
    private static final float CHIPS_Y = 14f;
    private static final float BOTTOM = 2f;
    private static final float CHIP_PAD_X = 8f;
    private static final float CHECK_BOX = 10f;
    private static final float CHECK_GAP = 4f;

    private final SimpleLinearAnimation[] selAnims;
    private final SimpleLinearAnimation[] hovAnims;
    private final float[] chipX;
    private final float[] chipY;
    private final float[] chipW;
    private final String[] modes;
    private int chipCount;
    private float layoutW = -1f;
    private float layoutScaleSnap = -1f;

    public CsMultiModeSetting(MultiModeSetting setting, float width) {
        super(setting, width);
        List<String> list = setting.getModes();
        this.modes = list.toArray(new String[0]);
        int n = modes.length;
        this.selAnims = new SimpleLinearAnimation[n];
        this.hovAnims = new SimpleLinearAnimation[n];
        for (int i = 0; i < n; i++) {
            selAnims[i] = new SimpleLinearAnimation(180);
            hovAnims[i] = new SimpleLinearAnimation(140);
            if (setting.isSelected(modes[i])) {
                selAnims[i].setImmediate(true);
            }
        }
        this.chipX = new float[n];
        this.chipY = new float[n];
        this.chipW = new float[n];
        layout(0f);
    }

    @Override
    public void setWidth(float width) {
        super.setWidth(width);
        if (Math.abs(layoutW - width) > 0.5f || Math.abs(layoutScaleSnap - layoutScale) > 0.001f) {
            layout(0f);
        }
    }

    @Override
    public float getHeight() {
        if (Math.abs(layoutW - width) > 0.5f || Math.abs(layoutScaleSnap - layoutScale) > 0.001f) {
            layout(0f);
        }
        return height;
    }

    private void layout(float originX) {
        layoutW = width;
        layoutScaleSnap = layoutScale;
        chipCount = modes.length;

        float chipText = sc(CHIP_TEXT);
        float chipH = sc(CHIP_H);
        float chipGap = sc(CHIP_GAP);
        float chipLineGap = sc(CHIP_LINE_GAP);
        float chipsY = sc(CHIPS_Y);
        float bottom = sc(BOTTOM);
        float chipPadX = sc(CHIP_PAD_X);
        float checkBox = sc(CHECK_BOX);
        float checkGap = sc(CHECK_GAP);
        float pad = sc(2.5f);

        float rowX = originX + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        float x = rowX;
        float y = chipsY;
        float right = rowX + rowW;

        for (int i = 0; i < chipCount; i++) {
            float tw = textWidth(FontType.INTER_SEMI, modes[i], chipText);
            float w = Math.min(rowW, Math.max(sc(28f), chipPadX + checkBox + checkGap + tw + chipPadX * 0.55f));
            if (x > rowX && x + w > right + 0.5f) {
                x = rowX;
                y += chipH + chipLineGap;
            }
            chipX[i] = x;
            chipY[i] = y;
            chipW[i] = w;
            x += w + chipGap;
        }

        float chipsBottom = chipCount == 0 ? chipsY : y + chipH;
        this.height = chipsBottom + bottom;
    }

    private int selectedCount() {
        int n = 0;
        for (String m : modes) {
            if (setting.isSelected(m)) n++;
        }
        return n;
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        layout(x);

        float nameSz = sc(NAME_SIZE);
        float chipText = sc(CHIP_TEXT);
        float chipH = sc(CHIP_H);
        float checkBox = sc(CHECK_BOX);
        float checkGap = sc(CHECK_GAP);
        float chipPadX = sc(CHIP_PAD_X);
        float pad = sc(2.5f);

        float labelX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);

        int count = selectedCount();
        String badge = count + "/" + chipCount;
        float badgeSz = sc(7.5f);
        float badgeW = textWidth(FontType.INTER_SEMI, badge, badgeSz) + sc(8f);
        float badgeH = sc(12f);
        float badgeX = labelX + rowW - badgeW;
        float badgeY = y + (nameSz - badgeH) * 0.5f + sc(0.5f);

        float nameMax = Math.max(sc(8f), badgeX - labelX - sc(6f));
        drawClippedText(FontType.INTER_SEMI, setting.getName(),
                labelX, y, nameMax, nameSz, withAlpha(0xFFCED0D5, alpha));

        int badgeBg = count > 0
                ? themeAccent(Math.round(alpha * 0.45f))
                : rgba(255, 255, 255, Math.round(alpha * 0.08f));
        Render2D.rect(badgeX, badgeY, badgeW, badgeH, sc(4f), badgeBg);
        Render2D.outline(badgeX, badgeY, badgeW, badgeH, sc(4f), 0.8f,
                rgba(255, 255, 255, Math.round(alpha * 0.14f)));
        float bw = textWidth(FontType.INTER_SEMI, badge, badgeSz);
        Render2D.text(FontType.INTER_SEMI, badge,
                badgeX + (badgeW - bw) * 0.5f,
                badgeY + (badgeH - badgeSz) * 0.5f,
                badgeSz,
                count > 0 ? rgba(255, 255, 255, alpha) : withAlpha(0xFF969AA6, alpha));

        for (int i = 0; i < chipCount; i++) {
            float cx = chipX[i];
            float cy = y + chipY[i];
            float cw = chipW[i];
            float ch = chipH;

            boolean selected = setting.isSelected(modes[i]);
            boolean hov = mouseX >= cx && mouseX <= cx + cw && mouseY >= cy && mouseY <= cy + ch;

            if (selected) selAnims[i].show();
            else selAnims[i].hide();
            if (hov) hovAnims[i].show();
            else hovAnims[i].hide();

            float sp = selAnims[i].getProgress();
            float hp = hovAnims[i].getProgress();

            int bgOff = rgba(255, 255, 255, Math.round(alpha * (0.06f + 0.04f * hp)));
            int bgOn = themeAccent(Math.round(alpha * (0.55f + 0.15f * hp)));
            Render2D.rect(cx, cy, cw, ch, sc(5f), blend(bgOff, bgOn, sp));
            Render2D.outline(cx, cy, cw, ch, sc(5f), 1.0f,
                    blend(rgba(255, 255, 255, Math.round(alpha * (0.14f + 0.06f * hp))),
                            themeAccent(Math.round(alpha * 0.7f)), sp));

            float box = checkBox;
            float bx = cx + chipPadX * 0.5f;
            float by = cy + (ch - box) * 0.5f;
            int boxOff = rgba(255, 255, 255, Math.round(alpha * 0.10f));
            int boxOn = themeAccent(Math.round(alpha * 0.95f));
            Render2D.rect(bx, by, box, box, sc(3f), blend(boxOff, boxOn, sp));
            Render2D.outline(bx, by, box, box, sc(3f), 1.0f,
                    rgba(255, 255, 255, Math.round(alpha * (0.2f + 0.15f * (1f - sp)))));
            if (sp > 0.08f) {
                CsMenuAssets.iconCentered(CsMenuAssets.CHECKMARK,
                        bx + box * 0.5f, by + box * 0.5f, box * 0.7f,
                        rgba(255, 255, 255, Math.round(alpha * sp)));
            }

            float textX = bx + box + checkGap;
            float textMax = Math.max(sc(4f), cx + cw - textX - sc(4f));
            int textCol = blend(withAlpha(0xFFC0C4CC, alpha), rgba(255, 255, 255, alpha), sp);
            drawClippedText(FontType.INTER_SEMI, modes[i],
                    textX, cy + (ch - chipText) * 0.5f, textMax, chipText, textCol);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        if (button != 0 && button != 2) return;
        layout(x);
        float chipH = sc(CHIP_H);

        if (button == 2) {
            if (hovered(x, y, (int) mouseX, (int) mouseY)) {
                setting.reset();
            }
            return;
        }

        for (int i = 0; i < chipCount; i++) {
            float cx = chipX[i];
            float cy = y + chipY[i];
            if (mouseX >= cx && mouseX <= cx + chipW[i] && mouseY >= cy && mouseY <= cy + chipH) {
                boolean next = !setting.isSelected(modes[i]);
                setting.setSelected(modes[i], next);
                if (next) selAnims[i].show();
                else selAnims[i].hide();
                return;
            }
        }
    }
}
