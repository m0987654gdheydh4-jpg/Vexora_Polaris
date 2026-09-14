package polaris.api.module.impl.combat.aura.custom;

import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;


public final class RotationPresetEditorState {
    private RotationPresetEditorState() {
    }

    public static void drawModel(float originX, float originY, float bodyW, float bodyH, float alpha) {
        if (bodyW <= 1.0F || bodyH <= 1.0F || alpha <= 0.001F) {
            return;
        }
        drawGround(originX, originY, bodyW, bodyH, alpha);
        drawLimb(originX, originY, bodyW, bodyH, -0.25F, 0.0F, 0.0F, 0.375F, alpha);
        drawLimb(originX, originY, bodyW, bodyH, 0.0F, 0.25F, 0.0F, 0.375F, alpha);
        drawLimb(originX, originY, bodyW, bodyH, -0.25F, 0.25F, 0.375F, 0.75F, alpha);
        drawLimb(originX, originY, bodyW, bodyH, -0.5F, -0.25F, 0.375F, 0.75F, alpha);
        drawLimb(originX, originY, bodyW, bodyH, 0.25F, 0.5F, 0.375F, 0.75F, alpha);
        drawLimb(originX, originY, bodyW, bodyH, -0.25F, 0.25F, 0.75F, 1.0F, alpha);
    }

    public static float[] toScreen(float originX, float originY, float bodyW, float bodyH, float nx, float ny) {
        return new float[]{originX + nx * bodyW, originY - ny * bodyH};
    }

    public static boolean hitModel(float nx, float ny) {
        if (ny < 0.0F || ny > 1.0F) {
            return false;
        }
        if (inBox(nx, ny, -0.25F, 0.25F, 0.75F, 1.0F)) return true;
        if (inBox(nx, ny, -0.25F, 0.25F, 0.375F, 0.75F)) return true;
        if (inBox(nx, ny, -0.5F, -0.25F, 0.375F, 0.75F)) return true;
        if (inBox(nx, ny, 0.25F, 0.5F, 0.375F, 0.75F)) return true;
        if (ny <= 0.375F) {
            if (nx >= -0.25F && nx < 0.0F) return true;
            if (nx >= 0.0F && nx <= 0.25F) return true;
        }
        return false;
    }

    private static boolean inBox(float x, float y, float x0, float x1, float y0, float y1) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    private static void drawLimb(float ox, float oy, float bw, float bh, float x0, float x1, float y0, float y1, float a) {
        float px = ox + x0 * bw;
        float py = oy - y1 * bh;
        float pw = (x1 - x0) * bw;
        float ph = (y1 - y0) * bh;
        int fill = ColorUtil.rgba(86, 112, 162, Math.round(82.0F * a));
        int edge = ColorUtil.rgba(155, 188, 238, Math.round(65.0F * a));
        Render2D.outline(px, py, pw, ph, 1.5F, 1.0F, edge);
        Render2D.rect(px, py, pw, ph, 1.5F, fill);
    }

    private static void drawGround(float ox, float oy, float bw, float bh, float a) {
        float top = oy - bh;
        Render2D.rect(ox - bw * 0.52F, top - 4.0F, bw * 1.04F, bh + 10.0F, 1.5F,
                ColorUtil.rgba(70, 95, 140, Math.round(10.0F * a)));
        Render2D.rect(ox - bw * 0.25F, oy - 1.0F, bw * 0.5F, 6.0F, 1.5F,
                ColorUtil.rgba(95, 160, 255, Math.round(20.0F * a)));
    }
}
