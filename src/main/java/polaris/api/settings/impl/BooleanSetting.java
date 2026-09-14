package polaris.api.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import polaris.api.settings.Setting;
import polaris.api.settings.SettingType;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue, SettingType.BOOLEAN);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        try {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                setValue(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                setValue(primitive.getAsDouble() != 0.0);
            } else if (primitive.isString()) {
                String raw = primitive.getAsString().trim();
                if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
                    setValue(true);
                } else if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
                    setValue(false);
                }
            }
        } catch (Exception ignored) {
        }
    }
}

