package polaris.theme;

import java.awt.Color;
import java.util.List;


public final class ThemeBuiltins {
    private ThemeBuiltins() {
    }

    public static List<ThemePreset> create() {
        return List.of(
                pack("ocean", "Ocean", new Color(120, 180, 255), new Color(7, 7, 9), ThemeStyle.BLUR),
                pack("violet", "Violet", new Color(123, 92, 250), new Color(12, 10, 18), ThemeStyle.BLUR),
                pack("crimson", "Crimson", new Color(255, 90, 120), new Color(14, 8, 10), ThemeStyle.BLUR),
                pack("emerald", "Emerald", new Color(80, 220, 160), new Color(7, 12, 10), ThemeStyle.BLUR),
                pack("amber", "Amber", new Color(255, 170, 60), new Color(14, 12, 8), ThemeStyle.BLUR),
                pack("midnight", "Midnight", new Color(90, 140, 255), new Color(6, 8, 14), ThemeStyle.GLASS),
                pack("snow", "Snow", new Color(220, 230, 255), new Color(18, 18, 22), ThemeStyle.BLUR),
                pack("magenta", "Magenta", new Color(230, 120, 255), new Color(14, 8, 16), ThemeStyle.GLASS)
        );
    }

    private static ThemePreset pack(String id, String name, Color accent, Color bg, ThemeStyle style) {
        ThemeState state = new ThemeState(
                accent,
                bg,
                Color.WHITE,
                ThemeState.deriveOutline(accent),
                style,
                style == ThemeStyle.GLASS ? 18f : 26f,
                0.94f,
                7f,
                style == ThemeStyle.GLASS ? 32f : 25f,
                0.08f,
                true,
                0.32f,
                true,
                6f,
                0.5f
        );
        return ThemePreset.builtin(id, name, state);
    }
}
