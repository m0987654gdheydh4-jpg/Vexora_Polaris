package polaris.utils.modules.warden.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;


public final class RotationController {
    public static RotationControllerState2 rotationControllerState2 = RotationControllerState2.IDLE;
    public static float floatValue;
    public static float floatValue2;
    public static float floatValue3;
    public static float floatValue4;
    public static int intValue;
    public static int intValue2;
    public static int intValue3;
    public static Rotation rotation;
    public static boolean flag;

    private RotationController() {
    }

    public static boolean check() {
        return !rotationControllerState2.equals(RotationControllerState2.IDLE);
    }

    public static void invoke3(Rotation rotation2, float f, float g, float h, float i, int j, int k, boolean bl) {
        rotation = rotation2;
        floatValue = Math.max(0.05F, f);
        floatValue2 = Math.max(0.05F, g);
        floatValue3 = h;
        floatValue4 = i;
        intValue = j;
        intValue2 = k;
        flag = bl;
        if (rotation2 != null) {
            
            FreeLookController.active = false;
            if (rotationControllerState2 == RotationControllerState2.IDLE) {
                FreeLookController.beginMouseCapture();
            }
            rotationControllerState2 = RotationControllerState2.AIM;
            applyToPlayer(rotation2, false);
        } else {
            clear();
        }
    }

    
    public static void applyToPlayer(Rotation target, boolean snapIfClose) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || target == null) {
            return;
        }
        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        float dy = Mth.wrapDegrees(target.floatValue - curYaw);
        float dp = target.floatValue2 - curPitch;

        float maxYaw = floatValue > 0.0F ? floatValue : 45.0F;
        float maxPitch = floatValue2 > 0.0F ? floatValue2 : 45.0F;
        if (snapIfClose && Math.hypot(dy, dp) < 3.0) {
            maxYaw = 180.0F;
            maxPitch = 180.0F;
        }
        float stepYaw = Mth.clamp(dy, -maxYaw, maxYaw);
        float stepPitch = Mth.clamp(dp, -maxPitch, maxPitch);

        float newYaw = curYaw + stepYaw;
        float newPitch = Mth.clamp(curPitch + stepPitch, -90.0F, 90.0F);

        player.setYRot(newYaw);
        player.setXRot(newPitch);
        player.yRotO = newYaw;
        player.xRotO = newPitch;
        player.yHeadRot = newYaw;
        player.yHeadRotO = newYaw;
        player.yBodyRot = newYaw;
        player.yBodyRotO = newYaw;
    }

    
    public static void tickApply() {
        if (rotationControllerState2 == RotationControllerState2.AIM && rotation != null) {
            applyToPlayer(rotation, true);
        } else if (rotationControllerState2 == RotationControllerState2.RESET) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                clear();
                return;
            }
            Rotation back = new Rotation(FreeLookController.floatValue, FreeLookController.floatValue2);
            applyToPlayer(back, true);
            float dy = Math.abs(Mth.wrapDegrees(player.getYRot() - FreeLookController.floatValue));
            float dp = Math.abs(player.getXRot() - FreeLookController.floatValue2);
            if (Math.hypot(dy, dp) < 1.0F) {
                clear();
            }
        }
    }

    
    public static void clear() {
        if (rotationControllerState2 != RotationControllerState2.IDLE && FreeLookController.active) {
            FreeLookController.restorePlayerLook();
            FreeLookController.active = FreeLookController.flag;
        }
        rotationControllerState2 = RotationControllerState2.IDLE;
        rotation = null;
        intValue = 0;
        flag = false;
    }

    
    public static void hardReset() {
        if (FreeLookController.active || FreeLookController.captureMouse) {
            FreeLookController.release();
        } else if (rotationControllerState2 != RotationControllerState2.IDLE) {
            FreeLookController.active = FreeLookController.flag;
        }
        rotationControllerState2 = RotationControllerState2.IDLE;
        rotation = null;
        intValue = 0;
        intValue2 = 0;
        intValue3 = 0;
        flag = false;
    }

    public enum RotationControllerState2 {
        IDLE,
        AIM,
        RESET
    }
}
