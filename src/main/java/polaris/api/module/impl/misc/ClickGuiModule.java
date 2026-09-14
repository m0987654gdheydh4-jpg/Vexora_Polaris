package polaris.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;
import polaris.screens.clickgui.dropdown.DropDownScreen;
import polaris.screens.csgui.CsGui;
import polaris.api.settings.bind.KeyBind;
import org.lwjgl.glfw.GLFW;

public class ClickGuiModule extends Module {
    public static final String STANDARD = "Standard";
    public static final String CS_GUI = "CsGui";

    private final ModeSetting interfaceSetting = register(new ModeSetting(
            "Interface", "Choose the click GUI interface.", CS_GUI, STANDARD, CS_GUI));
    private final ModeSetting languageSetting = register(new ModeSetting(
            "Language", "Language used by CsGui.", "Русский", "Русский", "English"));
    private final NumberSetting scaleSetting = register(new NumberSetting(
            "CsGui Scale", "CsGui interface scale (%).", 100.0, 75.0, 130.0, 1.0));
    private final BooleanSetting descriptionsSetting = register(new BooleanSetting(
            "Module Descriptions", "Show descriptions below module names.", true));
    private final StringSetting altProfilesSetting = register(new StringSetting(
            "Alt Profiles", "Saved offline profile names for CsGui.", "", 4096));
    private final StringSetting selectedAltSetting = register(new StringSetting(
            "Selected Alt", "The profile selected in CsGui.", "", 64));
    
    private final BooleanSetting openScaleSetting = register(new BooleanSetting(
            "Open Scale", "Scale ClickGUI from half size to full while opening.", true));
    
    private final BooleanSetting openDarknessSetting = register(new BooleanSetting(
            "Open Darkness", "Darken the world behind ClickGUI while opening.", true));
    
    private final BooleanSetting openCameraSetting = register(new BooleanSetting(
            "Open Camera", "Offset ClickGUI with camera look while open.", true));
    public static final String BG_STANDARD = "Standard";
    public static final String BG_HOLOGRAM = "Hologram";

    
    private final ModeSetting backgroundSetting = register(new ModeSetting(
            "Background", "Backdrop behind the menu.", BG_HOLOGRAM, BG_STANDARD, BG_HOLOGRAM));
    private final NumberSetting holoBlurMax = register(new NumberSetting(
            "Holo Blur", "Maximum blur radius of the holographic islands.", 32.0, 8.0, 64.0, 1.0));
    private final NumberSetting holoTint = register(new NumberSetting(
            "Holo Tint", "Iridescent sheen.", 0.6, 0.0, 1.0, 0.01));
    private final NumberSetting holoMousePull = register(new NumberSetting(
            "Holo Cursor Pull", "How much the islands are pulled toward the cursor.", 0.18, 0.0, 0.4, 0.01));
    private final NumberSetting holoClarity = register(new NumberSetting(
            "Holo Clarity", "Radius of the clear area around the cursor.", 0.28, 0.05, 0.6, 0.01));
    private final NumberSetting holoIslandSize = register(new NumberSetting(
            "Holo Island Size", "Size of the blurred islands.", 1.8, 0.8, 3.5, 0.05));
    private final NumberSetting holoFlow = register(new NumberSetting(
            "Holo Flow", "How fast the islands drift.", 0.55, 0.0, 1.5, 0.01));
    private final NumberSetting holoContrast = register(new NumberSetting(
            "Holo Contrast", "Island edge contrast.", 0.55, 0.0, 1.0, 0.01));
    private final NumberSetting holoVignette = register(new NumberSetting(
            "Holo Vignette", "Corner darkening.", 0.35, 0.0, 1.0, 0.01));
    private final NumberSetting holoBrightness = register(new NumberSetting(
            "Holo Brightness", "Island brightness.", 0.55, 0.0, 1.0, 0.01));
    private final NumberSetting holoSaturation = register(new NumberSetting(
            "Holo Saturation", "Backdrop saturation boost.", 0.45, 0.0, 1.0, 0.01));

    
    private final BooleanSetting moduleAppearSetting = register(new BooleanSetting(
            "Module Appear", "Cards fade and slide in one after another on category switch.", true));
    
    private final BooleanSetting openScanSetting = register(new BooleanSetting(
            "Open Scan", "World scan wave from your position when opening ClickGUI.", true));

    public ClickGuiModule() {
        super("ClickGUI", "Opens the client click GUI", ModuleCategory.MISC);
        setHidden(false);
        setBind(KeyBind.keyboard(GLFW.GLFW_KEY_RIGHT_SHIFT));
        languageSetting.setVisible(false);
        
        scaleSetting.setVisible(true);
        descriptionsSetting.setVisible(false);
        altProfilesSetting.setVisible(false);
        selectedAltSetting.setVisible(false);
        openScaleSetting.setVisible(true);
        openDarknessSetting.setVisible(true);
        openCameraSetting.setVisible(true);
        openScanSetting.setVisible(true);
        moduleAppearSetting.setVisible(true);
        for (NumberSetting holo : holoParams()) {
            holo.visibleWhen(() -> backgroundSetting.is(BG_HOLOGRAM));
        }
    }

    public ModeSetting backgroundSetting() {
        return backgroundSetting;
    }

    public boolean isHologramBackground() {
        return backgroundSetting.is(BG_HOLOGRAM);
    }

    private NumberSetting[] holoParams() {
        return new NumberSetting[]{
                holoBlurMax, holoTint, holoMousePull, holoClarity, holoIslandSize,
                holoFlow, holoContrast, holoVignette, holoBrightness, holoSaturation
        };
    }

    
    public float[] holoShaderParams(float intensity) {
        return new float[]{
                intensity,
                holoBlurMax.getFloat(),
                holoTint.getFloat(),
                holoMousePull.getFloat(),
                holoIslandSize.getFloat(),
                holoFlow.getFloat(),
                holoClarity.getFloat(),
                holoContrast.getFloat(),
                holoVignette.getFloat(),
                holoBrightness.getFloat(),
                holoSaturation.getFloat()
        };
    }

    public void openGui(Minecraft client) {
        if (client == null) {
            return;
        }
        if (interfaceSetting.is(CS_GUI)) {
            if (client.screen instanceof CsGui) {
                
                client.setScreen(null);
                return;
            }
            client.setScreen(new CsGui(client.screen));
            return;
        }

        if (client.screen instanceof DropDownScreen) {
            client.setScreen(null);
        } else {
            DropDownScreen.INSTANCE.openGui();
        }
    }

    public BindSetting menuBindSetting() {
        return getBindSetting();
    }

    public ModeSetting interfaceSetting() {
        return interfaceSetting;
    }

    public ModeSetting languageSetting() {
        return languageSetting;
    }

    public NumberSetting scaleSetting() {
        return scaleSetting;
    }

    public BooleanSetting descriptionsSetting() {
        return descriptionsSetting;
    }

    public StringSetting altProfilesSetting() {
        return altProfilesSetting;
    }

    public StringSetting selectedAltSetting() {
        return selectedAltSetting;
    }

    public BooleanSetting openScaleSetting() {
        return openScaleSetting;
    }

    public BooleanSetting openDarknessSetting() {
        return openDarknessSetting;
    }

    public BooleanSetting openCameraSetting() {
        return openCameraSetting;
    }

    public BooleanSetting moduleAppearSetting() {
        return moduleAppearSetting;
    }

    public BooleanSetting openScanSetting() {
        return openScanSetting;
    }

    public boolean isCsGuiSelected() {
        return interfaceSetting.is(CS_GUI);
    }
}
