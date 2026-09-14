package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.InputEvent;
import polaris.api.events.impl.RotationUpdateEvent;
import polaris.api.events.types.EventPhase;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.AngleConfig;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.impl.LinearConstructor;
import polaris.api.module.impl.combat.aura.target.TargetFinder;
import polaris.api.module.impl.combat.aura.util.TaskPriority;
import polaris.api.module.impl.combat.macetarget.attack.AttackHandler;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.inventory.InventoryFlowManager;
import polaris.utils.inventory.InventoryTask;
import polaris.utils.inventory.lookup.InventoryUtils;

public final class AutoMace extends Module {
    private enum JumpPhase { IDLE, ROTATING, USING, COOLDOWN }

    private static final float THROW_PITCH = 90.0F;
    private static final int ROTATION_WAIT_TICKS = 2;
    private static final long USE_TIMEOUT_MS = 750L;

    private final MultiModeSetting targetType = register(new MultiModeSetting(
            "Targets", "Target type filter.", new String[]{"Players", "Mobs", "Animals"}, "Players"));
    private final NumberSetting targetRange = register(new NumberSetting(
            "Target Range", "Max distance to search and chase target.", 30.0, 5.0, 64.0, 1.0));
    private final NumberSetting maceRange = register(new NumberSetting(
            "Mace Range", "Hit with mace at this distance.", 3.0, 1.0, 6.0, 0.1));
    private final BooleanSetting useWindCharges = register(new BooleanSetting(
            "Wind Jump", "Jump on wind charges to close distance. OFF = no jumps at all.", true));
    private final NumberSetting chargeDelay = register(new NumberSetting(
            "Charge Delay", "Ticks between wind charge throws.", 8.0, 0.0, 40.0, 1.0));
    private final BooleanSetting moveTowards = register(new BooleanSetting(
            "Move To Target", "Hold WASD towards target, staying as close as possible.", true));

    private final NumberSetting hitDelay = register(new NumberSetting(
            "Hit Delay", "Ticks between timer hits after the first instant hit.", 20.0, 1.0, 40.0, 1.0));
    private final BooleanSetting antiKick = register(new BooleanSetting(
            "AntiKick", "Strict hitbox/reach validation to avoid hitbox kicks.", true));

    private final TargetFinder targetFinder = new TargetFinder();
    private final AttackHandler attackHandler = new AttackHandler();

    private LivingEntity target;
    private JumpPhase jumpPhase = JumpPhase.IDLE;
    private int rotationTicks;
    private int cooldownTicks;
    private long useStartedAt;
    private int hitCooldown;
    private boolean wasInRange;

    public AutoMace() {
        super("Auto Mace", "Wind-jumps to the target and smashes it with mace at close range.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetJump();
        resetHits();
        target = null;
        attackHandler.reset();
    }

    @Override
    protected void onDisable() {
        AngleConnection.INSTANCE.startReturning();
        targetFinder.releaseTarget();
        target = null;
        attackHandler.reset();
        resetJump();
        resetHits();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) {
            resetJump();
            resetHits();
            return;
        }

        if (target == null || !target.isAlive()) {
            resetJump();
            resetHits();
            return;
        }

        float dist = client.player.distanceTo(target);
        boolean inRange = dist <= maceRange.getFloat();

        if (inRange && !wasInRange) {
            hitCooldown = 0;
        }
        wasInRange = inRange;

        if (hitCooldown > 0) {
            hitCooldown--;
        }

        if (!useWindCharges.getValue() || inRange) {
            resetJump();
            return;
        }

        switch (jumpPhase) {
            case IDLE -> {
                if (hasCharges()) {
                    jumpPhase = JumpPhase.ROTATING;
                    rotationTicks = 0;
                    useStartedAt = 0L;
                    AngleConnection.INSTANCE.forcePacketRotation(1);
                }
            }
            case ROTATING -> {
                applyThrowRotation(client);
                rotationTicks++;
                Angle current = AngleConnection.INSTANCE.getRotation();
                boolean rotationReady = current != null && current.getPitch() >= 80.0F;
                if (rotationReady && rotationTicks >= ROTATION_WAIT_TICKS) {
                    scheduleUse(client);
                    jumpPhase = JumpPhase.USING;
                    useStartedAt = System.currentTimeMillis();
                } else if (rotationTicks > 10) {
                    resetJump();
                }
            }
            case USING -> {

                applyThrowRotation(client);
                if (InventoryTask.isSwapAndUseIdle()
                        && InventoryFlowManager.isIdle()
                        && System.currentTimeMillis() - useStartedAt >= 50L) {
                    jumpPhase = JumpPhase.COOLDOWN;
                    cooldownTicks = chargeDelay.getValue().intValue();
                } else if (System.currentTimeMillis() - useStartedAt > USE_TIMEOUT_MS) {
                    resetJump();
                }
            }
            case COOLDOWN -> {
                cooldownTicks--;
                if (cooldownTicks <= 0) {
                    jumpPhase = JumpPhase.IDLE;
                }
            }
        }
    }

    private void applyThrowRotation(Minecraft client) {
        float yaw = yawToTarget(client);
        client.player.setYRot(yaw);
        client.player.setXRot(THROW_PITCH);
        client.player.yHeadRot = yaw;
    }

    private boolean hasCharges() {
        return InventoryUtils.findItemInHotbar(Items.WIND_CHARGE) != -1
                || InventoryUtils.findItemInInventory(Items.WIND_CHARGE) != -1;
    }

    private void scheduleUse(Minecraft client) {
        Angle throwAngle = new Angle(yawToTarget(client), THROW_PITCH);
        int hotbarSlot = InventoryUtils.findItemInHotbar(Items.WIND_CHARGE);
        if (hotbarSlot != -1) {
            InventoryTask.useHotbarItem(hotbarSlot, client.player.getInventory().getSelectedSlot(), true);
            return;
        }
        InventoryTask.swapAndUse(Items.WIND_CHARGE, true, throwAngle);
    }

    private float yawToTarget(Minecraft client) {
        if (target == null || client.player == null) {
            return client.player != null ? client.player.getYRot() : 0F;
        }
        Vec3 diff = target.position().subtract(client.player.position());
        return (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
    }

    private void resetJump() {
        jumpPhase = JumpPhase.IDLE;
        rotationTicks = 0;
        cooldownTicks = 0;
        useStartedAt = 0L;
    }

    private void resetHits() {
        hitCooldown = 0;
        wasInRange = false;
    }

    @SubscribeEvent
    private void onRotationUpdate(RotationUpdateEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        if (event.getPhase() == EventPhase.PRE) {
            if (target == null || !target.isAlive()) {
                findTarget(client);
            }
            if (target == null) {
                return;
            }

            boolean throwing = useWindCharges.getValue()
                    && (jumpPhase == JumpPhase.ROTATING || jumpPhase == JumpPhase.USING);

            if (throwing) {
                Angle throwAngle = new Angle(yawToTarget(client), THROW_PITCH);
                AngleConnection.INSTANCE.rotateTo(
                        throwAngle,
                        3,
                        new AngleConfig(new LinearConstructor(), true, true),
                        TaskPriority.HIGH_IMPORTANCE_1,
                        this
                );
            } else {
                Angle aim = MathAngle.calculateAngle(target.getBoundingBox().getCenter());
                AngleConnection.INSTANCE.rotateTo(
                        new Angle.VecRotation(aim, aim.toVector()),
                        target,
                        1,
                        new AngleConfig(new LinearConstructor(), true, false),
                        TaskPriority.HIGH_IMPORTANCE_1,
                        this
                );
            }
            return;
        }

        if (event.getPhase() == EventPhase.POST) {
            if (target == null || !target.isAlive()) {
                return;
            }
            if (client.player.distanceTo(target) > maceRange.getFloat()) {
                return;
            }
            if (hitCooldown > 0) {
                return;
            }
            if (antiKick.getValue() && !canSafelyHit(client, target)) {
                return;
            }
            attackHandler.performAttack(target);
            hitCooldown = hitDelay.getValue().intValue();
        }
    }


    private boolean canSafelyHit(Minecraft client, LivingEntity target) {
        Vec3 eye = client.player.getEyePosition();
        AABB box = target.getBoundingBox();

        double dx = Math.max(box.minX - eye.x, Math.max(0.0D, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0.0D, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0.0D, eye.z - box.maxZ));
        double eyeDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        return eyeDist <= 3.0D;
    }

    private void findTarget(Minecraft client) {
        if (client.level == null) {
            targetFinder.releaseTarget();
            target = null;
            return;
        }
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(targetType.getValue());
        targetFinder.searchTargets(client.level.entitiesForRendering(), targetRange.getFloat(), 360.0F, true, filter::isValid);
        targetFinder.validateTarget(filter::isValid);
        target = targetFinder.getCurrentTarget();
    }

    @SubscribeEvent
    private void onInput(InputEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || target == null || !target.isAlive()) {
            return;
        }
        if (!moveTowards.getValue()) {
            return;
        }
        moveToward(event, client.player.position(), target.position(), yawToTarget(client));
    }

    private void moveToward(InputEvent event, Vec3 playerPos, Vec3 targetPos, float yaw) {
        Vec3 targetFlat = new Vec3(targetPos.x, playerPos.y, targetPos.z);
        Vec3 dir = targetFlat.subtract(playerPos);
        if (dir.lengthSqr() < 1.0E-7) {
            event.setDirectionalLow(false, false, false, false);
            return;
        }

        float moveAngle = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0F;
        float angleDiff = Mth.wrapDegrees(moveAngle - yaw);
        boolean forward = false;
        boolean back = false;
        boolean left = false;
        boolean right = false;

        if (angleDiff >= -22.5F && angleDiff < 22.5F) {
            forward = true;
        } else if (angleDiff >= 22.5F && angleDiff < 67.5F) {
            forward = true;
            right = true;
        } else if (angleDiff >= 67.5F && angleDiff < 112.5F) {
            right = true;
        } else if (angleDiff >= 112.5F && angleDiff < 157.5F) {
            back = true;
            right = true;
        } else if (angleDiff >= -67.5F && angleDiff < -22.5F) {
            forward = true;
            left = true;
        } else if (angleDiff >= -112.5F && angleDiff < -67.5F) {
            left = true;
        } else if (angleDiff >= -157.5F && angleDiff < -112.5F) {
            back = true;
            left = true;
        } else {
            back = true;
        }

        event.setDirectionalLow(forward, back, left, right);
    }
}