package polaris.utils.cosmetics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;


public final class FiguraBridge {
    private static final String AVATAR_MANAGER = "org.figuramc.figura.avatar.AvatarManager";

    private static Boolean available;
    private static Method loadLocalAvatar;
    private static Method clearAvatars;
    private static String appliedId = "";

    private FiguraBridge() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = resolve();
        }
        return available;
    }

    private static boolean resolve() {
        if (!FabricLoader.getInstance().isModLoaded("figura")) {
            return false;
        }
        try {
            Class<?> manager = Class.forName(AVATAR_MANAGER);
            loadLocalAvatar = manager.getMethod("loadLocalAvatar", Path.class);
            clearAvatars = manager.getMethod("clearAvatars", UUID.class);
            return true;
        } catch (Throwable ignored) {
            loadLocalAvatar = null;
            clearAvatars = null;
            return false;
        }
    }

    
    public static String appliedId() {
        return appliedId;
    }

    public static boolean isApplied(CosmeticEntry entry) {
        return entry != null && entry.id().equals(appliedId);
    }

    
    public static boolean apply(CosmeticEntry entry) {
        if (entry == null || !isAvailable()) {
            return false;
        }
        try {
            loadLocalAvatar.invoke(null, entry.folder());
            appliedId = entry.id();
            return true;
        } catch (Throwable throwable) {
            System.err.println("[Polaris] Failed to apply cosmetic " + entry.id() + ": " + throwable);
            return false;
        }
    }

    
    public static boolean clear() {
        appliedId = "";
        if (!isAvailable()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }
        try {
            clearAvatars.invoke(null, mc.player.getUUID());
            return true;
        } catch (Throwable throwable) {
            System.err.println("[Polaris] Failed to clear cosmetic: " + throwable);
            return false;
        }
    }
}
