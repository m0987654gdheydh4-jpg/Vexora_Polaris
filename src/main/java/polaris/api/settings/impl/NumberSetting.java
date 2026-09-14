package polaris.api.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import polaris.api.settings.Setting;
import polaris.api.settings.SettingType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        super(name, description, defaultValue, SettingType.NUMBER);
        this.min = min;
        this.max = max;
        this.step = step <= 0.0 ? 0.1 : step;
        setValue(defaultValue);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    public float getFloat() {
        return getValue().floatValue();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        try {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("value")) {
                    parseAndSet(object.get("value"));
                }
                return;
            }
            parseAndSet(element);
        } catch (Exception ignored) {
            
        }
    }

    private void parseAndSet(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            setValue(primitive.getAsDouble());
            return;
        }
        if (primitive.isString()) {
            String raw = primitive.getAsString().trim();
            if (raw.isEmpty()) {
                return;
            }
            
            if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                return;
            }
            setValue(Double.parseDouble(raw));
        }
        
    }

    @Override
    protected Double normalize(Double value) {
        double safeValue = value == null ? min : value;
        double clamped = Math.max(min, Math.min(max, safeValue));
        
        
        double snapped = min + Math.round((clamped - min) / step) * step;
        
        
        
        return stripResidue(Math.max(min, Math.min(max, snapped)));
    }

    private static double stripResidue(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value)
                .setScale(9, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .doubleValue();
    }
}

