package polaris.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.CameraEvent;
import polaris.api.events.impl.CameraPositionEvent;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.player.FreeLook;
import polaris.api.module.impl.visual.AfkCamera;
import polaris.api.module.impl.visual.CameraSettings;
import polaris.manager.Manager;
import polaris.utils.modules.warden.rotation.FreeLookController;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    private float getMaxZoom(float distance) {
        throw new AssertionError();
    }

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @ModifyExpressionValue(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float cataclysm$freeLookYaw(float original) {
        Angle afk = AfkCamera.getActiveAngle();
        if (afk != null) {
            return afk.getYaw();
        }
        
        if (!FreeLookController.active && !FreeLookController.captureMouse) {
            FreeLookController.floatValue = original;
        }
        if (FreeLookController.active) {
            return FreeLookController.floatValue;
        }
        Angle angle = FreeLook.getActiveAngle();
        if (angle != null) {
            return angle.getYaw();
        }

        Angle legitAngle = cataclysm$getLegitAuraCameraAngle();
        return legitAngle != null ? legitAngle.getYaw() : original;
    }

    @ModifyExpressionValue(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float cataclysm$freeLookPitch(float original) {
        Angle afk = AfkCamera.getActiveAngle();
        if (afk != null) {
            return afk.getPitch();
        }
        if (!FreeLookController.active && !FreeLookController.captureMouse) {
            FreeLookController.floatValue2 = original;
        }
        float pitch = original;
        if (FreeLookController.active) {
            pitch = FreeLookController.floatValue2;
        } else {
            Angle angle = FreeLook.getActiveAngle();
            if (angle != null) {
                pitch = angle.getPitch();
            } else {
                Angle legitAngle = cataclysm$getLegitAuraCameraAngle();
                if (legitAngle != null) {
                    pitch = legitAngle.getPitch();
                }
            }
        }
        return CameraSettings.applyTransitionPitch(pitch);
    }

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER), cancellable = true)
    private void polaris$cameraSettingsHook(Level level, Entity focusedEntity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        CameraEvent event = new CameraEvent(false, 4.0f, new Angle(yRot, xRot));
        Manager.postEvent(event);

        if (!(focusedEntity instanceof LocalPlayer player) || player.isSleeping()) {
            return;
        }

        
        
        if (AfkCamera.isCinematicActive()) {
            Angle angle = AfkCamera.getActiveAngle();
            if (angle != null) {
                setRotation(angle.getYaw(), angle.getPitch());
            }
            
            ci.cancel();
            return;
        }

        
        boolean animating = CameraSettings.isAnimatingDetached() || event.getDistance() > 0.04f;
        if (!event.isCancelled() || (!detached && !animating)) {
            return;
        }

        Angle angle = event.getAngle() != null ? event.getAngle() : new Angle(yRot, xRot);
        float reverse = Mth.clamp(event.getReverseAmount(), 0.0f, 1.0f);
        
        float pitch = Mth.lerp(reverse, angle.getPitch(), -angle.getPitch());
        float yaw = angle.getYaw() - 180.0f * reverse;
        float distance = Math.max(0.05f, event.getDistance());

        setRotation(yaw, pitch);
        move(event.isCameraClip() ? -distance : -getMaxZoom(distance), 0.0f, 0.0f);
        ci.cancel();
    }

    @Inject(method = "setPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$cameraPositionHook(Vec3 pos, CallbackInfo ci) {
        CameraPositionEvent event = Manager.postEvent(new CameraPositionEvent(pos));
        Vec3 nextPos = event.getPos();
        if (nextPos == null || !Double.isFinite(nextPos.x) || !Double.isFinite(nextPos.y) || !Double.isFinite(nextPos.z)) {
            return;
        }
        if (nextPos.distanceToSqr(pos) <= 1.0E-8D) {
            return;
        }
        setPosition(nextPos.x, nextPos.y, nextPos.z);
        ci.cancel();
    }

    @Unique
    private Angle cataclysm$getLegitAuraCameraAngle() {
        AuraModule aura = AuraModule.getInstance();
        if (aura == null || !aura.isEnabled() || !aura.isLegitMode()) {
            return null;
        }
        return AngleConnection.INSTANCE.getCurrentAngle();
    }
}
