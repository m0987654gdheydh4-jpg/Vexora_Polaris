package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.repository.friend.FriendUtils;


public final class AimAssistV2 extends Module {
    private static AimAssistV2 instance;

    private final NumberSetting distance = register(new NumberSetting(
            "Distance", "Max target distance.", 4.0, 0.5, 6.0, 0.1));
    private final BooleanSetting changeX = register(new BooleanSetting(
            "Change X", "Assist horizontal (yaw).", true));
    private final NumberSetting accelX = register(new NumberSetting(
            "Accel X", "Yaw acceleration when moving toward target.", 1.75, 0.1, 5.0, 0.05));
    private final NumberSetting decelX = register(new NumberSetting(
            "Decel X", "Yaw deceleration when moving away.", 0.4, 0.1, 5.0, 0.05));
    private final NumberSetting rangeX = register(new NumberSetting(
            "Diff Range X", "Min yaw error (°) before assist engages.", 20.0, 0.0, 100.0, 5.0));

    private final BooleanSetting changeY = register(new BooleanSetting(
            "Change Y", "Assist vertical (pitch).", false));
    private final NumberSetting accelY = register(new NumberSetting(
            "Accel Y", "Pitch acceleration toward target.", 1.25, 0.1, 5.0, 0.05));
    private final NumberSetting decelY = register(new NumberSetting(
            "Decel Y", "Pitch deceleration away from target.", 0.75, 0.1, 5.0, 0.05));
    private final NumberSetting rangeY = register(new NumberSetting(
            "Diff Range Y", "Min pitch error (°) before assist engages.", 20.0, 0.0, 100.0, 5.0));

    private final MultiModeSetting targets = register(new MultiModeSetting(
            "Targets", "Who to aim at.",
            new String[]{"Players", "Hostile", "Peaceful", "Naked"},
            "Players"));
    private final BooleanSetting safeTarget = register(new BooleanSetting(
            "Safe Target", "Ignore friends / team.", true));
    private final BooleanSetting moveFix = register(new BooleanSetting(
            "Move Fix", "Correct movement to look direction while assisting.", true));

    private LivingEntity currentTarget;
    private double lastYawStep;
    private double lastPitchStep;
    private long lastNs;

    public AimAssistV2() {
        super("AimAssistV2", "Zenith multiply-style aim assist.", ModuleCategory.COMBAT);
        instance = this;
        accelX.visibleWhen(changeX::getValue);
        decelX.visibleWhen(changeX::getValue);
        rangeX.visibleWhen(changeX::getValue);
        accelY.visibleWhen(changeY::getValue);
        decelY.visibleWhen(changeY::getValue);
        rangeY.visibleWhen(changeY::getValue);
    }

    public static AimAssistV2 getInstance() {
        return instance;
    }

    public LivingEntity getTarget() {
        return isEnabled() ? currentTarget : null;
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
        lastYawStep = 0;
        lastPitchStep = 0;
        lastNs = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        update(client);
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (event.getLayer() == DrawEvent.Layer.GAME) {
            update(Minecraft.getInstance());
        }
    }

    private void update(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            currentTarget = null;
            return;
        }
        if (client.screen != null) {
            return;
        }

        long now = System.nanoTime();
        double frame = lastNs > 0L ? (now - lastNs) / 16_666_666.7 : 1.0;
        frame = Mth.clamp(frame, 0.25, 3.0);
        lastNs = now;

        currentTarget = findTarget(client);
        if (currentTarget == null) {
            lastYawStep *= 0.5;
            lastPitchStep *= 0.5;
            return;
        }

        Vec3 aimPoint = currentTarget.getBoundingBox().getCenter();
        Vec3 eye = client.player.getEyePosition();
        double[] desired = rotationsTo(eye, aimPoint);
        float curYaw = client.player.getYRot();
        float curPitch = client.player.getXRot();
        float dyaw = Mth.wrapDegrees((float) desired[0] - curYaw);
        float dpitch = (float) desired[1] - curPitch;

        
        if (changeX.getValue() && Math.abs(dyaw) > rangeX.getFloat()) {
            double factor = multiplyFactor(lastYawStep, dyaw, accelX.getFloat(),
                    Math.abs(dyaw) < 40.0f ? decelX.getFloat() : 1.0f);
            
            double step = dyaw * 0.08 * factor * frame;
            step = clampStep(step, lastYawStep, 2.5 + accelX.getFloat());
            step = clampToRemaining(step, dyaw);
            lastYawStep = step;
            client.player.setYRot(curYaw + (float) step);
        } else {
            lastYawStep *= 0.4;
        }

        if (changeY.getValue() && Math.abs(dpitch) > rangeY.getFloat()) {
            double factor = multiplyFactor(lastPitchStep, dpitch, accelY.getFloat(), decelY.getFloat());
            double step = dpitch * 0.08 * factor * frame;
            step = clampStep(step, lastPitchStep, 2.0 + accelY.getFloat());
            step = clampToRemaining(step, dpitch);
            lastPitchStep = step;
            client.player.setXRot(Mth.clamp(curPitch + (float) step, -90.0f, 90.0f));
        } else {
            lastPitchStep *= 0.4;
        }

        
        if (moveFix.getValue()) {
            client.player.yHeadRot = client.player.getYRot();
        }
    }

    
    private static double multiplyFactor(double currentStep, float neededDelta, float accel, float decel) {
        boolean sameDir = Math.signum(currentStep) == Math.signum(neededDelta) || Math.abs(currentStep) < 1.0e-4;
        return sameDir ? accel : decel;
    }

    private static double clampStep(double step, double previous, double maxDeltaChange) {
        double diff = step - previous;
        if (Math.abs(diff) > maxDeltaChange) {
            step = previous + Math.signum(diff) * maxDeltaChange;
        }
        return step;
    }

    private static double clampToRemaining(double step, float remaining) {
        if (Math.signum(step) != Math.signum(remaining) && Math.abs(remaining) > 1.0e-3) {
            return 0;
        }
        if (Math.abs(step) > Math.abs(remaining)) {
            return remaining;
        }
        return step;
    }

    private LivingEntity findTarget(Minecraft client) {
        double maxDist = distance.getValue();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = client.player.getEyePosition();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValid(client, living)) {
                continue;
            }
            double dist = client.player.distanceTo(living);
            if (dist > maxDist || dist < 0.4) {
                continue;
            }
            
            double[] rot = rotationsTo(eye, living.getBoundingBox().getCenter());
            float yawDiff = Math.abs(Mth.wrapDegrees((float) rot[0] - client.player.getYRot()));
            double score = dist + yawDiff * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean isValid(Minecraft client, LivingEntity e) {
        if (e == client.player || !e.isAlive()) {
            return false;
        }
        if (e instanceof Player player) {
            if (!targets.isSelected("Players") && !targets.isSelected("Naked")) {
                return false;
            }
            if (safeTarget.getValue() && FriendUtils.isFriend(player)) {
                return false;
            }
            boolean naked = !player.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    && !player.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    && !player.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
                    && !player.hasItemInSlot(net.minecraft.world.entity.EquipmentSlot.FEET);
            if (targets.isSelected("Naked") && !targets.isSelected("Players")) {
                return naked;
            }
            return targets.isSelected("Players") || (targets.isSelected("Naked") && naked);
        }
        if (e instanceof Animal) {
            return targets.isSelected("Peaceful");
        }
        if (e instanceof Mob) {
            return targets.isSelected("Hostile");
        }
        return false;
    }

    private static double[] rotationsTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, dist));
        return new double[]{yaw, pitch};
    }
}
