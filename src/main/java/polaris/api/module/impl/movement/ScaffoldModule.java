package polaris.api.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.InputEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.RotationUpdateEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.events.types.EventPhase;
import polaris.api.events.types.EventPriority;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.combat.aura.AngleConfig;
import polaris.api.module.impl.combat.aura.AngleConnection;
import polaris.api.module.impl.combat.aura.MathAngle;
import polaris.api.module.impl.combat.aura.impl.LinearConstructor;
import polaris.api.module.impl.combat.aura.util.TaskPriority;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.Render3D;
import polaris.utils.render.color.ColorUtil;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scaffold, портированный с LiquidBounce под Polaris API.
 * Техники: Normal / GodBridge / Breezily / Expand.
 * Фичи: Eagle, Telly, Down, Ceiling, Stabilize, Ledge, Strafe, JumpStrafe,
 *       SpeedLimiter, Acceleration, SprintControl, Blink, AutoBlock, Prediction.
 * Tower: None / Motion / Pulldown / Karhu / Vulcan / Hypixel.
 */
public final class ScaffoldModule extends Module {
    private static ScaffoldModule instance;

    // ============================ НАСТРОЙКИ ============================
    private final ModeSetting technique = register(new ModeSetting(
            "Technique", "Bridge technique.", "Normal",
            "Normal", "GodBridge", "Breezily", "Expand"));
    // Триггер Pulldown из конфига (0.16 вместо хардкода 0.1)
    private final NumberSetting pulldownTrigger = register(new NumberSetting(
            "Pulldown Trigger", "Velocity threshold for pulldown tower.", 0.16, 0.0, 0.2, 0.01));
// в towerTick: case "Pulldown" -> if (!mc.player.onGround() && v.y < pulldownTrigger.getValue() && isBlockBelow()) ...

    // SimulatePlacementAttempts (спам ПКМ 11–18 CPS во время движения)
    private final BooleanSetting simAttempts = register(new BooleanSetting("Simulate Attempts", "Spam use-key while moving.", true));
    private final NumberSetting simCps = register(new NumberSetting("Sim CPS", "Spam clicks per second.", 14.0, 1.0, 100.0, 1.0));
// в onPreTick: if (simAttempts.getValue() && isMoving() && mc.player.tickCount % Math.max(1, (int)(20/simCps.getValue())) == 0
//         && mc.hitResult instanceof BlockHitResult bhr) { mc.gameMode.useItemOn(mc.player, hand, bhr); }

    private final NumberSetting delay = register(new NumberSetting("Delay", "Ticks between placements.", 0.0, 0.0, 40.0, 1.0));
    private final NumberSetting minDist = register(new NumberSetting("Min Dist", "Min distance to block edge.", 0.0, 0.0, 0.25, 0.01));
    private final NumberSetting expandLength = register(new NumberSetting("Expand Length", "Expand technique reach.", 4.0, 1.0, 10.0, 1.0));
    private final ModeSetting sameY = register(new ModeSetting("Same Y", "Lock placement height.", "Off", "Off", "On", "Falling", "Hypixel"));
    private final ModeSetting tower = register(new ModeSetting("Tower", "Vertical tower bypass.", "None",
            "None", "Motion", "Pulldown", "Karhu", "Vulcan", "Hypixel"));
    private final ModeSetting swing = register(new ModeSetting("Swing", "Arm swing mode.", "Do Not Hide",
            "Do Not Hide", "Hide", "Only Hand", "Only Packet"));

    // AutoBlock
    private final BooleanSetting autoBlock = register(new BooleanSetting("Auto Block", "Silently select blocks.", true));
    private final BooleanSetting alwaysHold = register(new BooleanSetting("Always Hold", "Always keep block in hand.", false));
    private final NumberSetting slotResetDelay = register(new NumberSetting("Slot Reset Delay", "Ticks before slot restore.", 5.0, 0.0, 40.0, 1.0));
    private final NumberSetting doNotUseBelow = register(new NumberSetting("Do Not Use Below", "Reserve block count.", 1.0, 0.0, 64.0, 1.0));

    // Фичи
    private final BooleanSetting prediction = register(new BooleanSetting("Prediction", "Predict placement position.", true));
    private final BooleanSetting ledge = register(new BooleanSetting("Ledge", "Anti-fall off edges.", true));
    private final BooleanSetting eagle = register(new BooleanSetting("Eagle", "Sneak at block edge.", false));
    private final ModeSetting eagleMode = register(new ModeSetting("Eagle Mode", "Input or packet sneak.", "Input", "Input", "Packet"));
    private final NumberSetting eagleEdge = register(new NumberSetting("Eagle Edge", "Edge distance for eagle.", 0.01, 0.01, 1.3, 0.01));
    private final NumberSetting blocksToEagle = register(new NumberSetting("Blocks To Eagle", "Blocks placed without sneak.", 0.0, 0.0, 10.0, 1.0));
    private final BooleanSetting telly = register(new BooleanSetting("Telly", "Jump-bridge technique.", false));
    private final ModeSetting tellyMode = register(new ModeSetting("Telly Mode", "Jump reset mode.", "Reset", "Reset", "Reverse"));
    private final NumberSetting tellyJump = register(new NumberSetting("Telly Jump Ticks", "Ground ticks before jump.", 0.0, 0.0, 10.0, 1.0));
    private final BooleanSetting down = register(new BooleanSetting("Down", "Scaffold downwards on shift.", false));
    private final BooleanSetting ceiling = register(new BooleanSetting("Ceiling", "Scaffold above head.", false));
    private final BooleanSetting stabilize = register(new BooleanSetting("Stabilize", "Hold optimal movement line.", true));
    private final BooleanSetting headHitter = register(new BooleanSetting("Head Hitter", "Jump under low ceiling.", false));

    // Движение
    private final BooleanSetting strafe = register(new BooleanSetting("Strafe", "Force strafe speed.", false));
    private final NumberSetting strafeSpeed = register(new NumberSetting("Strafe Speed", "Strafe velocity.", 0.247, 0.0, 5.0, 0.001));
    private final BooleanSetting strafeHypixel = register(new BooleanSetting("Strafe Hypixel", "Hypixel strafe values.", false));
    private final BooleanSetting jumpStrafe = register(new BooleanSetting("Strafe On Jump", "Strafe right after jump.", false));
    private final NumberSetting jumpStraight = register(new NumberSetting("Jump Straight", "Straight jump speed.", 0.48, 0.1, 1.0, 0.01));
    private final NumberSetting jumpDiagonal = register(new NumberSetting("Jump Diagonal", "Diagonal jump speed.", 0.48, 0.1, 1.0, 0.01));
    private final BooleanSetting speedLimiter = register(new BooleanSetting("Speed Limiter", "Cap horizontal speed.", false));
    private final NumberSetting speedLimit = register(new NumberSetting("Speed Limit", "Max horizontal speed.", 0.11, 0.01, 0.4, 0.01));
    private final BooleanSetting acceleration = register(new BooleanSetting("Acceleration", "Damp horizontal inertia.", false));
    private final NumberSetting accelMultiplier = register(new NumberSetting("Accel Multiplier", "Inertia multiplier.", 0.6, 0.1, 3.0, 0.1));
    private final BooleanSetting sprintControl = register(new BooleanSetting("Sprint Control", "Override sprint state.", false));
    private final ModeSetting sprintClient = register(new ModeSetting("Sprint Client", "Client-side sprint.", "Do Not Change",
            "Do Not Change", "Force Sprint", "Force No Sprint", "No Sprint On Place"));
    private final ModeSetting sprintServer = register(new ModeSetting("Sprint Server", "Server-side sprint.", "Do Not Change",
            "Do Not Change", "Force Sprint", "Force No Sprint", "No Sprint On Place"));

    // Blink
    private final BooleanSetting blink = register(new BooleanSetting("Blink", "Queue outgoing packets.", false));
    private final NumberSetting blinkTime = register(new NumberSetting("Blink Time", "Queue hold time (ms).", 150.0, 0.0, 3000.0, 10.0));
    private final MultiModeSetting blinkFlush = register(new MultiModeSetting("Flush On", "Flush conditions.",
            new String[]{"Place", "Towering", "Sneaking", "On Ground", "In Air"}, "Place"));

    // Ротации
    private final ModeSetting rotationTiming = register(new ModeSetting("Rotation Timing", "When to rotate.", "Normal",
            "Normal", "On Tick", "On Tick Snap"));
    private final ModeSetting aimMode = register(new ModeSetting("Aim Mode", "Face point factory.", "Stabilized",
            "Center", "Random", "Stabilized", "Nearest", "Reverse Yaw", "Diagonal Yaw", "Edge Point"));
    private final BooleanSetting requiresSight = register(new BooleanSetting("Requires Sight", "Only place with line of sight.", false));

    // GodBridge / Breezily
    private final ModeSetting godBridgeMode = register(new ModeSetting("Ledge Action", "GodBridge ledge action.", "Jump",
            "Jump", "Sneak", "Stop Input", "Backwards", "Random"));
    private final NumberSetting godBridgeSneakBelow = register(new NumberSetting("Force Sneak Below", "Sneak when low on blocks.", 3.0, 0.0, 10.0, 1.0));
    private final NumberSetting breezilyEdgeMin = register(new NumberSetting("Breezily Edge Min", "Min edge distance.", 0.45, 0.25, 0.5, 0.01));
    private final NumberSetting breezilyEdgeMax = register(new NumberSetting("Breezily Edge Max", "Max edge distance.", 0.5, 0.25, 0.5, 0.01));

    // Рендер
    private final BooleanSetting render = register(new BooleanSetting("Render", "Render placed blocks.", true));
    private final ColorSetting renderColor = register(new ColorSetting("Render Color", "Placed block color.", new Color(0, 160, 255, 140)));
    private final NumberSetting renderFade = register(new NumberSetting("Render Fade", "Fade time (ms).", 600.0, 100.0, 3000.0, 50.0));
    private final BooleanSetting renderTarget = register(new BooleanSetting("Render Target", "Highlight current target.", true));

    // ============================ СОСТОЯНИЕ ============================
    private record Placement(BlockPos pos, BlockPos neighbor, Direction face, Vec3 hit) {}

    private static final Set<Block> DISALLOWED = Set.of(
            net.minecraft.world.level.block.Blocks.TNT,
            net.minecraft.world.level.block.Blocks.COBWEB,
            net.minecraft.world.level.block.Blocks.NETHER_PORTAL);

    private final Map<BlockPos, Long> renderedBlocks = new HashMap<>();
    private final ArrayDeque<net.minecraft.network.protocol.Packet<?>> blinkQueue = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> lastPlaced = new ArrayDeque<>();
    private final ArrayDeque<Vec3> placeOffsets = new ArrayDeque<>();

    private Placement currentTarget;
    private Input lastInput = new Input(false, false, false, false, false, false, false);
    private int placementY, startY, jumps, ticksUntilJump, forceSneak, delayCounter, placedSinceEagle, slotRestore;
    private double jumpOffY = Double.NaN;
    private boolean wasOnGround, wasPlaced, lastSprintState = true;
    private float lastSideways, breezilyEdge = 0.45f;
    private long blinkPulseMs, lastJumpMs;
    private int silentSlot = -1;

    public ScaffoldModule() {
        super("Scaffold", "Places blocks under you.", ModuleCategory.MOVEMENT); // если WORLD нет — замени на MISC
        instance = this;

        expandLength.visibleWhen(() -> technique.is("Expand"));
        breezilyEdgeMin.visibleWhen(() -> technique.is("Breezily"));
        breezilyEdgeMax.visibleWhen(() -> technique.is("Breezily"));
        godBridgeMode.visibleWhen(() -> technique.is("GodBridge"));
        godBridgeSneakBelow.visibleWhen(() -> technique.is("GodBridge"));
        eagleMode.visibleWhen(eagle::getValue);
        eagleEdge.visibleWhen(eagle::getValue);
        blocksToEagle.visibleWhen(eagle::getValue);
        tellyMode.visibleWhen(telly::getValue);
        tellyJump.visibleWhen(telly::getValue);
        strafeSpeed.visibleWhen(() -> strafe.getValue() && !strafeHypixel.getValue());
        strafeHypixel.visibleWhen(strafe::getValue);
        jumpStraight.visibleWhen(jumpStrafe::getValue);
        jumpDiagonal.visibleWhen(jumpStrafe::getValue);
        speedLimit.visibleWhen(speedLimiter::getValue);
        accelMultiplier.visibleWhen(acceleration::getValue);
        sprintClient.visibleWhen(sprintControl::getValue);
        sprintServer.visibleWhen(sprintControl::getValue);
        blinkTime.visibleWhen(blink::getValue);
        blinkFlush.visibleWhen(blink::getValue);
        aimMode.visibleWhen(() -> technique.is("Normal"));
        requiresSight.visibleWhen(() -> technique.is("Normal"));
        renderColor.visibleWhen(render::getValue);
        renderFade.visibleWhen(render::getValue);
        renderTarget.visibleWhen(render::getValue);
    }

    public static ScaffoldModule getInstance() { return instance; }

    // ============================ ЖИЗНЕННЫЙ ЦИКЛ ============================
    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { setEnabled(false); return; }
        placementY = mc.player.blockPosition().getY() - 1;
        startY = mc.player.blockPosition().getY();
        jumps = 2; ticksUntilJump = 0; forceSneak = 0; delayCounter = 0;
        placedSinceEagle = 0; slotRestore = 0; silentSlot = -1;
        renderedBlocks.clear(); blinkQueue.clear(); lastPlaced.clear(); placeOffsets.clear();
        currentTarget = null; wasOnGround = mc.player.onGround();
    }

    @Override
    protected void onDisable() {
        flushBlink();
        renderedBlocks.clear();
        restoreSlot(true);
        AngleConnection.INSTANCE.restoreVanillaLook();
    }

    // ============================ СЧЁТЧИК БЛОКОВ ============================
    public int getBlockCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidBlock(stack)) count += stack.getCount();
        }
        return count;
    }

    public ItemStack getBestStack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isValidBlock(stack)) continue;
            if (bestSlot == -1 || stack.getCount() > best.getCount()) { best = stack; bestSlot = i; }
        }
        return best;
    }

    private int findBestSlot() {
        Minecraft mc = Minecraft.getInstance();
        int best = -1; ItemStack bestStack = ItemStack.EMPTY;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!isValidBlock(s) || s.getCount() <= doNotUseBelow.getValue()) continue;
            if (best == -1 || s.getCount() > bestStack.getCount()) { best = i; bestStack = s; }
        }
        if (best == -1) for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (isValidBlock(s)) return i;
        }
        return best;
    }

    private boolean isValidBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        Block block = bi.getBlock();
        BlockState st = block.defaultBlockState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (st.getCollisionShape(mc.level, BlockPos.ZERO).isEmpty()) return false; // не твёрдый
        if (block instanceof FallingBlock) return false;                          // не падающий
        return !DISALLOWED.contains(block);
    }

    private boolean isUnfavourable(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return true;
        Block b = bi.getBlock();
        BlockState st = b.defaultBlockState();
        return b.getFriction() > 0.6F     // лёд
                || b.getSpeedFactor() < 1.0F  // песок душ / слизь
                || b.getJumpFactor() < 1.0F   // мёд
                || st.hasBlockEntity();       // сундуки, печки, спавнеры (вместо instanceof BaseEntityBlock)
    }

    // ============================ ГЕОМЕТРИЯ / ЦЕЛИ ============================
    private boolean isTowering() {
        Minecraft mc = Minecraft.getInstance();
        return !tower.is("None") && mc.options.keyJump.isDown() && getBlockCount() > 0 && isBlockBelow();
    }

    private boolean isBlockBelow() {
        Minecraft mc = Minecraft.getInstance();
        AABB box = mc.player.getBoundingBox().inflate(0.5, 0.0, 0.5).move(0.0, -1.05, 0.0);
        return mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private BlockPos getTargetedPosition(Vec3 predicted) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos base = BlockPos.containing(predicted);
        if (isTowering()) {
            if (tower.is("Hypixel") && !isMoving()) {
                // Боковой таверинг Hypixel: ищем ближайший незанятый бок
                BlockPos[] sides = {base.below(0).south(), base.north(), base.east(), base.west()};
                BlockPos best = null; double bestD = Double.MAX_VALUE;
                for (BlockPos s : sides) {
                    BlockPos under = s.below();
                    double d = s.distToCenterSqr(mc.player.position());
                    if (mc.level.getBlockState(under).isAir() && d < bestD) { best = under; bestD = d; }
                }
                if (best != null) return best;
            }
            return base.below();
        }
        if (down.getValue() && mc.options.keyShift.isDown()
                && !mc.level.getBlockState(mc.player.blockPosition().below(2)).isAir()) {
            return base.below(2);
        }
        if (ceiling.getValue() && !mc.level.getBlockState(mc.player.blockPosition().below()).isAir()) {
            return base.above(3);
        }
        if (sameY.is("On")) return BlockPos.containing(predicted.x, placementY, predicted.z);
        if (sameY.is("Falling") && mc.player.getDeltaMovement().y < 0.2)
            return BlockPos.containing(predicted.x, placementY, predicted.z);
        if (sameY.is("Hypixel")) {
            if (mc.player.getDeltaMovement().y == -0.15233518685055708 && jumps >= 2) {
                jumps = 0;
                return BlockPos.containing(predicted.x, startY, predicted.z);
            }
            return BlockPos.containing(predicted.x, startY - 1, predicted.z);
        }
        return base.below();
    }

    /** Поиск цели: перебор оффсетов вокруг targeted-позиции + выбор грани соседа. */
    private Placement findTarget(Vec3 predicted) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos targeted = getTargetedPosition(predicted);
        Vec3 eye = mc.player.getEyePosition();
        int range = technique.is("Expand") ? (int) expandLength.getValue() : 0;

        Placement best = null; double bestScore = Double.MAX_VALUE;

        for (int e = 0; e <= range; e++) {
            BlockPos expandBase = targeted;
            if (e > 0) {
                float yaw = mc.player.getYRot();
                expandBase = targeted.offset((int) (-Mth.sin((float) Math.toRadians(yaw)) * e), 0,
                        (int) (Mth.cos((float) Math.toRadians(yaw)) * e));
            }
            for (int dy = 0; dy >= -1; dy--) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = expandBase.offset(dx, dy, dz);
                BlockState st = mc.level.getBlockState(pos);
                if (!st.isAir() && !st.canBeReplaced()) continue;

                for (Direction d : Direction.values()) {
                    BlockPos n = pos.relative(d);
                    BlockState ns = mc.level.getBlockState(n);
                    if (ns.isAir() || ns.getCollisionShape(mc.level, n).isEmpty()) continue;

                    Direction face = d.getOpposite();               // грань соседа, в которую кликаем
                    Vec3 faceCenter = Vec3.atCenterOf(n).add(Vec3.atLowerCornerOf(face.step()).scale(0.5));
                    Vec3 aim = aimPoint(faceCenter, face, n);
                    if (eye.distanceTo(aim) > 4.4) continue;
                    if (!passesMinDist(aim, face)) continue;

                    // Проверка линии видимости до грани
                    Angle angle = MathAngle.calculateAngle(aim);
                    if (requiresSight.getValue() && technique.is("Normal")) {
                        HitResult hr = raycast(angle);
                        if (hr.getType() != HitResult.Type.BLOCK || !((BlockHitResult) hr).getBlockPos().equals(n)) continue;
                    }

                    double score = pos.distToCenterSqr(mc.player.position());
                    if (optimalLineDir() != null) {
                        Vec3 toLine = nearestLinePoint(pos);
                        score += toLine.distanceToSqr(Vec3.atCenterOf(pos)) * 2.0;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Placement(pos, n, face, aim);
                    }
                }
            }
            if (best != null && e > 0) break; // Expand: первая успешная дистанция
        }
        return best;
    }

    /** Точка прицела на грани соседа согласно Aim Mode. */
    private Vec3 aimPoint(Vec3 faceCenter, Direction face, BlockPos n) {
        Minecraft mc = Minecraft.getInstance();
        double j = 0.2;
        switch (aimMode.getValue()) {
            case "Random" -> {
                return faceCenter.add((Math.random() - 0.5) * j, (Math.random() - 0.5) * j, (Math.random() - 0.5) * j);
            }
            case "Reverse Yaw" -> {
                float yaw = mc.player.getYRot();
                return faceCenter.add(-Mth.sin((float) Math.toRadians(yaw)) * 0.25, 0, Mth.cos((float) Math.toRadians(yaw)) * 0.25);
            }
            case "Diagonal Yaw" -> {
                float yaw = mc.player.getYRot() + 45;
                return faceCenter.add(-Mth.sin((float) Math.toRadians(yaw)) * 0.25, 0, Mth.cos((float) Math.toRadians(yaw)) * 0.25);
            }
            case "Edge Point" -> {
                Vec3 p = faceCenter;
                if (face.getAxis() == Direction.Axis.X) p = new Vec3(p.x, p.y, Math.round(p.z - 0.5) + 0.5 + Math.signum(p.z - faceCenter.z) * 0.4);
                if (face.getAxis() == Direction.Axis.Z) p = new Vec3(Math.round(p.x - 0.5) + 0.5 + Math.signum(p.x - faceCenter.x) * 0.4, p.y, p.z);
                return p;
            }
            case "Nearest" -> {
                Vec3 eye = mc.player.getEyePosition();
                return faceCenter.add(Mth.clamp(eye.x - faceCenter.x, -0.4, 0.4) * 0.5,
                        Mth.clamp(eye.y - faceCenter.y, -0.4, 0.4) * 0.5,
                        Mth.clamp(eye.z - faceCenter.z, -0.4, 0.4) * 0.5);
            }
            default -> { return faceCenter; } // Center / Stabilized
        }
    }

    private boolean passesMinDist(Vec3 aim, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        double m = minDist.getValue();
        if (m <= 0 || face.getAxis() == Direction.Axis.Y) return true;
        Vec3 diff = aim.subtract(mc.player.getEyePosition());
        double dist = face.getAxis() == Direction.Axis.Z ? diff.z : diff.x;
        return Math.abs(dist) >= m;
    }

    private HitResult raycast(Angle angle) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = from.add(angle.toVector().scale(4.5));
        return mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    // ============================ OPTIMAL LINE (Stabilize / Prediction) ============================
    private Vec3 optimalLineDir() {
        if (!stabilize.getValue() && !prediction.getValue()) return null;
        float moveYaw = movementYaw();
        if (moveYaw == Float.NaN) return null;
        int oct = Math.round(moveYaw / 45f) * 45;
        double rad = Math.toRadians(oct);
        return new Vec3(-Mth.sin((float) rad), 0, Mth.cos((float) rad));
    }

    private Vec3 lineOrigin() {
        Minecraft mc = Minecraft.getInstance();
        if (!lastPlaced.isEmpty()) {
            BlockPos b = lastPlaced.peekLast();
            return new Vec3(b.getX() + 0.5, mc.player.position().y, b.getZ() + 0.5);
        }
        BlockPos under = BlockPos.containing(mc.player.position().x, mc.player.position().y - 1, mc.player.position().z);
        return new Vec3(under.getX() + 0.5, mc.player.position().y, under.getZ() + 0.5);
    }

    private Vec3 nearestLinePoint(BlockPos pos) {
        Vec3 o = lineOrigin(), d = optimalLineDir();
        if (d == null) return Vec3.atCenterOf(pos);
        Vec3 p = Vec3.atCenterOf(pos).subtract(o);
        double t = p.dot(d);
        return o.add(d.scale(t));
    }

    private boolean isMoving() {
        return lastInput.forward() || lastInput.backward() || lastInput.left() || lastInput.right();
    }

    private float movementYaw() {
        if (!isMoving()) return Float.NaN;
        Minecraft mc = Minecraft.getInstance();
        float yaw = mc.player.getYRot();
        if (lastInput.left() && !lastInput.right()) yaw += 90;
        else if (lastInput.right() && !lastInput.left()) yaw -= 90;
        if (lastInput.backward() && !lastInput.forward()) yaw += 180;
        return yaw;
    }

    // ============================ СОБЫТИЕ ВВОДА (фичи движения) ============================
    @SubscribeEvent
    private void onInput(InputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getInput() == null) return;
        lastInput = event.getInput();
        Input in = lastInput;
        boolean f = in.forward(), b = in.backward(), l = in.left(), r = in.right();
        boolean jump = in.jump(), shift = in.shift(), sprint = in.sprint();

        // --- Speed Limiter: гасим ввод при превышении скорости ---
        if (speedLimiter.getValue() && horizontalSpeed() > speedLimit.getValue()) {
            event.setDirectionalLow(false, false, false, false);
            f = b = l = r = false;
        }

        // --- Down: отменяем присед, чтобы соскользнуть вниз ---
        if (down.getValue() && mc.options.keyShift.isDown()
                && !mc.level.getBlockState(mc.player.blockPosition().below(2)).isAir()) {
            shift = false;
        }

        // --- Eagle: присед у края ---
        if (eagle.getValue() && eagleMode.is("Input") && shouldEagle() && placedSinceEagle == 0) {
            shift = true;
        }
        if (forceSneak > 0) { shift = true; forceSneak--; }

        // --- Ledge: анти-падение с края ---
        if (ledge.getValue() && isCloseToEdge()) {
            if (getBlockCount() <= 0 || currentTarget == null) {
                shift = true; forceSneak = Math.max(forceSneak, 1);
            } else if (technique.is("GodBridge")) {
                switch (godBridgeMode.getValue()) {
                    case "Jump" -> jump = true;
                    case "Sneak" -> { shift = true; forceSneak = 1; }
                    case "Stop Input" -> { f = b = l = r = false; }
                    case "Backwards" -> { f = false; b = true; }
                    case "Random" -> { if (Math.random() < 0.5) jump = true; else { shift = true; forceSneak = 1; } }
                }
            }
        }

        // --- Telly: авто-прыжки ---
        if (telly.getValue() && isMoving() && getBlockCount() > 0 && mc.player.onGround()
                && ticksUntilJump >= tellyJump.getValue()) {
            jump = true;
        }

        // --- Breezily: микро-стрейфы к краю блока ---
        if (technique.is("Breezily") && f && !shift) {
            float side = breezilySideways();
            if (side != 0) { l = side < 0; r = side > 0; }
        }

        // --- Stabilize: удержание оптимальной линии ---
        if (stabilize.getValue() && !(jump && mc.player.onGround()) && optimalLineDir() != null && isMoving()) {
            Vec3 d = optimalLineDir();
            Vec3 toLine = nearestLinePoint(mc.player.blockPosition()).subtract(mc.player.position());
            double dev = Math.sqrt(toLine.x * toLine.x + toLine.z * toLine.z);
            boolean towards = toLine.dot(mc.player.getDeltaMovement()) > 0;
            double maxDev = towards ? 0.075 : 0.2;
            if (dev > maxDev) {
                float want = (float) Math.toDegrees(Math.atan2(toLine.z, toLine.x)) - 90f;
                float diff = Mth.wrapDegrees(want - mc.player.getYRot());
                boolean nf = false, nb = false, nl = false, nr = false;
                if (diff >= -22.5 && diff < 22.5) nf = true;
                else if (diff >= 22.5 && diff < 67.5) { nf = true; nr = true; }
                else if (diff >= 67.5 && diff < 112.5) nr = true;
                else if (diff >= 112.5 && diff < 157.5) { nb = true; nr = true; }
                else if (diff >= -67.5 && diff < -22.5) { nf = true; nl = true; }
                else if (diff >= -112.5 && diff < -67.5) nl = true;
                else if (diff >= -157.5 && diff < -112.5) { nb = true; nl = true; }
                else nb = true;
                if (!f && !b) { f = nf; b = nb; }
                if (!l && !r) { l = nl; r = nr; }
            }
        }

        // --- Sprint Control (client) ---
        if (sprintControl.getValue()) {
            switch (sprintClient.getValue()) {
                case "Force Sprint" -> { if (isMoving()) { sprint = true; event.setSprinting(true); } }
                case "Force No Sprint" -> { sprint = false; event.setSprinting(false); }
                case "No Sprint On Place" -> { if (wasPlaced) { sprint = false; event.setSprinting(false); } }
                default -> {}
            }
        }

        event.setInput(new Input(f, b, l, r, jump, shift, sprint));
    }

    private boolean shouldEagle() {
        Minecraft mc = Minecraft.getInstance();
        return !mc.player.getAbilities().flying && mc.player.onGround() && isCloseToEdgeDist(eagleEdge.getValue());
    }

    private boolean isCloseToEdge() { return isCloseToEdgeDist(0.0); }

    private boolean isCloseToEdgeDist(double dist) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = movementYaw();
        if (yaw == Float.NaN) yaw = mc.player.getYRot();
        double rad = Math.toRadians(yaw);
        Vec3 ahead = mc.player.position().add(-Mth.sin((float) rad) * (0.4 + dist), -1.0, Mth.cos((float) rad) * (0.4 + dist));
        BlockPos under = BlockPos.containing(ahead);
        return mc.level.getBlockState(under).getCollisionShape(mc.level, under).isEmpty();
    }

    private float breezilySideways() {
        Minecraft mc = Minecraft.getInstance();
        double modX = mc.player.position().x - Math.floor(mc.player.position().x);
        double modZ = mc.player.position().z - Math.floor(mc.player.position().z);
        double ma = 1 - breezilyEdge;
        Direction dir = Direction.fromYRot(mc.player.getYRot());
        float cur = 0;
        switch (dir) {
            case SOUTH -> { if (modX > ma) cur = 1; if (modX < breezilyEdge) cur = -1; }
            case NORTH -> { if (modX > ma) cur = -1; if (modX < breezilyEdge) cur = 1; }
            case EAST -> { if (modZ > ma) cur = -1; if (modZ < breezilyEdge) cur = 1; }
            case WEST -> { if (modZ > ma) cur = 1; if (modZ < breezilyEdge) cur = -1; }
            default -> {}
        }
        if (cur != 0 && cur != lastSideways) {
            lastSideways = cur;
            breezilyEdge = (float) (breezilyEdgeMin.getValue() + Math.random() * (breezilyEdgeMax.getValue() - breezilyEdgeMin.getValue()));
        }
        if (cur != 0) lastSideways = cur;
        return lastSideways;
    }

    private double horizontalSpeed() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 v = mc.player.getDeltaMovement();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    // ============================ РОТАЦИИ ============================
    @SubscribeEvent
    private void onRotationUpdate(RotationUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getPhase() != EventPhase.PRE) return;

        Vec3 predicted = predictPosition();
        currentTarget = findTarget(predicted);
        if (currentTarget == null) return;
        if (rotationTiming.is("Normal")) {
            Angle angle = computeRotation();
            if (angle == null) return;
            AngleConnection.INSTANCE.rotateTo(
                    new Angle.VecRotation(angle, angle.toVector()),
                    null, 1,
                    new AngleConfig(new LinearConstructor(), true, false),
                    TaskPriority.HIGH_IMPORTANCE_1, this);
        }
    }

    /** Предсказание позиции установки (упрощённый ScaffoldMovementPrediction). */
    private Vec3 predictPosition() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = mc.player.position();
        if (!prediction.getValue()) return pos;
        if (isCloseToEdge()) return pos;
        // среднее смещение последних установок + инерция
        Vec3 avg = averageOffset();
        if (avg != null) return pos.add(avg.scale(0.5));
        return pos.add(mc.player.getDeltaMovement().scale(1.0));
    }

    private Vec3 averageOffset() {
        if (placeOffsets.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        for (Vec3 v : placeOffsets) { x += v.x; y += v.y; z += v.z; }
        int n = placeOffsets.size();
        return new Vec3(x / n, y / n, z / n);
    }

    /** Углы по текущей технике. */
    private Angle computeRotation() {
        Minecraft mc = Minecraft.getInstance();
        if (currentTarget == null) return null;

        if (technique.is("Expand")) {
            return MathAngle.calculateAngle(Vec3.atCenterOf(currentTarget.pos));
        }

        if (technique.is("GodBridge") || technique.is("Breezily")) {
            float my = movementYaw();
            if (my == Float.NaN) {
                float axis = (float) (Math.floor(AngleConnection.INSTANCE.getRotation().getYaw() / 90) * 90);
                return new Angle(axis + 45, technique.is("GodBridge") ? 75f : 75f);
            }
            float movingYaw = Math.round((my + 180) / 45f) * 45f;
            boolean straight = movingYaw % 90 == 0;
            if (technique.is("Breezily")) {
                return new Angle(movingYaw, straight ? 80f : 75.6f);
            }
            // GodBridge
            if (straight) {
                boolean rightSide = isOnRightSide(movingYaw);
                return new Angle(movingYaw + (rightSide ? 45 : -45), 75.7f);
            }
            return new Angle(movingYaw, 75.6f);
        }

        // Normal: прицел в точку на грани
        return MathAngle.calculateAngle(currentTarget.hit);
    }

    private boolean isOnRightSide(float movingYaw) {
        Minecraft mc = Minecraft.getInstance();
        double rad = Math.toRadians(movingYaw);
        double px = mc.player.position().x + -Mth.sin((float) rad) * 0.5;
        double pz = mc.player.position().z + Mth.cos((float) rad) * 0.5;
        return Math.floor(px) != Math.floor(mc.player.position().x)
                || Math.floor(pz) != Math.floor(mc.player.position().z);
    }

    // ============================ ГЛАВНЫЙ ТИК ============================
    @SubscribeEvent(priority = EventPriority.HIGH)
    private void onPreTick(TickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean onGround = mc.player.onGround();
        if (onGround) {
            placementY = mc.player.blockPosition().getY() - 1;
            jumps++;
            ticksUntilJump++;
        } else {
            ticksUntilJump = 0;
        }
        if (mc.options.keyJump.isDown()) { startY = mc.player.blockPosition().getY(); jumps = 2; }

        // Детект прыжка для JumpStrafe
        if (wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0) {
            lastJumpMs = System.currentTimeMillis();
            applyJumpStrafe();
        }
        wasOnGround = onGround;
        wasPlaced = false;

        towerTick(mc);          // Tower-режимы
        headHitterTick(mc);     // Head Hitter
        movementTick(mc);       // Strafe / Acceleration

        if (delayCounter > 0) { delayCounter--; return; }
        if (currentTarget == null) return;

        // Валидация прицела текущей ротацией
        Angle rot = rotationTiming.is("Normal")
                ? AngleConnection.INSTANCE.getRotation()
                : computeRotation();
        if (rot == null) return;

        if (!rotationTiming.is("Normal")) {
            // On Tick / On Tick Snap: мгновенный поворот + пакет ротации
            mc.player.setYRot(rot.getYaw());
            mc.player.setXRot(rot.getPitch());
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(rot.getYaw(), rot.getPitch(), onGround, mc.player.isSprinting()));
            }
        }

        HitResult hr = raycast(rot);
        if (hr.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult bhr = (BlockHitResult) hr;
        if (!bhr.getBlockPos().equals(currentTarget.neighbor)) return;

        place(mc, bhr);
    }

    private void place(Minecraft mc, BlockHitResult crosshair) {
        InteractionHand hand = pickHand(mc);
        if (hand == null) {
            if (!autoBlock.getValue()) return;
            int slot = findBestSlot();
            if (slot == -1) return;
            silentSlot = slot;
            slotRestore = (int) slotResetDelay.getValue();
            mc.player.getInventory().setSelectedSlot(slot);
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
            hand = InteractionHand.MAIN_HAND;
        }

        boolean ok = mc.gameMode.useItemOn(mc.player, hand,
                        new BlockHitResult(currentTarget.hit, currentTarget.face, currentTarget.neighbor, false))
                .consumesAction();
        if (!ok) return;

        // Swing
        switch (swing.getValue()) {
            case "Do Not Hide" -> { mc.player.swing(hand); }
            case "Only Hand" -> { mc.player.swing(hand); }
            case "Only Packet" -> { if (mc.getConnection() != null) mc.getConnection().send(new ServerboundSwingPacket(hand)); }
            case "Hide" -> {}
        }
        if (swing.is("Do Not Hide") && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSwingPacket(hand));
        }

        // Рендер + трекинг
        renderedBlocks.put(currentTarget.pos.immutable(), System.currentTimeMillis());
        lastPlaced.addLast(currentTarget.pos.immutable());
        while (lastPlaced.size() > 4) lastPlaced.removeFirst();
        placeOffsets.addLast(mc.player.position().subtract(Vec3.atCenterOf(currentTarget.pos)));
        while (placeOffsets.size() > 4) placeOffsets.removeFirst();

        // Коллбэки фич
        wasPlaced = true;
        placedSinceEagle++;
        if (placedSinceEagle > (int) blocksToEagle.getValue()) {
            placedSinceEagle = 0;
            if (eagle.getValue() && eagleMode.is("Packet") && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            }
        }
        blinkPulseMs = System.currentTimeMillis();
        delayCounter = (int) delay.getValue();
        currentTarget = null;
    }

    private InteractionHand pickHand(Minecraft mc) {
        if (isValidBlock(mc.player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isValidBlock(mc.player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (alwaysHold.getValue() || autoBlock.getValue()) {
            if (silentSlot != -1 && isValidBlock(mc.player.getInventory().getItem(silentSlot))) return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private void restoreSlot(boolean force) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || silentSlot == -1) return;
        if (slotRestore > 0 && !force) { slotRestore--; return; }
        mc.player.getInventory().setSelectedSlot(silentSlot == -1 ? 0 : prevSlot());
        silentSlot = -1;
    }

    private int prevSlot() { return 0; } // храни предыдущий слот при желании

    // ============================ TOWER ============================
    private void towerTick(Minecraft mc) {
        if (!isTowering()) { jumpOffY = Double.NaN; return; }
        boolean jumpHeld = mc.options.keyJump.isDown();
        if (!jumpHeld || getBlockCount() <= 0) return;
        Vec3 v = mc.player.getDeltaMovement();
        int age = mc.player.tickCount;

        switch (tower.getValue()) {
            case "Motion" -> {
                if (Double.isNaN(jumpOffY)) jumpOffY = mc.player.position().y;
                if (mc.player.position().y > jumpOffY + 0.78) {
                    mc.player.setPos(mc.player.position().x, Math.floor(mc.player.position().y), mc.player.position().z);
                    mc.player.setDeltaMovement(v.x * 1.0, 0.42, v.z * 1.0);
                    mc.player.awardStat(Stats.JUMP);
                    jumpOffY = mc.player.position().y;
                }
            }
            case "Pulldown" -> {
                pulldownTrigger.visibleWhen(() -> tower.is("Pulldown"));
                if (!mc.player.onGround() && v.y < 0.1 && isBlockBelow()) mc.player.setDeltaMovement(v.x, -1.0, v.z);
            }
            case "Karhu" -> {
                // TODO: подключи Timer API Polaris: TimerUtil.setTimer(5.0f)
                if (!mc.player.onGround() && v.y < 0.06 && isBlockBelow()) mc.player.setDeltaMovement(v.x, v.y - 1.0, v.z);
            }
            case "Vulcan" -> {
                if (age % 2 == 0) mc.player.setDeltaMovement(v.x, 0.7, v.z);
                else {
                    mc.player.setDeltaMovement(v.x, isMoving() ? 0.42 : 0.6, v.z);
                    mc.player.awardStat(Stats.JUMP);
                }
            }
            case "Hypixel" -> {
                if (!isMoving() && mc.player.position().x % 1.0 != 0) {
                    double dx = Mth.clamp(Math.round(mc.player.position().x) - mc.player.position().x, -0.281, 0.281);
                    mc.player.setDeltaMovement(dx, v.y, v.z);
                }
                if (getAirTicks() > 14) { mc.player.setDeltaMovement(v.x * 0.6, v.y - 0.09, v.z * 0.6); return; }
                switch (getAirTicks() % 3) {
                    case 0 -> setStrafeVelocity(0.247 - Math.random() / 100.0, 0.42);
                    case 2 -> mc.player.setDeltaMovement(v.x, 1 - (mc.player.position().y % 1.0), v.z);
                }
            }
        }
    }

    private int airTicks;
    private int getAirTicks() { return airTicks; }

    // ============================ ДВИЖЕНИЕ / СТРЕЙФ ============================
    private void movementTick(Minecraft mc) {
        Vec3 v = mc.player.getDeltaMovement();

        if (acceleration.getValue() && mc.player.onGround()) {
            mc.player.setDeltaMovement(v.x * accelMultiplier.getValue(), v.y, v.z * accelMultiplier.getValue());
            v = mc.player.getDeltaMovement();
        }
        if (strafe.getValue() && isMoving()) {
            if (strafeHypixel.getValue()) {
                double s = 0.207;
                if (mc.player.tickCount % 20 == 0 || ticksUntilJump <= 7) s = 0.098;
                setStrafeVelocity(s, v.y);
            } else {
                setStrafeVelocity(strafeSpeed.getValue(), v.y);
            }
        }
        airTicks = mc.player.onGround() ? 0 : airTicks + 1;

        // Sprint server-side
        if (sprintControl.getValue() && mc.getConnection() != null) {
            boolean want = switch (sprintServer.getValue()) {
                case "Force Sprint" -> isMoving();
                case "Force No Sprint" -> false;
                case "No Sprint On Place" -> !wasPlaced;
                default -> mc.player.isSprinting();
            };
            if (want != lastSprintState) {
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,
                        want ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                                : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                lastSprintState = want;
            }
        }
    }

    private void applyJumpStrafe() {
        if (!jumpStrafe.getValue()) return;
        Minecraft mc = Minecraft.getInstance();
        float my = movementYaw();
        if (my == Float.NaN) return;
        float rounded = Math.round((my + 180) / 45f) * 45f;
        boolean straight = rounded % 90 == 0;
        double speed = (straight ? jumpStraight.getValue() : jumpDiagonal.getValue())
                + (Math.random() - 0.5) * 0.01;
        Vec3 v = mc.player.getDeltaMovement();
        setStrafeVelocity(speed, v.y);
    }

    private void setStrafeVelocity(double speed, double keepY) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = movementYaw();
        if (yaw == Float.NaN) yaw = mc.player.getYRot();
        double rad = Math.toRadians(yaw);
        mc.player.setDeltaMovement(-Mth.sin((float) rad) * speed, keepY, Mth.cos((float) rad) * speed);
    }

    private void headHitterTick(Minecraft mc) {
        if (!headHitter.getValue()) return;
        BlockPos above = mc.player.blockPosition().above(2);
        if (!mc.level.getBlockState(above).isAir() && mc.player.onGround() && isMoving()) {
            mc.player.jumpFromGround();
        }
    }

    // ============================ BLINK ============================
    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Vulcan: микро-смещения пакетов движения
        if (tower.is("Vulcan") && isTowering() && !event.isReceive()
                && event.getPacket() instanceof ServerboundMovePlayerPacket p
                && p.hasPosition() && !isMoving() && mc.player.tickCount % 2 == 0) {
            event.setCancelled(true);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        p.position().x + 0.1, p.position().y, p.position().z + 0.1, p.isOnGround()));
            }
            return;
        }

        // Blink: очередь исходящих пакетов
        if (!blink.getValue() || event.isReceive()) return;
        boolean elapsed = System.currentTimeMillis() - blinkPulseMs >= blinkTime.getValue();
        boolean flush = blinkFlush.isSelected("Place") && wasPlaced
                || blinkFlush.isSelected("Towering") && isTowering()
                || blinkFlush.isSelected("Sneaking") && mc.player.hasEnoughImpulseToStartSprinting()
                || blinkFlush.isSelected("On Ground") && mc.player.onGround()
                || blinkFlush.isSelected("In Air") && !mc.player.onGround();
        if (elapsed || flush) {
            flushBlink();
            return;
        }
        event.setCancelled(true);
        blinkQueue.addLast(event.getPacket());
    }

    private void flushBlink() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) { blinkQueue.clear(); return; }
        while (!blinkQueue.isEmpty()) mc.getConnection().send(blinkQueue.removeFirst());
    }

    // ============================ РЕНДЕР ============================
    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (!render.getValue() || mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        int rgb = renderColor.getValue().getRGB();

        renderedBlocks.entrySet().removeIf(e -> now - e.getValue() > renderFade.getValue());
        for (Map.Entry<BlockPos, Long> e : renderedBlocks.entrySet()) {
            float t = 1f - (now - e.getValue()) / renderFade.getFloat();
            int faded = ColorUtil.withAlpha(rgb, Math.round(((rgb >> 24) & 0xFF) * Mth.clamp(t, 0, 1)));
            AABB box = new AABB(e.getKey());
            Render3D.drawBox(box, faded, 1.0f, true, true, false);
            Render3D.drawBoxOverlay(box, faded, 1.25f);
        }

        if (renderTarget.getValue() && currentTarget != null) {
            int target = ColorUtil.withAlpha(rgb, 90);
            Render3D.drawBox(new AABB(currentTarget.pos), target, 1.2f, true, false, false);
            Render3D.drawBoxOverlay(new AABB(currentTarget.pos), target, 1.5f);
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            currentTarget = null;
            renderedBlocks.clear();
        }
        if (slotRestore > 0) slotRestore--;
        else restoreSlot(false);
    }
}