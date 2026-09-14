package polaris.screens.csgui.elements;

import org.lwjgl.glfw.GLFW;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.screens.csgui.CsMenuAssets;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public final class CsBindSetting extends CsSettingComponent<BindSetting> {
    private static final float NAME_SIZE = 8.5f;
    private static final float BOX_H = 18f;
    private static final float OUTLINE = 0.85f;
    private static final float PAD_X = 8f;

    private final SimpleLinearAnimation listenAnim = new SimpleLinearAnimation(180);
    private final SimpleLinearAnimation widthAnim = new SimpleLinearAnimation(180);
    private float targetBoxW = 40f;
    private float visualBoxW = 40f;

    public CsBindSetting(BindSetting setting, float width) {
        super(setting, width);
        this.height = sc(18f);
        widthAnim.setImmediate(true);
    }

    @Override
    public float getHeight() {
        return sc(18f);
    }

    private boolean hasBind() {
        KeyBind b = setting.getValue();
        return b != null && b.isBound();
    }

    public void applyBind(KeyBind bind) {
        setting.setValue(bind != null ? bind : KeyBind.NONE);
        listeningBindComp = null;
        listenAnim.hide();
    }

    private float measureBoxW(boolean listening) {
        String text;
        if (listening) {
            text = "...";
        } else if (hasBind()) {
            text = setting.getValue().getDisplayName();
        } else {
            text = "None";
        }
        float textSz = sc(8f);
        float tw = textWidth(FontType.INTER_SEMI, text, textSz);
        float icon = (!hasBind() || listening) ? sc(11f) + sc(5f) : 0f;
        return Math.max(sc(36f), icon + tw + sc(PAD_X) * 2f);
    }

    @Override
    public void draw(float x, float y, int mouseX, int mouseY, int alpha) {
        this.height = getHeight();
        float va = getVisibilityAlpha();
        int a = Math.round(alpha * va);
        if (a <= 0) return;

        boolean listening = listeningBindComp == this;
        if (listening) listenAnim.show();
        else listenAnim.hide();
        float lp = listenAnim.getProgress();

        targetBoxW = measureBoxW(listening);
        
        visualBoxW += (targetBoxW - visualBoxW) * 0.28f;

        float pad = sc(2f);
        float nameSz = sc(NAME_SIZE);
        float boxH = sc(BOX_H);
        float midY = y + height * 0.5f;
        float right = x + width - pad;
        float boxW = visualBoxW;
        float boxX = right - boxW;
        float boxY = midY - boxH * 0.5f;

        boolean hov = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH
                || hovered(x, y, mouseX, mouseY);
        updateHoverState(hov);
        float hp = hoverAnimation.getProgress();

        
        float nameAvail = Math.max(0f, boxX - (x + pad) - sc(6f));
        drawClippedText(FontType.INTER_MEDIUM, setting.getName(),
                x + pad, midY - nameSz * 0.5f, nameAvail, nameSz, withAlpha(0xFFCED0D5, a));

        
        int fill = blend(
                rgba(255, 255, 255, Math.round(a * (0.07f + 0.04f * hp))),
                themeAccent(Math.round(a * (0.55f + 0.2f * lp))),
                lp);
        int outline = blend(
                rgba(255, 255, 255, Math.round(a * (0.16f + 0.08f * hp))),
                themeAccent(Math.round(a * 0.9f)),
                lp);
        float radius = sc(5f);
        Render2D.rect(boxX, boxY, boxW, boxH, radius, fill);
        Render2D.outline(boxX, boxY, boxW, boxH, radius, sc(OUTLINE), outline);

        String text;
        if (listening) text = "...";
        else if (hasBind()) text = setting.getValue().getDisplayName();
        else text = "None";

        float textSz = sc(8f);
        float tw = textWidth(FontType.INTER_SEMI, text, textSz);
        boolean showIcon = !hasBind() || listening;
        float icon = sc(11f);
        float contentW = (showIcon ? icon + sc(5f) : 0f) + tw;
        float cx = boxX + (boxW - contentW) * 0.5f;

        int iconCol = blend(withAlpha(0xFF9AA0AC, a), rgba(255, 255, 255, a), lp);
        int textCol = blend(withAlpha(0xFFB0B4BE, a), rgba(255, 255, 255, a), Math.max(lp, hasBind() ? 0.85f : 0f));

        if (showIcon) {
            CsMenuAssets.icon(CsMenuAssets.KEYBOARD, cx, boxY + (boxH - icon) * 0.5f, icon, iconCol);
            cx += icon + sc(5f);
        }
        Render2D.text(FontType.INTER_SEMI, text, cx, boxY + (boxH - textSz) * 0.5f, textSz, textCol);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button, float x, float y) {
        float pad = sc(2f);
        float boxH = sc(BOX_H);
        float midY = y + height * 0.5f;
        float right = x + width - pad;
        float boxW = visualBoxW;
        float boxX = right - boxW;
        float boxY = midY - boxH * 0.5f;
        boolean onBox = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;

        if (listeningBindComp == this) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                listeningBindComp = null;
                listenAnim.hide();
                return;
            }
            applyBind(KeyBind.mouse(button));
            return;
        }

        if (!onBox && !hovered(x, y, (int) mouseX, (int) mouseY)) return;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            listeningBindComp = this;
            listenAnim.show();
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            applyBind(KeyBind.NONE);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            setting.reset();
            listeningBindComp = null;
        }
    }
}
