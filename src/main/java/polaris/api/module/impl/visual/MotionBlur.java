package polaris.api.module.impl.visual;

import net.minecraft.client.Minecraft;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.post.motionblur.MotionBlurRenderer;


public final class MotionBlur extends Module {
    private static MotionBlur instance;

    private final NumberSetting smoothness = register(new NumberSetting(
            "Smoothness", "How much of the previous frames is kept (%).", 68.0, 0.0, 100.0, 1.0));
    private final BooleanSetting cutProtection = register(new BooleanSetting(
            "Cut Protection", "Drop the trail where the image changes drastically.", true));

    public MotionBlur() {
        super("MotionBlur", "Physically-flavoured motion blur, makes the picture read smoother.", ModuleCategory.VISUAL);
        instance = this;
    }

    public static MotionBlur getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        
        
        MotionBlurRenderer.clearError();
    }

    @Override
    protected void onDisable() {
        MotionBlurRenderer.reset();
    }

    
    public static void applyIfActive() {
        MotionBlur module = instance;
        if (module == null || !module.isEnabled() || MotionBlurRenderer.isDisabledAfterError()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) {
            MotionBlurRenderer.reset();
            return;
        }

        float raw = Math.clamp(module.smoothness.getFloat() / 100.0f, 0.0f, 1.0f);
        
        float eased = raw * raw * (3.0f - 2.0f * raw);
        
        
        float blend = eased * 0.95f;
        
        
        float threshold = module.cutProtection.getValue() ? 0.55f : 10.0f;
        MotionBlurRenderer.apply(blend, threshold);
    }
}
