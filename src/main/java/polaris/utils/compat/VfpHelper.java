package polaris.utils.compat;

import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Method;

public final class VfpHelper {
    private static Object screenInstance;
    private static Method openMethod;
    private static Method getTargetVersion;
    private static Method getNameMethod;

    static {
        try {
            // Ищем классы VFP в рантайме
            Class<?> screenClass = Class.forName("com.viaversion.viafabricplus.screen.impl.ProtocolSelectionScreen");
            screenInstance = screenClass.getField("INSTANCE").get(null);
            openMethod = screenClass.getMethod("open", Screen.class);

            Class<?> translatorClass = Class.forName("com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator");
            getTargetVersion = translatorClass.getMethod("getTargetVersion");
        } catch (Throwable ignored) {
            // Если мода нет в папке mods, просто игнорируем
        }
    }

    private VfpHelper() {}

    public static boolean isAvailable() {
        return screenInstance != null && openMethod != null;
    }

    public static String getVersionName() {
        if (getTargetVersion == null) return null;
        try {
            Object version = getTargetVersion.invoke(null);
            if (version == null) return null;
            if (getNameMethod == null) {
                getNameMethod = version.getClass().getMethod("getName");
            }
            return (String) getNameMethod.invoke(version);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void openScreen(Screen parent) {
        if (!isAvailable()) return;
        try {
            openMethod.invoke(screenInstance, parent);
        } catch (Throwable ignored) {
        }
    }
}