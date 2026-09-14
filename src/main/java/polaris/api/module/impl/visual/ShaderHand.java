package polaris.api.module.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.hands.ShaderHandMode;
import polaris.utils.render.hands.ShaderHandRenderer;


public final class ShaderHand extends Module {
    private static final String[] MODES = ShaderHandMode.displayNames();

    
    private static final float HAND_MOTION_RIGHT_X = 0.72f;
    private static final float HAND_MOTION_LEFT_X = 0.28f;
    private static final float HAND_MOTION_Y = 0.22f;

    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "Sirius shader painted over the hands.",
            ShaderHandMode.AQUA.displayName(), MODES));
    private final BooleanSetting animation = register(new BooleanSetting(
            "Animation", "Advance the shader clock. Off freezes the pattern in place.", true));
    private final NumberSetting timeSpeed = register(new NumberSetting(
            "Time Speed", "How fast the shader clock runs.", 1.0, 0.0, 5.0, 0.05));
    private final NumberSetting effectAlpha = register(new NumberSetting(
            "Effect Alpha", "Opacity of the effect over the hands.", 100.0, 0.0, 100.0, 1.0));
    private final NumberSetting patternSpeed = register(new NumberSetting(
            "Pattern Speed", "Pattern movement speed.", 1.0, 0.0, 5.0, 0.05));
    private final NumberSetting shift = register(new NumberSetting(
            "Shift", "Pattern shift.", 1.6, 0.0, 24.0, 0.1));

    public ShaderHand() {
        super("ShaderHand", "Zenith Sirius shader material on the first-person hands.", ModuleCategory.VISUAL);
        timeSpeed.visibleWhen(animation::getValue);
        patternSpeed.visibleWhen(this::isPatterned);
        shift.visibleWhen(this::isPatterned);
    }

    @Override
    protected void onEnable() {
        
        ShaderHandRenderer.setEnabled(true);
        sync();
    }

    @Override
    protected void onDisable() {
        ShaderHandRenderer.setEnabled(false);
    }

    @Override
    public void onTick(Minecraft client) {
        sync();
    }

    private void sync() {
        float motionX = HAND_MOTION_RIGHT_X;
        if (mc != null && mc.player != null && mc.player.getMainArm() == HumanoidArm.LEFT) {
            motionX = HAND_MOTION_LEFT_X;
        }
        ShaderHandRenderer.configure(
                selectedMode().ordinal(),
                animation.getValue(),
                timeSpeed.getFloat(),
                effectAlpha.getFloat(),
                patternSpeed.getFloat(),
                shift.getFloat(),
                motionX,
                HAND_MOTION_Y
        );
    }

    private ShaderHandMode selectedMode() {
        return ShaderHandMode.byDisplayName(mode.getValue());
    }

    private boolean isPatterned() {
        return selectedMode().isPatterned();
    }
}
