package polaris.screens.csgui.elements;

import polaris.api.settings.impl.ModeSetting;
import polaris.screens.csgui.CsMenuAssets;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.List;


public final class CsModeSetting extends CsSettingComponent<ModeSetting> {
    private static final float NAME_SIZE = 8.5f;
    private static final float NAME_GAP = 3f;
    private static final float BOX_H = 18f;
    private static final float ROW_H = 17f;
    private static final float DROP_PAD = 3f;
    private static final float DROP_GAP = 2f;
    private static final float OUTLINE = 0.9f;
    
    private static final float RADIUS = 3f;

    
    public static CsModeSetting openInstance;

    private final SimpleLinearAnimation expandAnim = new SimpleLinearAnimation(180);
    private boolean expanded;
    private final String[] modes;

    private float lastBoxX, lastBoxY, lastBoxW, lastBoxH, lastDropY;

    public CsModeSetting(ModeSetting setting, float width) {
        super(setting, width);
        List<String> list = setting.getModes();
        this.modes = list.toArray(new String[0]);
        this.height = getHeight();
    }

    private float collapsedHeight() {
        return sc(NAME_SIZE + NAME_GAP + BOX_H + 1f);
    }

    private float dropdownHeight(float ep) {
        if (ep <= 0.01f) return 0f;
        return (sc(DROP_GAP) + modes.length * sc(ROW_H) + sc(DROP_PAD) * 2f) * ep;
    }

    @Override
    public float getHeight() {
        
        if (expanded) expandAnim.show();
        else expandAnim.hide();
        return collapsedHeight() + dropdownHeight(expandAnim.getProgress());
    }

    public boolean isExpanded() {
        return expanded || expandAnim.getProgress() > 0.01f;
    }

    public void collapse() {
        expanded = false;
        expandAnim.hide();
        if (openInstance == this) openInstance = null;
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        if (expanded) expandAnim.show();
        else expandAnim.hide();
        float ep = expandAnim.getProgress();
        this.height = collapsedHeight() + dropdownHeight(ep);

        float va = getVisibilityAlpha();
        int a = Math.round(alpha * va);
        if (a <= 0) return;

        float pad = sc(2f);
        float nameSz = sc(NAME_SIZE);
        float boxH = sc(BOX_H);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);

        drawClippedText(FontType.INTER_MEDIUM, setting.getName(),
                rowX, y, rowW, nameSz, withAlpha(0xFFCED0D5, a));

        float boxX = rowX;
        float boxY = y + nameSz + sc(NAME_GAP);
        float boxW = rowW;

        lastBoxX = boxX;
        lastBoxY = boxY;
        lastBoxW = boxW;
        lastBoxH = boxH;
        lastDropY = boxY + boxH + sc(DROP_GAP);

        boolean boxHov = mouseX >= boxX && mouseX <= boxX + boxW
                && mouseY >= boxY && mouseY <= boxY + boxH;
        updateHoverState(boxHov || expanded);
        float hp = hoverAnimation.getProgress();

        
        
        
        int ol = blend(rgba(255, 255, 255, Math.round(a * 0.16f)),
                rgba(255, 255, 255, Math.round(a * 0.42f)), hp);
        ol = blend(ol, themeAccent(Math.round(a * 0.55f)), ep);
        float radius = sc(RADIUS);
        Render2D.outline(boxX, boxY, boxW, boxH, radius, sc(OUTLINE), ol);

        float icon = sc(11f);
        float arrow = sc(10f);
        float textSz = sc(8.5f);
        String val = setting.getValue() != null ? setting.getValue() : "";

        float ix = boxX + sc(7f);
        CsMenuAssets.icon(CsMenuAssets.ENUM, ix, boxY + (boxH - icon) * 0.5f, icon,
                withAlpha(0xFF8E94A0, a));

        float ax = boxX + boxW - sc(7f) - arrow;
        CsMenuAssets.icon(CsMenuAssets.ARROW, ax, boxY + (boxH - arrow) * 0.5f, arrow,
                blend(withAlpha(0xFF8E94A0, a), themeAccent(a), ep));

        float tx = ix + icon + sc(5f);
        float textMax = Math.max(sc(6f), ax - tx - sc(6f));
        drawClippedText(FontType.INTER_SEMI, val, tx, boxY + (boxH - textSz) * 0.5f, textMax, textSz,
                withAlpha(0xFFE6E8EE, a));

        
        if (ep > 0.02f) {
            float dropY = lastDropY;
            float rowH = sc(ROW_H);
            float dPad = sc(DROP_PAD);
            float dropH = modes.length * rowH + dPad * 2f;
            int dropA = Math.round(a * ep);
            float dropR = sc(RADIUS);

            
            
            
            int panelOl = rgba(255, 255, 255, Math.min(48, Math.round(44 * (dropA / 255f))));
            Render2D.outline(boxX, dropY, boxW, dropH * ep, dropR, sc(OUTLINE), panelOl);

            
            float visibleH = dropH * ep;
            String current = setting.getValue();
            for (int i = 0; i < modes.length; i++) {
                float ry = dropY + dPad + i * rowH;
                if (ry + rowH * 0.3f > dropY + visibleH) break;
                boolean sel = current != null && current.equalsIgnoreCase(modes[i]);
                boolean hov = mouseX >= boxX && mouseX <= boxX + boxW
                        && mouseY >= ry && mouseY <= ry + rowH;
                if (sel || hov) {
                    int rowBg = sel
                            ? themeAccent(Math.round(dropA * 0.28f))
                            : rgba(255, 255, 255, Math.round(dropA * 0.07f));
                    Render2D.rect(boxX + sc(3f), ry + sc(1f), boxW - sc(6f), rowH - sc(2f), sc(2f), rowBg);
                }
                float tSz = sc(8.5f);
                drawClippedText(FontType.INTER_SEMI, modes[i],
                        boxX + sc(9f), ry + (rowH - tSz) * 0.5f, boxW - sc(28f), tSz,
                        withAlpha(sel ? 0xFFFFFFFF : 0xFFC0C4CC, dropA));
                if (sel) {
                    CsMenuAssets.iconCentered(CsMenuAssets.CHECKMARK,
                            boxX + boxW - sc(13f), ry + rowH * 0.5f, sc(9f), rgba(255, 255, 255, dropA));
                }
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        if (button != 0) return;

        float pad = sc(2f);
        float nameSz = sc(NAME_SIZE);
        float boxH = sc(BOX_H);
        float rowX = x + pad;
        float rowW = Math.max(sc(8f), width - pad * 2f);
        float boxX = rowX;
        float boxY = y + nameSz + sc(NAME_GAP);
        float boxW = rowW;
        float dropY = boxY + boxH + sc(DROP_GAP);
        float rowH = sc(ROW_H);
        float dPad = sc(DROP_PAD);

        lastBoxX = boxX;
        lastBoxY = boxY;
        lastBoxW = boxW;
        lastBoxH = boxH;
        lastDropY = dropY;

        
        if (expanded || expandAnim.getProgress() > 0.4f) {
            for (int i = 0; i < modes.length; i++) {
                float ry = dropY + dPad + i * rowH;
                if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= ry && mouseY <= ry + rowH) {
                    setting.setValue(modes[i]);
                    collapse();
                    return;
                }
            }
        }

        if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH) {
            if (expanded) {
                collapse();
            } else {
                if (openInstance != null && openInstance != this) {
                    openInstance.collapse();
                }
                expanded = true;
                expandAnim.show();
                openInstance = this;
            }
        }
    }
}
