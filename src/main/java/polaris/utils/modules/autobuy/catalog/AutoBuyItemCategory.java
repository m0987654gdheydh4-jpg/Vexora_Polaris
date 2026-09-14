package polaris.utils.modules.autobuy.catalog;


public enum AutoBuyItemCategory {
    KRUSH("Крушитель"),
    SPHERES("Сферы"),
    TALISMANS("Талисманы"),
    POTIONS("Зелья"),
    HOLYWORLD("HolyWorld"),
    MISC("Разное");

    private final String displayName;

    AutoBuyItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    
    public boolean isHolyWorld() {
        return this == HOLYWORLD;
    }

    
    public boolean matchesServer(String serverMode) {
        if (serverMode == null || serverMode.isBlank()) {
            return !isHolyWorld();
        }
        boolean holy = serverMode.equalsIgnoreCase("HolyWorld");
        return isHolyWorld() == holy;
    }
}
