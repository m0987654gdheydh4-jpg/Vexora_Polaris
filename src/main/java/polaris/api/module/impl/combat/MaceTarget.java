package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import polaris.api.module.impl.combat.aura.util.StopWatch;
import polaris.api.module.impl.combat.macetarget.armor.ArmorSwapHandler;
import polaris.api.module.impl.combat.macetarget.armor.FireworkHandler;
import polaris.api.module.impl.combat.macetarget.attack.AttackHandler;
import polaris.api.module.impl.combat.macetarget.flight.FlightController;
import polaris.api.module.impl.combat.macetarget.prediction.TargetPredictor;
import polaris.api.module.impl.combat.macetarget.stage.StageHandler;
import polaris.api.module.impl.combat.macetarget.state.MaceState.Stage;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.inventory.lookup.InventoryUtils;
import polaris.utils.inventory.swap.SwapSettings;

public class MaceTarget extends Module {
    private static MaceTarget instance;

    private final ModeSetting serverMode = register(new ModeSetting("Server", "Server mode.", "Standard", "Standard", "ReallyWorld"));
    private final NumberSetting height = register(new NumberSetting("Height", "Flight height above target.", 30.0, 20.0, 60.0, 1.0));
    private final MultiModeSetting targetType = register(new MultiModeSetting("Targets", "Target type filter.", new String[]{"Players", "Mobs", "Animals"}, "Players"));
    private final BooleanSetting autoEquipChest = register(new BooleanSetting("Auto Chestplate", "Equip chestplate on disable when possible.", true));
    private final BooleanSetting predictMovement = register(new BooleanSetting("Predict", "Predict target movement.", true));
    private final TargetPredictor predictor = new TargetPredictor();
    private final FlightController flightController = new FlightController(predictor);
    private final AttackHandler attackHandler = new AttackHandler();
    private final ArmorSwapHandler armorSwapHandler = new ArmorSwapHandler(this::buildSettings);
    private final FireworkHandler fireworkHandler = new FireworkHandler(this::buildSettings);
    private final StopWatch fireworkTimer = new StopWatch();
    private final StageHandler stageHandler = new StageHandler(armorSwapHandler, fireworkHandler, attackHandler, fireworkTimer);
    private final TargetFinder targetFinder = new TargetFinder();
    private LivingEntity target;

    public MaceTarget() {
        super("Mace Target", "Mace combat target helper.", ModuleCategory.COMBAT);
        instance = this;
    }

    public static MaceTarget getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        stageHandler.reset();
        target = null;
        attackHandler.reset();
        armorSwapHandler.reset();
        fireworkHandler.reset();
        predictor.reset();
        fireworkTimer.reset();
    }

    @Override
    protected void onDisable() {
        if (autoEquipChest.getValue() && Minecraft.getInstance().player != null) {
            equipChestplateOnDisable();
        }
        armorSwapHandler.forceRestore();
        fireworkHandler.forceRestore();
        target = null;
        targetFinder.releaseTarget();
        armorSwapHandler.reset();
        fireworkHandler.reset();
        attackHandler.reset();
        predictor.reset();
        stageHandler.reset();
        AngleConnection.INSTANCE.startReturning();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            resetAllStates();
            return;
        }

        armorSwapHandler.processLoop();
        fireworkHandler.processLoop();
        if (armorSwapHandler.isActive() || fireworkHandler.isActive()) {
            return;
        }

        if (target == null || !target.isAlive()) {
            return;
        }

        processStage();
    }

    @SubscribeEvent
    private void onRotationUpdate(RotationUpdateEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        if (event.getPhase() == EventPhase.PRE) {
            updateHandlers();

            if (target == null || !target.isAlive()) {
                findTarget();
            }

            if (target == null) {
                return;
            }

            predictor.update(target);

            // ===== Мгновенная атака по радиусу =====
            // Цель в радиусе досягаемости хотя бы 1 тик — бьём в эту же тику,
            // не дожидаясь стадии ATTACKING стейт-машины.
            if (!armorSwapHandler.isActive() && !fireworkHandler.isActive() && isWithinReach(target)) {
                rotateTo(MathAngle.calculateAngle(target.getBoundingBox().getCenter()));
                attackHandler.setPendingAttack(true);
                return;
            }
            // =======================================

            Stage currentStage = stageHandler.getStage();
            if (currentStage == Stage.FLYING_UP) {
                if (InventoryUtils.hasElytra() && client.player.isFallFlying()) {
                    rotateTo(flightController.calculateAngle(target, currentStage));
                }
            } else if (currentStage == Stage.TARGETTING || currentStage == Stage.ATTACKING) {
                rotateTo(flightController.calculateAngle(target, currentStage));
            }
            return;
        }

        if (event.getPhase() == EventPhase.POST) {
            // Страховка: если PRE не успел поставить pending (например, свап закончился
            // посреди тики), а цель всё ещё в радиусе — ставим pending здесь.
            if (target != null && target.isAlive()
                    && !armorSwapHandler.isActive() && !fireworkHandler.isActive()
                    && isWithinReach(target)) {
                attackHandler.setPendingAttack(true);
            }
            handlePostRotation();
        }
    }

    @SubscribeEvent
    private void onInput(InputEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        if (armorSwapHandler.getMovement().isBlocked() || fireworkHandler.getMovement().isBlocked()) {
            event.setDirectionalLow(false, false, false, false);
            event.setJumping(false);
            event.setSprinting(false);
            client.player.setSprinting(false);
        }
        if (target != null && InventoryUtils.hasElytra() && stageHandler.getStage() == Stage.FLYING_UP) {
            if (client.player.onGround()) {
                event.setJumping(true);
            } else if (!client.player.isFallFlying() && !client.player.getAbilities().flying) {
                event.setJumping(client.player.tickCount % 2 == 0);
            }
        }
    }

    /**
     * Проверка досягаемости как в ванили: расстояние от глаза игрока
     * до ближайшей точки хитбокса цели <= entity interaction range.
     */
    private boolean isWithinReach(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity == null) {
            return false;
        }
        double reach = client.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) + 0.25D;
        Vec3 eye = client.player.getEyePosition();
        AABB box = entity.getBoundingBox();

        double dx = Math.max(box.minX - eye.x, Math.max(0.0D, eye.x - box.maxX));
        double dy = Math.max(box.minY - eye.y, Math.max(0.0D, eye.y - box.maxY));
        double dz = Math.max(box.minZ - eye.z, Math.max(0.0D, eye.z - box.maxZ));

        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= reach;
    }

    private SwapSettings buildSettings() {
        return SwapSettings.instant();
    }

    private void updateHandlers() {
        stageHandler.setSilentMode(true);
        stageHandler.setReallyWorldMode(serverMode.is("ReallyWorld"));
        stageHandler.setHeight(height.getFloat());
        flightController.setPredictionEnabled(predictMovement.getValue());
        flightController.setHeight(height.getFloat());
    }

    private void findTarget() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            targetFinder.releaseTarget();
            target = null;
            return;
        }
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(targetType.getValue());
        targetFinder.searchTargets(client.level.entitiesForRendering(), 128.0F, 360.0F, true);
        targetFinder.validateTarget(filter::isValid);
        target = targetFinder.getCurrentTarget();
    }

    private void processStage() {
        boolean hasElytra = InventoryUtils.hasElytra();
        switch (stageHandler.getStage()) {
            case PREPARE -> stageHandler.handlePrepare(hasElytra);
            case FLYING_UP -> stageHandler.handleFlyingUp(target, hasElytra);
            case TARGETTING -> stageHandler.handleTargetting(target);
            case ATTACKING -> stageHandler.handleAttacking(target, hasElytra);
        }
    }

    private void handlePostRotation() {
        if (!attackHandler.isPendingAttack()) {
            return;
        }
        attackHandler.performAttack(target);
        attackHandler.setPendingAttack(false);
        if (attackHandler.isShouldDisableAfterAttack()) {
            attackHandler.setShouldDisableAfterAttack(false);
            setEnabled(false);
        }
    }

    private void rotateTo(Angle angle) {
        AngleConnection.INSTANCE.rotateTo(
                new Angle.VecRotation(angle, angle.toVector()),
                target,
                1,
                new AngleConfig(new LinearConstructor(), true, false),
                TaskPriority.HIGH_IMPORTANCE_1,
                this
        );
    }

    private void equipChestplateOnDisable() {
        if (InventoryUtils.hasElytra()) {
            int slot = InventoryUtils.findChestArmorSlot();
            if (slot != -1) {
                InventoryUtils.swap(InventoryUtils.wrapSlot(slot), 6);
                InventoryUtils.closeScreen();
            }
        }
    }

    private void resetAllStates() {
        armorSwapHandler.reset();
        fireworkHandler.reset();
        attackHandler.reset();
        predictor.reset();
        target = null;
        targetFinder.releaseTarget();
        stageHandler.reset();
    }
}