package polaris.screens.csgui;

import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;

public final class CsIcons {
    private CsIcons() {}

    public static void drawRefreshIcon(Render2D r, float cx, float cy, float size, float rotationDeg, int col) {
        float bodyW = size * 0.85f;
        float bodyH = size * 0.85f;
        float bx = cx - bodyW / 2f;
        float by = cy - bodyH / 2f;
        r.rect(bx, by, bodyW, bodyH, 1.5f, col);
        float innerPad = 1.4f;
        r.rect(bx + innerPad, by + innerPad, bodyW - innerPad * 2f, bodyH * 0.32f, 1f, ColorUtil.rgba(0, 0, 0, (col >>> 24) & 0xFF));
        float slotW = bodyW * 0.18f;
        float slotH = bodyH * 0.18f;
        r.rect(cx + bodyW * 0.16f, by + innerPad + 0.6f, slotW * 0.55f, slotH, 0.6f, ColorUtil.rgba(0, 0, 0, (col >>> 24) & 0xFF));
        float lbW = bodyW * 0.55f;
        float lbH = bodyH * 0.38f;
        r.rect(cx - lbW / 2f, by + bodyH - lbH - innerPad, lbW, lbH, 1f, ColorUtil.rgba(0, 0, 0, ((col >>> 24) & 0xFF) / 2));
    }

    public static void drawCrossIcon(Render2D r, float cx, float cy, float size, float thickness, int col) {
        int steps = Math.max(6, (int) (size * 1.4f));
        float half = size / 2f;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float dx = -half + size * t;
            r.rect(cx + dx - thickness / 2f, cy + dx - thickness / 2f, thickness, thickness, thickness / 2f, col);
            r.rect(cx + dx - thickness / 2f, cy - dx - thickness / 2f, thickness, thickness, thickness / 2f, col);
        }
    }

    public static void drawHamburgerOrClose(Render2D r, float x, float y, float size, boolean isClose, int col) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        if (isClose) {
            float l = 11f, t = 1.8f;
            r.rect(cx - l / 2f, cy - t / 2f, l, t, 1f, col);
            r.rect(cx - t / 2f, cy - l / 2f, t, l, 1f, col);
        } else {
            float barW = 13f, barH = 1.8f, gap = 3.5f;
            float startY = cy - (barH * 3 + gap * 2) / 2f;
            float startX = cx - barW / 2f;
            r.rect(startX, startY, barW, barH, 1f, col);
            r.rect(startX, startY + barH + gap, barW, barH, 1f, col);
            r.rect(startX, startY + (barH + gap) * 2f, barW, barH, 1f, col);
        }
    }

    public static void drawPlusIcon(Render2D r, float x, float y, float size, int col) {
        float cx = x + size / 2f;
        float cy = y + size / 2f;
        float l = 11f, t = 1.8f;
        r.rect(cx - l / 2f, cy - t / 2f, l, t, 1f, col);
        r.rect(cx - t / 2f, cy - l / 2f, t, l, 1f, col);
    }
}
