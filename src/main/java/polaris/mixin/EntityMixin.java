package polaris.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import polaris.api.module.impl.combat.HitBoxModule;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.visual.NoRender;
import polaris.api.events.impl.PlayerVelocityStrafeEvent;
import polaris.api.events.impl.PushEvent;
import polaris.manager.Manager;
import polaris.utils.player.BaritoneMovementHelper;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void cataclysm$expandCombatHitBox(CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof LivingEntity living) {
            cir.setReturnValue(HitBoxModule.apply(living, cir.getReturnValue()));
        }
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$disableGlow(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.shouldHideGlow() && (Object) this instanceof Player) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$handleEntityPush(Entity entity, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        Entity self = (Entity) (Object) this;
        if (player == null || (self != player && entity != player)) {
            return;
        }
        PushEvent event = Manager.postEvent(new PushEvent(PushEvent.Type.COLLISION));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushedByFluid()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$handleFluidPush(CallbackInfoReturnable<Boolean> cir) {
        if (!cataclysm$isLocalPlayer()) {
            return;
        }
        PushEvent event = Manager.postEvent(new PushEvent(PushEvent.Type.WATER));
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 cataclysm$fixMoveRelative(Vec3 movementInput, float speed, float yaw) {
        if (cataclysm$isLocalPlayer()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (BaritoneMovementHelper.isMovementControlled(player)) {
                return cataclysm$getInputVector(movementInput, speed, yaw);
            }
            PlayerVelocityStrafeEvent event = Manager.postEvent(new PlayerVelocityStrafeEvent(
                    movementInput,
                    speed,
                    yaw,
                    cataclysm$getInputVector(movementInput, speed, yaw)
            ));
            return event.getVelocity();
        }
        return cataclysm$getInputVector(movementInput, speed, yaw);
    }

    @ModifyVariable(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float cataclysm$packetPitch(float pitch) {
        if (cataclysm$isLocalPlayer() && AngleConnection.INSTANCE.shouldApplyPacketRotation()) {
            return AngleConnection.INSTANCE.getPacketPitch();
        }
        return pitch;
    }

    @ModifyVariable(method = "calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float cataclysm$packetYaw(float yaw) {
        if (cataclysm$isLocalPlayer() && AngleConnection.INSTANCE.shouldApplyPacketRotation()) {
            return AngleConnection.INSTANCE.getPacketYaw();
        }
        return yaw;
    }

    @Unique
    private boolean cataclysm$isLocalPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (Object) this == player;
    }

    @Unique
    private static Vec3 cataclysm$getInputVector(Vec3 movementInput, float speed, float yaw) {
        double length = movementInput.lengthSqr();
        if (length < 1.0E-7D) {
            return Vec3.ZERO;
        }
        Vec3 scaled = (length > 1.0D ? movementInput.normalize() : movementInput).scale(speed);
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin);
    }
}

