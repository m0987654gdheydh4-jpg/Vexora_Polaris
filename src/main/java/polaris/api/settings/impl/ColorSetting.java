package polaris.api.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import polaris.api.drag.impl.HudTheme;
import polaris.api.settings.Setting;
import polaris.api.settings.SettingType;

import java.awt.Color;

public class ColorSetting extends Setting<Color> {
    private boolean syncTheme = false;

    public ColorSetting(String name, String description, Color defaultValue) {
        super(name, description, defaultValue, SettingType.COLOR);
    }

    public boolean isSyncTheme() {
        return syncTheme;
    }

    public void setSyncTheme(boolean syncTheme) {
        if (this.syncTheme == syncTheme) {
            return;
        }
        this.syncTheme = syncTheme;
        fireChanged();
    }

    
    public Color getRawValue() {
        return super.getValue();
    }

    @Override
    public Color getValue() {
        if (syncTheme) {
            try {
                int rgb = HudTheme.current().accentColorRgb;
                int alpha = super.getValue().getAlpha();
                return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
            } catch (Throwable ignored) {
                return super.getValue();
            }
        }
        return super.getValue();
    }

    @Override
    public JsonElement toJson() {
        Color color = super.getValue();
        JsonObject object = new JsonObject();
        object.addProperty("red", color.getRed());
        object.addProperty("green", color.getGreen());
        object.addProperty("blue", color.getBlue());
        object.addProperty("alpha", color.getAlpha());
        object.addProperty("rgba", color.getRGB());
        object.addProperty("syncTheme", syncTheme);
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("syncTheme")) {
            syncTheme = object.get("syncTheme").getAsBoolean();
        }
        if (object.has("rgba")) {
            setValue(new Color(object.get("rgba").getAsInt(), true));
            return;
        }
        int red = object.has("red") ? object.get("red").getAsInt() : super.getValue().getRed();
        int green = object.has("green") ? object.get("green").getAsInt() : super.getValue().getGreen();
        int blue = object.has("blue") ? object.get("blue").getAsInt() : super.getValue().getBlue();
        int alpha = object.has("alpha") ? object.get("alpha").getAsInt() : super.getValue().getAlpha();
        setValue(new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha)));
    }

    @Override
    protected Color normalize(Color value) {
        return value == null ? new Color(255, 255, 255, 255) : value;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}

