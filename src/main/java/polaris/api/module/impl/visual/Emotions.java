package polaris.api.module.impl.visual;

import org.lwjgl.glfw.GLFW;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.emotions.EmotionWheelManager;

public class Emotions extends Module {
    private static Emotions instance;

    private final BindSetting wheelBind = register(new BindSetting(
            "Колесо эмоций",
            "Удерживай, чтобы открыть колесо выбора эмоции.",
            KeyBind.keyboard(GLFW.GLFW_KEY_V)
    ));

    public Emotions() {
        super("Emotions", "Emotion wheel with animated player poses.", ModuleCategory.VISUAL);
        instance = this;
        setEnabled(true);
    }

    public static Emotions getInstance() {
        return instance;
    }

    public BindSetting getWheelBind() {
        return wheelBind;
    }

    @Override
    protected void onDisable() {
        EmotionWheelManager manager = EmotionWheelManager.getInstance();
        if (manager != null) {
            manager.closeWheel(true);
        }
    }
}
