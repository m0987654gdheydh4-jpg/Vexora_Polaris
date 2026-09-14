package polaris.screens.mainmenu;

import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;


public final class RockstarMenuChrome {
    public static final int COL_FIRST = ColorUtil.rgba(14, 17, 21, 255);
    public static final int COL_SECOND = ColorUtil.rgba(19, 21, 27, 255);
    public static final int COL_THIRD = ColorUtil.rgba(25, 29, 36, 255);
    public static final int COL_FOURS = ColorUtil.rgba(68, 65, 77, 255);
    public static final int COL_FOURS_OUTLINE = ColorUtil.rgba(68, 65, 77, 178);
    public static final int COL_TEXT = ColorUtil.rgba(255, 255, 255, 255);
    public static final int COL_TEXT_DIM = ColorUtil.rgba(184, 184, 184, 255);
    public static final int COL_ACCENT = ColorUtil.rgba(86, 102, 220, 255);

    public static final String[] DOCK_IDS = {"home", "single", "multi", "alt", "settings", "exit"};
    public static final String[] DOCK_ICONS = {
            "cataclysm:icons/mainmenu/home.png",
            "cataclysm:icons/mainmenu/single.png",
            "cataclysm:icons/mainmenu/multi.png",
            "cataclysm:icons/mainmenu/alt.png",
            "cataclysm:icons/mainmenu/settings.png",
            "cataclysm:icons/mainmenu/exit.png"
    };

    private RockstarMenuChrome() {
    }

    public static void drawBackground(float dw, float dh, float mouseX, float mouseY) {
        Render2D.rect(0, 0, dw, dh, 0f, COL_FIRST);

        float logoSize = dw / 1.88f;
        float logoX = -logoSize / 9.14f;
        float logoY = dh - 0.8632f * logoSize;
        Render2D.image("cataclysm:icons/mainmenu/bglogo.png", logoX, logoY, logoSize, logoSize, 0f,
                ColorUtil.rgba(16, 19, 24, 255));

        float step = Math.max(8f, (float) Math.floor(dw / 15f));
        int gridColor = ColorUtil.rgba(25, 29, 36, 25);
        for (float x = 0; x <= dw; x += step) {
            Render2D.rect(x, 0, 1f, dh, 0f, gridColor);
        }
        for (float y = 0; y <= dh; y += step) {
            Render2D.rect(0, y, dw, 1f, 0f, gridColor);
        }

        float glow = 90f;
        Render2D.rect(mouseX - glow * 0.5f, mouseY - glow * 0.5f, glow, glow, glow * 0.5f,
                ColorUtil.rgba(255, 255, 255, 10));
    }

    public static PanelGeom panelMulti(float dw, float dh) {
        float width = 328f;
        float height = dh / 1.33f;
        float x = dw * 0.5f - width * 0.5f;
        float y = dh * 0.5f - height * 0.5f - dh / 12.73f;
        return new PanelGeom(x, y, width, height);
    }

    public static PanelGeom panelPause(float dw, float dh, float height) {
        float width = 204f;
        float x = dw * 0.5f - width * 0.5f;
        float y = dh * 0.5f - height * 0.5f;
        return new PanelGeom(x, y, width, height);
    }

    public static void drawPanel(PanelGeom panel, boolean tintTopLeft) {
        Render2D.rect(panel.x - 6f, panel.y - 6f, panel.w + 12f, panel.h + 12f, 14f,
                ColorUtil.rgba(68, 65, 77, 28));

        int tl = tintTopLeft ? COL_SECOND : COL_FIRST;
        Render2D.rect(panel.x, panel.y, panel.w, panel.h, 8f,
                tl, COL_FIRST, COL_FIRST, COL_FIRST);
        Render2D.outline(panel.x, panel.y, panel.w, panel.h, 8f, 0.8f, COL_FOURS_OUTLINE);
    }

    public static DockGeom drawDock(float dw, float dh, String activeId) {
        float width = 155f;
        float height = 29f;
        float x = dw * 0.5f - width * 0.5f;
        float y = dh - 39f;

        Render2D.rect(x - 4f, y - 4f, width + 8f, height + 8f, 10f,
                ColorUtil.rgba(68, 65, 77, 22));
        Render2D.rect(x, y, width, height, 6f,
                COL_SECOND, COL_FIRST, COL_SECOND, COL_FIRST);
        Render2D.outline(x, y, width, height, 6f, 0.8f, COL_FOURS_OUTLINE);

        for (int i = 0; i < DOCK_IDS.length; i++) {
            float ix = x + 7f + 25f * i;
            float iy = y + 6f;
            boolean active = DOCK_IDS[i].equals(activeId);
            if (active) {
                Render2D.rect(ix - 2f, iy - 2f, 20f, 20f, 10f,
                        ColorUtil.withAlpha(COL_ACCENT, 70));
            }
            Render2D.image(DOCK_ICONS[i], ix, iy, 16f, 16f, 0f,
                    active ? COL_ACCENT : COL_TEXT);
        }
        return new DockGeom(x, y, width, height);
    }

    public static String hitDock(DockGeom dock, float mx, float my) {
        if (dock == null || mx < dock.x || mx > dock.x + dock.w || my < dock.y || my > dock.y + dock.h) {
            return null;
        }
        for (int i = 0; i < DOCK_IDS.length; i++) {
            float ix = dock.x + 7f + 25f * i;
            float iy = dock.y + 6f;
            if (mx >= ix && mx <= ix + 16f && my >= iy && my <= iy + 16f) {
                return DOCK_IDS[i];
            }
        }
        return null;
    }

    public record PanelGeom(float x, float y, float w, float h) {
    }

    public record DockGeom(float x, float y, float w, float h) {
    }
}