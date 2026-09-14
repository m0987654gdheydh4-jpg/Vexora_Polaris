package polaris.utils.render.hands;

import net.minecraft.resources.Identifier;


public enum ShaderHandMode {
    AQUA("Aqua", "sirius_aqua", false),
    FLOW("Flow", "sirius_flow", false),
    GALAXY("Galaxy", "sirius_galaxy", false),
    GAMER("Gamer", "sirius_gamer", false),
    GANG("Gang", "sirius_gang", true),
    GOLDEN("Golden", "sirius_golden", false),
    GUI_SHADER("GuiShader", "sirius_guishader", false),
    HIDEF("HiDef", "sirius_hidef", false),
    HOLYFUCK("HolyFuck", "sirius_holyfuck", true),
    HOMIE("Homie", "sirius_homie", false),
    PURPLE("Purple", "sirius_purple", false),
    SHELDON("Sheldon", "sirius_sheldon", false),
    SMOKE("Smoke", "sirius_smoke", false),
    SMOKY("Smoky", "sirius_smoky", false),
    TECHNO("Techno", "sirius_techno", false),
    YIPPIE_OWNS("YippieOwns", "sirius_yippieowns", false);

    private static final ShaderHandMode[] VALUES = values();

    private final String displayName;
    private final Identifier fragmentShader;
    private final Identifier pipelineId;
    private final boolean patterned;

    ShaderHandMode(String displayName, String shaderName, boolean patterned) {
        this.displayName = displayName;
        this.fragmentShader = Identifier.fromNamespaceAndPath("cataclysm", "effects/shaderhand/" + shaderName);
        this.pipelineId = Identifier.fromNamespaceAndPath("cataclysm", "pipeline/effects/shaderhand/" + shaderName);
        this.patterned = patterned;
    }

    public static ShaderHandMode byIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return AQUA;
        }
        return VALUES[index];
    }

    public static ShaderHandMode byDisplayName(String name) {
        for (ShaderHandMode mode : VALUES) {
            if (mode.displayName.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return AQUA;
    }

    public static String[] displayNames() {
        String[] names = new String[VALUES.length];
        for (int i = 0; i < VALUES.length; i++) {
            names[i] = VALUES[i].displayName;
        }
        return names;
    }

    public static int count() {
        return VALUES.length;
    }

    public String displayName() {
        return displayName;
    }

    public Identifier fragmentShader() {
        return fragmentShader;
    }

    public Identifier pipelineId() {
        return pipelineId;
    }

    
    public boolean isPatterned() {
        return patterned;
    }
}
