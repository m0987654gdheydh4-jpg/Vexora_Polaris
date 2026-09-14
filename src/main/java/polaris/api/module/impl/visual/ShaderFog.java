package polaris.api.module.impl.visual;

import com.mojang.blaze3d.pipeline.RenderTarget;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.post.shaderfog.ShaderFogRenderer;

import java.awt.Color;


public final class ShaderFog extends Module {
    private static ShaderFog instance;

    private static final String[] MODES = {
            "Gradient", "Galaxy", "Aqua", "Purple", "Overcast"
    };

    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "Sky shader style.", "Gradient", MODES));
    private final ColorSetting firstColor = register(new ColorSetting(
            "First Color", "Gradient first sky color.", new Color(86, 162, 255, 255)));
    private final ColorSetting secondColor = register(new ColorSetting(
            "Second Color", "Gradient second sky color.", new Color(190, 112, 255, 255)));
    private final ColorSetting purpleColor = register(new ColorSetting(
            "Purple Color", "Purple sky tint.", new Color(185, 56, 195, 255)));
    private final NumberSetting intensity = register(new NumberSetting(
            "Intensity", "How strongly sky is replaced (0–1).", 1.0, 0.0, 1.5, 0.05));
    private final NumberSetting timeSpeed = register(new NumberSetting(
            "Time Speed", "Animation speed.", 0.1, 0.0, 5.0, 0.05));
    private final BooleanSetting lightning = register(new BooleanSetting(
            "Lightning", "Lightning bolts and flashes in the storm sky.", true));
    private final NumberSetting lightningPower = register(new NumberSetting(
            "Bolt Power", "Brightness of the bolt and its flash %.", 100.0, 10.0, 200.0, 5.0));
    private final NumberSetting lightningRate = register(new NumberSetting(
            "Strike Interval", "Seconds between strike attempts. Roughly half of them fire.",
            9.0, 2.0, 45.0, 0.5));
    private final NumberSetting worldFlash = register(new NumberSetting(
            "World Flash", "How much a strike lights up the terrain %. 0 keeps the world "
                    + "completely untouched.", 35.0, 0.0, 100.0, 5.0));

    public ShaderFog() {
        super("ShaderFog", "Replaces the sky with Gradient / Galaxy / Aqua / Purple / Overcast (not world fog).", ModuleCategory.VISUAL);
        instance = this;
        firstColor.visibleWhen(() -> mode.is("Gradient"));
        secondColor.visibleWhen(() -> mode.is("Gradient"));
        purpleColor.visibleWhen(() -> mode.is("Purple"));
        timeSpeed.visibleWhen(() -> !mode.is("Gradient"));
        lightning.visibleWhen(() -> mode.is("Overcast"));
        lightningPower.visibleWhen(() -> mode.is("Overcast") && lightning.getValue());
        lightningRate.visibleWhen(() -> mode.is("Overcast") && lightning.getValue());
        worldFlash.visibleWhen(() -> mode.is("Overcast") && lightning.getValue());
    }

    public static ShaderFog getInstance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        ShaderFogRenderer.clear();
    }

    public void onAfterTranslucent(RenderTarget target) {
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) {
            return;
        }
        if (ShaderFogRenderer.isDisabledAfterError()) {
            return;
        }
        int modeId = 0;
        for (int i = 0; i < MODES.length; i++) {
            if (mode.is(MODES[i])) {
                modeId = i;
                break;
            }
        }
        ShaderFogRenderer.apply(
                target,
                modeId,
                intensity.getFloat(),
                timeSpeed.getFloat(),
                firstColor.getValue().getRGB(),
                secondColor.getValue().getRGB(),
                purpleColor.getValue().getRGB(),
                lightning.getValue() ? lightningPower.getFloat() / 100f : 0f,
                lightningRate.getFloat(),
                worldFlash.getFloat() / 100f
        );
    }
}
