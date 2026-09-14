package polaris.api.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import polaris.api.settings.Setting;
import polaris.api.settings.SettingType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private final boolean renderDescription;
    
    private final Set<String> warnedUnknown = new HashSet<>();

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        this(name, description, true, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, String defaultValue, String... modes) {
        super(name, description, defaultValue, SettingType.MODE);
        this.modes = List.of(modes);
        this.renderDescription = renderDescription;
        setValue(defaultValue);
    }

    public List<String> getModes() {
        return modes;
    }

    public boolean shouldRenderDescription() {
        return renderDescription;
    }

    public boolean is(String mode) {
        return getValue().equals(normalize(mode));
    }

    public boolean isSelected(String mode) {
        return is(mode);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            setValue(element.getAsString());
        }
    }

    @Override
    protected String normalize(String value) {
        if (modes.isEmpty()) {
            return "";
        }
        if (value == null) {
            return modes.getFirst();
        }
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        
        
        
        
        
        if (warnedUnknown.add(value)) {
            System.err.println("[Polaris] Unknown mode '" + value + "' for setting '" + getName()
                    + "' — falling back to '" + modes.getFirst() + "'. Known modes: " + modes);
        }
        return modes.getFirst();
    }
}

