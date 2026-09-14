package polaris.utils.modules.warden.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;


public final class FreeLookController {
    
    public static boolean active;
    
    public static boolean flag;
    
    public static boolean captureMouse;
    public static float floatValue;
    public static float floatValue2;

    private FreeLookController() {
    }

    public static boolean redirectsMouse() {
        return active;
    }

    public static boolean tracksMouse() {
        return active || captureMouse;
    }

    public static void syncFromPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        floatValue = player.getYRot();
        floatValue2 = player.getXRot();
    }

    
    public static void enableCameraDetach() {
        if (!active) {
            syncFromPlayer();
            active = true;
        }
        captureMouse = false;
    }

    
    public static void beginMouseCapture() {
        if (!captureMouse && !active) {
            syncFromPlayer();
            captureMouse = true;
        }
    }

    public static void applyMouseDelta(double cursorDeltaX, double cursorDeltaY) {
        floatValue += (float) cursorDeltaX * 0.15F;
        floatValue2 = Mth.clamp(floatValue2 + (float) cursorDeltaY * 0.15F, -90.0F, 90.0F);
    }

    
    public static void restorePlayerLook() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.setYRot(floatValue);
        player.setXRot(floatValue2);
        player.yRotO = floatValue;
        player.xRotO = floatValue2;
        player.yHeadRot = floatValue;
        player.yHeadRotO = floatValue;
        player.yBodyRot = floatValue;
        player.yBodyRotO = floatValue;
    }

    public static void release() {
        if (active || captureMouse) {
            restorePlayerLook();
        }
        active = flag;
        captureMouse = false;
    }
}
