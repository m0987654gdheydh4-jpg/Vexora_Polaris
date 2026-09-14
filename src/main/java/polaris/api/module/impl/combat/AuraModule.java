package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.events.impl.InputEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.RotationUpdateEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.types.EventPhase;
import polaris.api.events.types.EventPriority;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.AngleConfig;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.attack.StrikeManager;
import polaris.api.module.impl.combat.aura.attack.StrikerConstructor;
import polaris.api.module.impl.combat.aura.context.AutoRegressionContext;
import polaris.api.module.impl.combat.aura.impl.LinearConstructor;
import polaris.api.module.impl.combat.aura.impl.RotateConstructor;
import polaris.api.module.impl.combat.aura.rotations.AiAngle;
import polaris.api.module.impl.combat.aura.rotations.CustomAngle;
import polaris.api.module.impl.combat.aura.rotations.FovSnapAngle;
import polaris.api.module.impl.combat.aura.rotations.FtNewAngle;
import polaris.api.module.impl.combat.aura.rotations.LegitAngle;
import polaris.api.module.impl.combat.aura.rotations.MatrixAngle;
import polaris.api.module.impl.combat.aura.rotations.SnapAngle;
import polaris.api.module.impl.combat.aura.rotations.SpookyFinalAngle;
import polaris.api.module.impl.combat.aura.target.MultiPoint;
import polaris.api.module.impl.combat.aura.target.TargetFinder;
import polaris.api.module.impl.combat.aura.util.TaskPriority;
import polaris.api.module.impl.movement.ElytraTarget;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ButtonSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.screens.ailab.AiLabScreen;
import polaris.screens.rotationbuilder.RotationBuilderScreen;
import polaris.utils.player.BaritoneMovementHelper;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;

public class AuraModule extends Module {
    private static AuraModule instance;
    public static LivingEntity target;

    private final TargetFinder targetSelector = new TargetFinder();
    private final MultiPoint pointFinder = new MultiPoint();
    private final StrikerConstructor attackPerpetrator = new StrikerConstructor();

    public final ModeSetting mode = register(new ModeSetting(
            "Mode",
            "Aura rotation mode.",
            "Matrix",


            "Matrix", "Snap", "SpookyTime", "FT-New", "Legit", "FOV", "Custom"
    ));

    public final NumberSetting timeOnTick = register(new NumberSetting("Snap Hold", "How long Snap keeps target after hit.", 50.0, 1.0, 300.0, 1.0));
    public final NumberSetting speedValue = register(new NumberSetting("Snap Speed", "Snap enter/return speed.", 50.0, 15.0, 180.0, 1.0));
    public final ModeSetting snapMode = register(new ModeSetting("Snap Style", "FOV/Snap sub-style.", "Fast", "Fast", "Smooth", "Random"));
    public final NumberSetting legitSpeed = register(new NumberSetting(
            "Legit Speed",
            "Legit yaw blend speed (metaculture DirectAimStrategy).",
            0.08, 0.02, 0.4, 0.01
    ));
    public final NumberSetting aiJitter = register(new NumberSetting("AI Jitter", "AI residual jitter strength.", 1.0, 0.0, 2.0, 0.05));
    public final BooleanSetting aiHumanMisses = register(new BooleanSetting("AI Human Misses", "Occasionally overshoot like a human.", false));
    public final BooleanSetting aiDebugLog = register(new BooleanSetting("AI Debug Log", "Log AI train/replay debug to chat and file.", false));
    public final ButtonSetting aiLabButton = register(new ButtonSetting(
            "AI Lab",
            "Open AI Lab (train / learn / compare).",
            () -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new AiLabScreen()))
    ));
    public final ButtonSetting customBuilderButton = register(new ButtonSetting(
            "Конструктор ротации",
            "Open Custom rotation builder.",
            () -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new RotationBuilderScreen()))
    ));

    public final NumberSetting funTimeFov = register(new NumberSetting("FOV", "FOV cone radius from crosshair (degrees).", 90.0, 5.0, 180.0, 1.0));
    public final NumberSetting attackRange = register(new NumberSetting("Range", "Maximum attack distance.", 3.0, 2.5, 6.0, 0.1));
    public final NumberSetting lookRange = register(new NumberSetting("Look Range", "Extra target search distance.", 1.5, 0.5, 10.0, 0.1));
    public final MultiModeSetting targetType = register(new MultiModeSetting("Targets", "Target type filter.", new String[]{"Players", "Friends", "Mobs", "Animals", "Invisible", "Armor Stands"}, "Players"));
    public final ModeSetting targetPriority = register(new ModeSetting("Priority", "Target sort priority.", "Distance", "Distance", "Health", "Armor", "FOV", "Combined"));
    public final MultiModeSetting options = register(new MultiModeSetting("Options", "Aura options.", new String[]{"Pause While Using", "Ignore Walls", "Release Shield", "Sync TPS"}, "Pause While Using"));
    public final ModeSetting moveFix = register(new ModeSetting("Move Fix", "Movement correction mode.", "Focused", "Focused", "Free", "Chase", "Target"));
    public final BooleanSetting onlyCriticals = register(new BooleanSetting("Only Crits", "Attack only with critical hits.", true));
    public final BooleanSetting smartCriticals = register(new BooleanSetting("Smart Crits", "Only force smart criticals while jumping.", false));
    private final BooleanSetting oldDelay = register(new BooleanSetting("Old Delay", "Classic random CPS delay.", false));
    private final NumberSetting minCps = register(new NumberSetting("Min CPS", "Minimum clicks per second.", 16.0, 1.0, 30.0, 1.0));
    private final NumberSetting maxCps = register(new NumberSetting("Max CPS", "Maximum clicks per second.", 18.0, 1.0, 30.0, 1.0));

    private int delayTicks = 0;

    private final MatrixAngle matrixSmooth = new MatrixAngle();
    private final SnapAngle snapSmooth = new SnapAngle();
    private final SpookyFinalAngle spookySmooth = new SpookyFinalAngle();
    private final FtNewAngle ftNewAngle = new FtNewAngle();
    private final LegitAngle legitAngle = new LegitAngle();
    private final FovSnapAngle fovSnapAngle = new FovSnapAngle();
    private final CustomAngle customAngle = new CustomAngle();

    private final AiAngle aiAngle = new AiAngle();
    private final LinearConstructor linearSmooth = new LinearConstructor();

    private LivingEntity lastTarget;
    private long activationTimeMs;
    private float tps = 20.0F;
    private float adjustTicks;
    private long timestamp;
    private int reducedHitboxAttackCounter;
    private boolean wasForwardPressed;
    private boolean wasBackPressed;
    private boolean wasLeftPressed;
    private boolean wasRightPressed;
    private boolean wasJumpPressed;
    private boolean keysOverridden;
    private boolean inventoryOpened;
    private boolean packetsHeld;




    public AuraModule() {
        super("Aura", "Automatically attacks nearby targets.", ModuleCategory.COMBAT);
        timeOnTick.visibleWhen(() -> mode.is("Snap"));
        speedValue.visibleWhen(() -> mode.is("Snap"));
        snapMode.visibleWhen(() -> mode.is("FOV") || mode.is("Snap"));
        legitSpeed.visibleWhen(() -> mode.is("Legit"));

        aiJitter.visibleWhen(() -> mode.is("AI"));
        aiHumanMisses.visibleWhen(() -> mode.is("AI"));
        aiDebugLog.visibleWhen(() -> mode.is("AI"));
        aiLabButton.visibleWhen(() -> mode.is("AI"));
        customBuilderButton.visibleWhen(() -> mode.is("Custom"));
        funTimeFov.visibleWhen(() -> mode.is("FOV"));
        smartCriticals.visibleWhen(onlyCriticals::getValue);
        minCps.visibleWhen(oldDelay::getValue);
        maxCps.visibleWhen(oldDelay::getValue);
        instance = this;
    }

    public static AuraModule getInstance() {
        return instance;
    }

    public static float activeTps() {
        return instance == null ? 20.0F : instance.tps;
    }

    @Override
    protected void onEnable() {
        activationTimeMs = System.currentTimeMillis();
        timestamp = System.nanoTime();
        tps = 20.0F;
        adjustTicks = 0.0F;
        reducedHitboxAttackCounter = 0;
        AutoRegressionContext context = AutoRegressionContext.getInstance();
        context.setCdMinecraft(1000L / 12L);
        context.hitContentClear();
        delayTicks = 0;
    }

    @Override
    protected void onDisable() {
        activationTimeMs = 0L;
        delayTicks = 0;
        timestamp = 0L;
        tps = 20.0F;
        adjustTicks = 0.0F;
        reducedHitboxAttackCounter = 0;
        targetSelector.releaseTarget();
        target = null;
        lastTarget = null;
        attackPerpetrator.getAttackHandler().resetPendingState();
        AutoRegressionContext.getInstance().hitContentClear();
        lastTarget = null;
        legitAngle.reset();
        ftNewAngle.reset();
        fovSnapAngle.reset();
        customAngle.reset();
        aiAngle.reset();
        finishRotationOnRelease(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            targetSelector.releaseTarget();
            target = null;
            releaseAimState();
            delayTicks = 0;
        } else if (target == null || !target.isAlive()) {
            delayTicks = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private void onPreTick(TickEvent.Pre event) {
        Minecraft client = event.getClient();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        attackPerpetrator.tick();
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        attackPerpetrator.onPacket(event);
        if (!isSyncWithTpsEnabled() || !event.isReceive() || !(event.getPacket() instanceof ClientboundSetTimePacket)) {
            return;
        }
        long currentTime = System.nanoTime();
        if (timestamp == 0L) {
            timestamp = currentTime;
            return;
        }
        long delay = currentTime - timestamp;
        if (delay <= 0L) {
            timestamp = currentTime;
            return;
        }
        float boundedTPS = Mth.clamp(20.0F * (1e9f / delay), 0.0F, 20.0F);
        tps = (float) limitDecimals(boundedTPS, 2);
        adjustTicks = boundedTPS - 20.0F;
        timestamp = currentTime;
    }

    @SubscribeEvent
    private void onInput(InputEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.level == null || event.getInput() == null) {
            return;
        }
        if (BaritoneMovementHelper.isBaritoneActive(client.player)) {
            return;
        }

        Input input = event.getInput();
        if (target == null || !target.isAlive()) {
            return;
        }

        boolean inWater = client.player.isInWater() || client.player.isUnderWater();
        StrikeManager attackHandler = getAttackHandlerRaw();
        attackHandler.syncShieldState();

        boolean targetFix = moveFix.is("Target");
        boolean chaseFix = moveFix.is("Chase");
        boolean focusedFixSelected = moveFix.is("Focused");

        StrikerConstructor.AttackPerpetratorConfigurable config = getConfig();
        float targetDistance = client.player.distanceTo(target);
        boolean shouldPrepareAttack = attackHandler.canAttack(config, 1)
                && targetDistance <= attackDistance()
                && !inWater;

        if (focusedFixSelected && shouldPrepareAttack) {
            event.setDirectionalLow(false, false, false, false);
        }
        if (!focusedFixSelected && shouldPrepareAttack) {
            event.setSprinting(false);
            client.player.setSprinting(false);
        }
        if (target != null
                && attackHandler.canAttack(config, 1)
                && client.player.distanceTo(target) <= attackDistance()
                && !client.player.isSwimming()) {
            event.setDirectionalLow(false, false, false, false);
        }
        if (!focusedFixSelected && !targetFix && !chaseFix) {
            Input patchedInput = event.getInput();
            event.setInput(new Input(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.shift(),
                    patchedInput.sprint()
            ));
        }
        if (focusedFixSelected && shouldPrepareAttack) {
            event.setDirectionalLow(false, false, false, false);
        }

        boolean w = client.options.keyUp.isDown();
        boolean s = client.options.keyDown.isDown();
        boolean a = client.options.keyLeft.isDown();
        boolean d = client.options.keyRight.isDown();

        if (inWater) {
            return;
        }

        if (targetFix && (
                client.options.keyUp.isDown() ||
                        client.options.keyDown.isDown() ||
                        client.options.keyLeft.isDown() ||
                        client.options.keyRight.isDown()
        )) {

            moveToward(event, client.player.position(), target.position(), AngleConnection.INSTANCE.getRotation().getYaw());
            return;
        }
        if (!chaseFix) {
            return;
        }
        if (!w && !s && !a && !d) {
            return;
        }

        Vec3 playerPos = client.player.position();
        AABB targetBox = target.getBoundingBox();
        Vec3 center = targetBox.getCenter();
        float targetYaw = target.getYRot();
        double rad = Math.toRadians(targetYaw);
        Vec3 forwardDir = new Vec3(-Math.sin(rad), 0, Math.cos(rad)).normalize();
        Vec3 rightDir = new Vec3(-forwardDir.z, 0, forwardDir.x).normalize();
        Vec3 leftDir = rightDir.scale(-1);
        double offset = target.getBbWidth() / 2.0 + 0.1;
        Vec3 offsetVec = Vec3.ZERO;

        if (w) {
            offsetVec = offsetVec.add(forwardDir);
        }
        if (s) {
            offsetVec = offsetVec.add(forwardDir.scale(-1.0));
        }
        if (a) {
            offsetVec = offsetVec.add(leftDir);
        }
        if (d) {
            offsetVec = offsetVec.add(rightDir);
        }

        Vec3 moveTargetVec = center;
        if (offsetVec.lengthSqr() > 0) {
            moveTargetVec = center.add(offsetVec.normalize().scale(offset));
        }
        moveToward(event, playerPos, moveTargetVec, AngleConnection.INSTANCE.getRotation().getYaw());
    }

    @SubscribeEvent
    private void onRotationUpdate(RotationUpdateEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || client.player == null || client.level == null) {
            return;
        }
        if (BaritoneMovementHelper.isBaritoneActive(client.player)) {
            targetSelector.releaseTarget();
            target = null;
            lastTarget = null;
            return;
        }
        if (event.getPhase() == EventPhase.PRE) {
            target = updateTarget();
            if (target != null && isTargetInsideFov(target)) {
                rotateToTarget(getConfig());
                lastTarget = target;
            } else {
                if (target != null && !isTargetInsideFov(target)) {
                    targetSelector.releaseTarget();
                    target = null;
                }
                if (lastTarget != null) {
                    lastTarget = null;
                    releaseAimState();
                }
            }
            return;
        }
        if (event.getPhase() == EventPhase.POST && target != null && isTargetInsideFov(target)) {
            performAuraAttack(getConfig());
        }
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (!isEnabled() || event.getLayer() != DrawEvent.Layer.GAME) {
            return;
        }

        if (mode.is("Legit") && target != null && target.isAlive()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {

                polaris.utils.modules.warden.rotation.FreeLookController.beginMouseCapture();
                Angle next = legitAngle.stepFrame(target);
                client.player.setYRot(next.getYaw());
                client.player.yHeadRot = next.getYaw();
            }
        }
        if (mode.is("FOV")) {
            drawFunTimeFovCircle();
        }
    }

    public float finalDistance() {
        ElytraTarget elytraTarget = ElytraTarget.getInstance();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null
                && client.player.isFallFlying()
                && elytraTarget != null
                && elytraTarget.isEnabled()) {
            return elytraTarget.getRange();
        }
        return attackRange.getFloat() + lookRange.getFloat();
    }

    public float attackDistance() {
        return attackRange.getFloat();
    }

    public StrikerConstructor.AttackPerpetratorConfigurable getConfig() {
        Minecraft client = Minecraft.getInstance();
        if (target == null || client.player == null) {
            AABB fallbackBox = client.player != null ? client.player.getBoundingBox() : new AABB(0, 0, 0, 0, 0, 0);
            return new StrikerConstructor.AttackPerpetratorConfigurable(
                    client.player,
                    MathAngle.cameraAngle(),
                    attackDistance(),
                    options.getValue(),
                    mode,
                    fallbackBox,
                    onlyCriticals.getValue()
            );
        }

        Tuple<Vec3, AABB> point = pointFinder.computeVector(target, attackDistance(), AngleConnection.INSTANCE.getRotation(), getSmoothMode().randomValue(), options.isSelected("Ignore Walls"));
        Vec3 computedPoint = point.getA();
        AABB box = adjustAttackBox(point.getB());

        if (client.player.isFallFlying() && target.isFallFlying()) {
            Vec3 targetVelocity = target.getDeltaMovement();
            double targetSpeed = targetVelocity.horizontalDistance();
            float leadTicks = 0.0F;
            ElytraTarget elytraTarget = ElytraTarget.getInstance();
            if (ElytraTarget.shouldElytraTarget && elytraTarget != null && elytraTarget.isEnabled()) {
                leadTicks = elytraTarget.getForward();
            }
            if (targetSpeed > 0.35D) {
                Vec3 predictedPos = target.position().add(targetVelocity.scale(leadTicks));
                computedPoint = predictedPos.add(0.0D, target.getBbHeight() / 2.0D, 0.0D);
                box = adjustAttackBox(new AABB(
                        predictedPos.x - target.getBbWidth() / 2.0D,
                        predictedPos.y,
                        predictedPos.z - target.getBbWidth() / 2.0D,
                        predictedPos.x + target.getBbWidth() / 2.0D,
                        predictedPos.y + target.getBbHeight(),
                        predictedPos.z + target.getBbWidth() / 2.0D
                ));
            }
        }

        Angle angle = MathAngle.fromVec3d(computedPoint.subtract(client.player.getEyePosition()));

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target, angle, attackDistance(), options.getValue(), mode, box,
                onlyCriticals.getValue(), options.isSelected("Sync TPS"), false);
    }

    public AngleConfig getRotationConfig() {
        return new AngleConfig(getSmoothMode(), true, moveFix.is("Free"));
    }

    public RotateConstructor getSmoothMode() {
        Minecraft client = Minecraft.getInstance();
        ElytraTarget elytraTarget = ElytraTarget.getInstance();
        if (client.player != null && client.player.isFallFlying() && elytraTarget != null && elytraTarget.isEnabled()) {
            return linearSmooth;
        }


        return switch (mode.getValue()) {
            case "Matrix"         -> matrixSmooth;
            case "Snap"           -> snapSmooth;
            case "SpookyTime"     -> spookySmooth;
            case "FT-New"         -> ftNewAngle;
            case "Legit"          -> legitAngle;
            case "FOV"            -> fovSnapAngle;
            case "Custom"         -> customAngle;


            default               -> linearSmooth;
        };
    }

    private void applyTargetOrChaseCorrection(InputEvent event, Minecraft client) {
        LivingEntity currentTarget = target;
        if (currentTarget == null || !currentTarget.isAlive() || client.player.isInWater() || client.player.isUnderWater()) {
            return;
        }

        if (!moveFix.is("Target") && !moveFix.is("Chase") && !moveFix.is("Focused")) {
            return;
        }

        StrikeManager attackHandler = getAttackHandlerRaw();
        if (attackHandler == null) {
            return;
        }
        boolean attackReady = attackHandler.canAttack(getConfig(), 1) && client.player.distanceTo(currentTarget) <= attackDistance();
        if (moveFix.is("Focused")) {
            if (attackReady) {
                event.setDirectionalLow(false, false, false, false);
            }
            return;
        }

        if (moveFix.is("Target")) {
            moveToward(event, client.player.position(), currentTarget.position(), AngleConnection.INSTANCE.getMoveRotation().getYaw());
            return;
        }

        var input = event.getInput();
        if (!input.forward() && !input.backward() && !input.left() && !input.right()) {
            return;
        }

        Vec3 playerPos = client.player.position();
        Vec3 center = currentTarget.getBoundingBox().getCenter();
        float targetYaw = currentTarget.getYRot();
        double rad = Math.toRadians(targetYaw);
        Vec3 forwardDir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
        Vec3 rightDir = new Vec3(-forwardDir.z, 0.0, forwardDir.x).normalize();
        Vec3 offsetVec = Vec3.ZERO;

        if (input.forward()) {
            offsetVec = offsetVec.add(forwardDir);
        }
        if (input.backward()) {
            offsetVec = offsetVec.add(forwardDir.scale(-1.0));
        }
        if (input.left()) {
            offsetVec = offsetVec.add(rightDir.scale(-1.0));
        }
        if (input.right()) {
            offsetVec = offsetVec.add(rightDir);
        }

        double offset = currentTarget.getBbWidth() / 2.0 + 0.1;
        Vec3 moveTarget = offsetVec.lengthSqr() > 0.0 ? center.add(offsetVec.normalize().scale(offset)) : center;
        moveToward(event, playerPos, moveTarget, AngleConnection.INSTANCE.getMoveRotation().getYaw());
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

    public boolean shouldCancelInteractItem(InteractionHand hand) {
        if (shouldCancelUseInteractions()) {
            return true;
        }
        StrikeManager attackHandler = getAttackHandler();
        return attackHandler != null && attackHandler.shouldCancelShieldUse(hand);
    }

    public boolean shouldSuppressAirUsePacket(InteractionHand hand) {
        return shouldCancelInteractItem(hand);
    }

    public boolean shouldCancelInteractBlock() {
        if (shouldCancelUseInteractions()) {
            return true;
        }
        StrikeManager attackHandler = getAttackHandler();
        return attackHandler != null && attackHandler.shouldCancelUseItemOn();
    }

    public boolean shouldSuppressBlockUsePacket() {
        return shouldCancelInteractBlock();
    }

    public boolean shouldCancelEntityInteraction() {
        if (shouldCancelUseInteractions()) {
            return true;
        }
        StrikeManager attackHandler = getAttackHandler();
        return attackHandler != null && attackHandler.shouldCancelEntityInteraction();
    }

    public boolean shouldSuppressEntityUsePacket() {
        return shouldCancelEntityInteraction();
    }

    public boolean shouldBlockUseInteractions() {
        return false;
    }

    public boolean shouldPauseForUse() {
        return false;
    }

    public boolean shouldCancelUseInteractions() {
        return false;
    }

    public boolean isSyncWithTpsEnabled() {
        return options.isSelected("Sync TPS");
    }

    public StrikeManager getAttackHandler() {
        return isEnabled() ? attackPerpetrator.getAttackHandler() : null;
    }

    public StrikeManager getAttackHandlerRaw() {
        return attackPerpetrator.getAttackHandler();
    }

    public boolean hasQueuedAttack() {
        return false;
    }

    public void flushQueuedAttack() {

    }

    public ModeSetting getMode() {
        return mode;
    }

    public NumberSetting getTimeOnTick() {
        return timeOnTick;
    }

    public NumberSetting getSpeedValue() {
        return speedValue;
    }

    public NumberSetting getLegitSpeed() {
        return legitSpeed;
    }

    public ModeSetting getSnapMode() {
        return snapMode;
    }

    public NumberSetting getAiJitter() {
        return aiJitter;
    }

    public BooleanSetting getAiHumanMisses() {
        return aiHumanMisses;
    }

    public BooleanSetting getAiDebugLog() {
        return aiDebugLog;
    }


    public NumberSetting getAimAssistSpeed() {
        return null;
    }

    public NumberSetting getAimAssistSmoothness() {
        return null;
    }

    public NumberSetting getRange() {
        return attackRange;
    }

    public NumberSetting getLookRange() {
        return lookRange;
    }

    public NumberSetting getFunTimeFov() {
        return funTimeFov;
    }


    public boolean isFunTimeFovMode() {
        return mode.is("FOV");
    }

    public boolean isTargetInsideAuraFov(LivingEntity entity) {
        return isTargetInsideFov(entity);
    }

    public MultiModeSetting getTargets() {
        return targetType;
    }

    public ModeSetting getTargetPriority() {
        return targetPriority;
    }

    public MultiModeSetting getOptions() {
        return options;
    }

    public ModeSetting getMoveFix() {
        return moveFix;
    }

    public BooleanSetting getOnlyCrits() {
        return onlyCriticals;
    }

    public BooleanSetting getOnlyCriticals() {
        return onlyCriticals;
    }

    public BooleanSetting getSmartCrits() {
        return smartCriticals;
    }

    public BooleanSetting getSmartCriticals() {
        return smartCriticals;
    }

    public boolean isLegitMode() {
        return mode.is("Legit");
    }

    public boolean isLegitPitchCorrecting() {

        return false;
    }

    public long getActivationTimeMs() {
        return activationTimeMs;
    }

    public float getTps() {
        return tps;
    }

    public float getAdjustTicks() {
        return adjustTicks;
    }
    public BooleanSetting getOldDelay() {
        return oldDelay;
    }

    public NumberSetting getMinCps() {
        return minCps;
    }

    public NumberSetting getMaxCps() {
        return maxCps;
    }

    private LivingEntity updateTarget() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            targetSelector.releaseTarget();
            return null;
        }
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(targetType.getValue());

        float maxFov = isFunTimeFovMode() ? funTimeFov.getFloat() : 360.0F;
        targetSelector.searchTargets(client.level.entitiesForRendering(), finalDistance(), maxFov, options.isSelected("Ignore Walls"), filter::isValid);
        targetSelector.validateTarget(filter::isValid);
        LivingEntity found = targetSelector.getCurrentTarget();
        if (found != null && isFunTimeFovMode() && !isTargetInsideFov(found)) {
            targetSelector.releaseTarget();
            return null;
        }
        return targetSelector.getCurrentTarget();
    }


    public boolean isTargetInsideFov(LivingEntity entity) {
        if (!isFunTimeFovMode() || entity == null) {
            return true;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        Angle toTarget = MathAngle.calculateAngle(entity.getBoundingBox().getCenter());
        double angleDiff = AngleConnection.computeRotationDifference(MathAngle.cameraAngle(), toTarget);

        return angleDiff <= funTimeFov.getValue();
    }

    private void drawFunTimeFovCircle() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null || client.getWindow() == null) {
            return;
        }

        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        float radius = calculateFovCircleRadius(width, height);
        if (radius < 1.0F) {
            return;
        }

        float diameter = radius * 2.0F;
        boolean hasTarget = target != null && target.isAlive() && isTargetInsideFov(target);
        int color = hasTarget ? ColorUtil.rgba(127, 242, 255, 165) : ColorUtil.rgba(255, 255, 255, 95);
        Render2D.outline360(
                width * 0.5F - radius,
                height * 0.5F - radius,
                diameter,
                diameter,
                radius,
                1.2F,
                color,
                Render2D.outline360Range(0.0F, 360.0F, color)
        );
    }


    private float calculateFovCircleRadius(int width, int height) {
        float maxRadius = Math.max(0.0F, Math.min(width, height) * 0.5F - 2.0F);
        if (maxRadius <= 0.0F) {
            return 0.0F;
        }

        Minecraft client = Minecraft.getInstance();
        double aimHalf = Mth.clamp(funTimeFov.getValue(), 0.1D, 89.9D);
        double gameFov = Mth.clamp(client.options.fov().get(), 30.0D, 170.0D);
        double gameHalf = Mth.clamp(gameFov * 0.5D, 1.0D, 89.9D);
        if (aimHalf >= gameHalf) {
            return maxRadius;
        }

        double radius = Math.tan(Math.toRadians(aimHalf)) / Math.tan(Math.toRadians(gameHalf)) * (Math.min(width, height) * 0.5D);
        return (float) Mth.clamp(radius, 0.0D, maxRadius);
    }

    private void finishRotationOnRelease(Minecraft client) {
        releaseAimState();
    }


    private void releaseAimState() {


        polaris.utils.modules.warden.rotation.RotationController.hardReset();
        polaris.api.module.impl.combat.aura.ai.AiRotationTrainer.clearSilent();
        AngleConnection.INSTANCE.restoreVanillaLook();
        legitAngle.reset();
        fovSnapAngle.reset();
    }

    private void rotateToTarget(StrikerConstructor.AttackPerpetratorConfigurable config) {

        if (isLegitMode()) {
            return;
        }
        AngleConnection controller = AngleConnection.INSTANCE;
        Angle.VecRotation rotation = new Angle.VecRotation(config.getAngle(), config.getAngle().toVector());
        controller.rotateTo(rotation, target, 1, getRotationConfig(), TaskPriority.HIGH_IMPORTANCE_1, this);
    }

    private void performAuraAttack(StrikerConstructor.AttackPerpetratorConfigurable config) {
        if (!isTargetInsideFov(target)) {
            return;
        }

        // ===== Old Delay (random CPS) =====
        if (oldDelay.getValue()) {
            if (delayTicks > 0) {
                delayTicks--;
                return;
            }

            int min = Math.max(1, minCps.getValue().intValue());
            int max = Math.max(1, maxCps.getValue().intValue());
            if (max < min) {
                int t = min; min = max; max = t;
            }

            Minecraft client = Minecraft.getInstance();
            float randomCps = min + (max - min) * client.player.getRandom().nextFloat();
            delayTicks = Math.max(0, (int) (20.0F / randomCps) - 1);

            // Прямая атака, минуя внутренний кулдаун strike manager'а
            directAttack(config);
            return;
        }
        // =================================

        StrikeManager attackHandler = attackPerpetrator.getAttackHandler();
        int before = attackHandler.getCount();
        attackPerpetrator.performAttack(config);
        int performed = attackHandler.getCount() - before;
        if (performed > 0) {
            reducedHitboxAttackCounter += performed;
        }
    }

    /**
     * Прямая атака без проверки внутреннего кулдауна StrikeManager.
     * Используется когда включён Old Delay для точного контроля CPS.
     */
    private void directAttack(StrikerConstructor.AttackPerpetratorConfigurable config) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || target == null || !target.isAlive()) return;

        boolean critical = false;

        // Проверка на криты (если включена настройка)
        if (onlyCriticals.getValue()) {
            if (!client.player.onGround() || client.player.isFallFlying()) {
                // Не на земле или летит - крит возможен
                if (smartCriticals.getValue()) {
                    // Smart crits: только при падении
                    critical = client.player.getDeltaMovement().y < 0.0D
                            && !client.player.onGround()
                            && !client.player.isPassenger();
                } else {
                    // Обычные криты: не на земле
                    critical = !client.player.onGround();
                }
            }

            if (!critical && onlyCriticals.getValue()) {
                // Не можем сделать крит, пропускаем удар
                return;
            }
        }

        // Свинг руки
        client.player.swing(InteractionHand.MAIN_HAND);

        // Прямая атака через Minecraft API
        client.gameMode.attack(client.player, target);

        // Обновляем счётчик для reduced hitbox
        reducedHitboxAttackCounter++;
    }

    private AABB adjustAttackBox(AABB box) {
        if (box == null || !shouldUseReducedHitbox()) {
            return box;
        }
        Vec3 center = box.getCenter();
        double halfX = Math.max(box.getXsize() * 0.4, 0.01);
        double halfZ = Math.max(box.getZsize() * 0.4, 0.01);
        return new AABB(center.x - halfX, box.minY, center.z - halfZ, center.x + halfX, box.maxY, center.z + halfZ);
    }

    private boolean shouldUseReducedHitbox() {
        return reducedHitboxAttackCounter % 4 < 2;
    }

    private double limitDecimals(double value, int decimalPlaces) {
        return Math.round(value * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
    }
}

