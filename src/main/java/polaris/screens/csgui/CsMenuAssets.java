package polaris.screens.csgui;

import polaris.utils.render.ui.Render2D;


public final class CsMenuAssets {
    private CsMenuAssets() {
    }

    public static final String SEARCH = "cataclysm:icons/menu/new/search.png";
    public static final String CHECKMARK = "cataclysm:icons/menu/new/checkmark.png";
    public static final String PLUS = "cataclysm:icons/menu/new/plus.png";
    public static final String CROSS = "cataclysm:icons/menu/new/cross.png";
    public static final String CLICK = "cataclysm:icons/menu/new/click.png";
    public static final String KEYBOARD = "cataclysm:icons/menu/new/keyboard.png";
    public static final String FRAME = "cataclysm:icons/menu/new/frame.png";
    public static final String BRUSH = "cataclysm:icons/menu/new/brush.png";
    public static final String CONTAINER = "cataclysm:icons/menu/new/container.png";
    
    public static final String GRAB = "cataclysm:icons/menu/new/grab.png";
    public static final String SLIDER = "cataclysm:icons/menu/new/slider.png";
    public static final String ENUM = "cataclysm:icons/menu/new/enum.png";
    public static final String MULTI_ENUM = "cataclysm:icons/menu/new/multienum.png";
    public static final String TEXT = "cataclysm:icons/menu/new/text.png";
    public static final String STAR = "cataclysm:icons/menu/new/star.png";
    public static final String STAR_FILL = "cataclysm:icons/menu/new/star_fill.png";
    public static final String ARROW = "cataclysm:icons/menu/new/arrow.png";
    public static final String ARROW_H = "cataclysm:icons/menu/new/arrow_horizontal.png";
    public static final String CLOSE = "cataclysm:icons/menu/close.png";
    public static final String CHECK = "cataclysm:icons/menu/check.png";
    public static final String PALETTE = "cataclysm:icons/menu/palette.png";
    public static final String SETTINGS = "cataclysm:icons/menu/settings.png";
    public static final String FOLDER = "cataclysm:icons/menu/new/folder_fill.png";
    public static final String CALENDAR = "cataclysm:icons/menu/new/calendar.png";
    public static final String SAVE = "cataclysm:icons/menu/new/save.png";
    public static final String DISPLAY = "cataclysm:icons/menu/new/display.png";
    public static final String TRASH = "cataclysm:icons/menu/new/trash.png";
    public static final String BINARY = "cataclysm:icons/menu/new/binary.png";
    public static final String THEMES_TAB = "cataclysm:icons/menu/new/tabs/themes.png";
    public static final String CONFIGS_TAB = "cataclysm:icons/menu/new/tabs/configs.png";
    
    public static final String COSMETICS_TAB = "cataclysm:icons/menu/new/tabs/render.png";
    public static final String TEXT_SCROLL = "cataclysm:icons/menu/new/text_scroll.png";
    public static final String SMILE = "cataclysm:icons/menu/new/smile.png";

    public static final String TAB_COMBAT = "cataclysm:icons/menu/new/tabs/combat.png";
    public static final String TAB_MOVEMENT = "cataclysm:icons/menu/new/tabs/movement.png";
    public static final String TAB_PLAYER = "cataclysm:icons/menu/new/tabs/player.png";
    public static final String TAB_RENDER = "cataclysm:icons/menu/new/tabs/render.png";
    public static final String TAB_MISC = "cataclysm:icons/menu/new/tabs/misc.png";
    public static final String TAB_CONFIGS = "cataclysm:icons/menu/new/tabs/configs.png";
    public static final String TAB_THEMES = "cataclysm:icons/menu/new/tabs/themes.png";

    
    public static final int[] PRESET_COLORS = {
            0xFFE7BC37, 
            0xFF8673FA, 
            0xFFB8FF8C, 
            0xFFC34040, 
            0xFF65677A, 
            0xFFD6D6E6, 
            0xFF59B35C, 
            0xFFEDCA5C, 
            0xFF568CDD, 
            0xFFFF6158, 
            0xFFAF52DE, 
            0xFF98D8C8, 
            0xFFFFB366, 
            0xFFFFD700, 
            0xFF575AC6, 
            0xFF7AD7C1, 
            0xFFF2A65A, 
            0xFF6A9FB5, 
    };

    public static void icon(String path, float x, float y, float size, int color) {
        if (path == null || size <= 0f) {
            return;
        }
        Render2D.image(path, x, y, size, size, 0f, color);
    }

    public static void iconCentered(String path, float cx, float cy, float size, int color) {
        icon(path, cx - size * 0.5f, cy - size * 0.5f, size, color);
    }
}
