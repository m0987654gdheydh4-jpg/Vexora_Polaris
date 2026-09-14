package polaris.screens.modernui.settings;

import polaris.api.settings.Setting;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ButtonSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;

final class SettingCardFactory {
    private SettingCardFactory() {
    }

    static SettingCardComponent<?> create(Setting<?> setting) {
        if (setting instanceof ModeSetting modeSetting) {
            return new ModeSettingCard(modeSetting);
        }
        if (setting instanceof MultiModeSetting multiModeSetting) {
            return new MultiSettingCard(multiModeSetting);
        }
        if (setting instanceof NumberSetting numberSetting) {
            return new SliderSettingCard(numberSetting);
        }
        if (setting instanceof BooleanSetting booleanSetting) {
            return new BooleanSettingCard(booleanSetting);
        }
        if (setting instanceof ColorSetting colorSetting) {
            return new ColorSettingCard(colorSetting);
        }
        if (setting instanceof BindSetting bindSetting) {
            return new BindSettingCard(bindSetting);
        }
        if (setting instanceof StringSetting stringSetting) {
            return new TextSettingCard(stringSetting);
        }
        if (setting instanceof ButtonSetting buttonSetting) {
            return new ButtonSettingCard(buttonSetting);
        }
        return null;
    }
}

