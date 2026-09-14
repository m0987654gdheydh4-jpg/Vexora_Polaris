package polaris.theme;


public enum ThemeStyle {
    BLUR,
    GLASS;

    public static ThemeStyle fromHud(String raw) {
        if (raw == null) {
            return BLUR;
        }
        String s = raw.trim().toLowerCase();
        if (s.contains("glass") || s.contains("стекл")) {
            return GLASS;
        }
        return BLUR;
    }

    public String toHudMode() {
        return this == GLASS ? "Liquid Glass" : "Blur";
    }
}
