package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.Render3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoWebV2 extends Module {

    private final NumberSetting range = register(new NumberSetting("Range", "Target scan range.", 5.0, 1.0, 7.0, 0.5));
    private final NumberSetting blocksPerTick = register(new NumberSetting("Blocks/Tick", "How many webs to place per tick.", 4.0, 1.0, 8.0, 1.0));
    private final NumberSetting placeDelay = register(new NumberSetting("Delay", "Tick delay between actions.", 1.0, 0.0, 10.0, 1.0));

    private final BooleanSetting head = register(new BooleanSetting("Head(бошка)", "Place on enemy head", true));
    private final BooleanSetting aboveHead = register(new BooleanSetting("Above Head(над бошкой)", "Place web above enemy head", false));
    private final BooleanSetting legs = register(new BooleanSetting("Legs(ноги)", "Place on enemy legs", true));
    private final BooleanSetting surround = register(new BooleanSetting("Surround(под игроком)", "Place web around enemy legs", false));

    private final BooleanSetting ignoreWalls = register(new BooleanSetting("Ignore Walls", "Place cobwebs through walls.", true));
    private final BooleanSetting antiFriend = register(new BooleanSetting("Anti Friend", "Don't trap players in your friend list.", true));

    // Режим ротаций: Snap (мгновенно), Legit (плавный поворот камеры), Packet (пакет без поворота камеры)
    private final ModeSetting rotationMode = register(new ModeSetting(
            "Rotation Mode",
            "How to aim at the placement side.",
            "Snap",
            "Snap", "Legit", "Packet"
    ));
    private final NumberSetting turnSpeed = register(new NumberSetting("Turn Speed", "Degrees per tick for Legit rotations.", 45.0, 5.0, 180.0, 5.0));

    // Place ESP
    private final BooleanSetting placeEsp = register(new BooleanSetting("Place ESP", "Highlight blocks where web is being placed.", true));
    private final ColorSetting espColor = register(new ColorSetting("ESP Color", "Color of the placement highlight.", new Color(0, 200, 255, 140)));
    private final NumberSetting espFade = register(new NumberSetting("ESP Fade", "Highlight fade time in ms.", 600.0, 100.0, 3000.0, 100.0));

    private final ModeSetting switchMode = register(new ModeSetting(
            "Switch Mode",
            "How to swap to cobwebs.",
            "Normal",
            "None", "Normal", "Silent", "Inventory"
    ));

    private final Map<BlockPos, Long> renderPoses = new ConcurrentHashMap<>();

    private final Minecraft mc = Minecraft.getInstance();
    private int ticksDelay = 0;

    public enum SwitchMode {
        None, Normal, Silent, Inventory
    }

    public enum RotationMode {
        Snap, Legit, Packet
    }

    /** Точка клика: соседний блок + грань + координаты хита. */
    private record Placement(BlockPos neighbor, Direction side, Vec3 hitVec) {
    }

    public AutoWebV2() {
        super("AutoWeb", "Traps nearby enemies in cobwebs dynamically.", ModuleCategory.COMBAT);
    }

    @Override
    public void onEnable() {
        ticksDelay = 0;
        renderPoses.clear();
    }

    @Override
    public void onDisable() {
        renderPoses.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (ticksDelay > 0) {
            ticksDelay--;
            return;
        }

        Player target = mc.level.players().stream()
                .filter(p -> p != mc.player && !p.isDeadOrDying() && !p.isSpectator())
                .filter(p -> mc.player.distanceTo(p) <= range.getValue().floatValue())
                .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)))
                .orElse(null);

        if (target == null) return;

        SwitchMode mode;
        try {
            mode = SwitchMode.valueOf(switchMode.getValue());
        } catch (Exception e) {
            mode = SwitchMode.Normal;
        }

        RotationMode rotMode;
        try {
            rotMode = RotationMode.valueOf(rotationMode.getValue());
        } catch (Exception e) {
            rotMode = RotationMode.Snap;
        }

        int slot = getWebSlot(mode);
        if (slot == -1) return;

        BlockPos targetBp = BlockPos.containing(target.getX(), target.getY(), target.getZ());
        List<BlockPos> targets = new ArrayList<>();

        if (legs.getValue()) targets.add(targetBp);
        if (head.getValue()) targets.add(targetBp.above());
        if (aboveHead.getValue()) targets.add(targetBp.above(2));
        if (surround.getValue()) {
            targets.add(targetBp.east());
            targets.add(targetBp.west());
            targets.add(targetBp.south());
            targets.add(targetBp.north());
        }

        int placed = 0;
        int maxPerTick = blocksPerTick.getValue().intValue();

        for (BlockPos pos : targets) {
            if (placed >= maxPerTick) break;
            if (!canPlace(pos)) continue;

            if (placeBlock(pos, slot, mode, rotMode)) {
                placed++;
            }

            // В Legit камера поворачивается постепенно — за тик обрабатываем только одну позицию
            if (rotMode == RotationMode.Legit) break;
        }

        if (placed > 0) {
            ticksDelay = placeDelay.getValue().intValue();
        }
    }

    private boolean canPlace(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced()) return false;

        for (Direction dir : Direction.values()) {
            if (!mc.level.getBlockState(pos.relative(dir)).canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private Placement findPlacement(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.canBeReplaced()) continue;

            Direction sideToClick = dir.getOpposite();
            Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                    new Vec3(sideToClick.getStepX(), sideToClick.getStepY(), sideToClick.getStepZ()).scale(0.5)
            );

            if (!ignoreWalls.getValue()) {
                ClipContext context = new ClipContext(
                        mc.player.getEyePosition(), hitVec,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, mc.player
                );
                if (mc.level.clip(context).getType() != HitResult.Type.MISS) continue;
            }

            return new Placement(neighbor, sideToClick, hitVec);
        }
        return null;
    }

    private boolean placeBlock(BlockPos pos, int slot, SwitchMode mode, RotationMode rotMode) {
        Placement placement = findPlacement(pos);
        if (placement == null) return false;

        float[] rotations = getRotations(placement.hitVec());
        float yawBefore = mc.player.getYRot();
        float pitchBefore = mc.player.getXRot();

        switch (rotMode) {
            case Snap -> {
                mc.player.setYRot(rotations[0]);
                mc.player.setXRot(rotations[1]);
            }
            case Legit -> {
                float speed = turnSpeed.getValue().floatValue();
                float newYaw = stepYaw(yawBefore, rotations[0], speed);
                float newPitch = stepPitch(pitchBefore, rotations[1], speed);
                mc.player.setYRot(newYaw);
                mc.player.setXRot(newPitch);

                // Ещё не докрутились — установка будет на следующих тиках
                if (Math.abs(newYaw - rotations[0]) > 0.01f || Math.abs(newPitch - rotations[1]) > 0.01f) {
                    return false;
                }
            }
            case Packet -> sendRotationPacket(rotations[0], rotations[1]);
        }

        // --- Свитч слота ---
        int originalSlot = mc.player.getInventory().getSelectedSlot();
        int useSlot = slot;
        boolean inventorySwapped = false;

        if (mode == SwitchMode.Inventory && useSlot > 8) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, useSlot, originalSlot, ClickType.SWAP, mc.player);
            useSlot = originalSlot;
            inventorySwapped = true;
        } else if (useSlot != originalSlot && useSlot <= 8 && mode != SwitchMode.None) {
            if (mode == SwitchMode.Normal) {
                mc.player.getInventory().setSelectedSlot(useSlot);
            } else {
                sendCarriedItem(useSlot);
            }
        }

        // --- Взаимодействие и замах руки ---
        BlockHitResult hitResult = new BlockHitResult(placement.hitVec(), placement.side(), placement.neighbor(), false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND);

        // --- Возврат слота ---
        if (inventorySwapped) {
            mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, originalSlot, ClickType.SWAP, mc.player);
        } else if (useSlot != originalSlot && useSlot <= 8 && mode != SwitchMode.None) {
            if (mode == SwitchMode.Normal) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } else {
                sendCarriedItem(originalSlot);
            }
        }

        // --- Возврат углов камеры ---
        switch (rotMode) {
            case Snap -> {
                mc.player.setYRot(yawBefore);
                mc.player.setXRot(pitchBefore);
            }
            case Packet -> sendRotationPacket(yawBefore, pitchBefore);
            case Legit -> {
                // камера честно остаётся повёрнутой — ничего не возвращаем
            }
        }

        if (placeEsp.getValue()) {
            renderPoses.put(pos.immutable(), System.currentTimeMillis());
        }
        return true;
    }

    private void sendRotationPacket(float yaw, float pitch) {
        if (mc.getConnection() == null) return;
        // Для 1.21.2+ (4 аргумента). Если маппинги старше и конструктор подсвечен красным —
        // убери последний аргумент: new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround())
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
    }

    private void sendCarriedItem(int slot) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static float stepYaw(float current, float target, float maxStep) {
        float diff = Mth.wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.copySign(maxStep, diff);
    }

    private static float stepPitch(float current, float target, float maxStep) {
        float diff = Mth.clamp(target - current, -maxStep, maxStep);
        if (Math.abs(diff) <= 0.01f) return target;
        return current + diff;
    }

    private float[] getRotations(Vec3 vec) {
        Vec3 eyesPos = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{
                mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()),
                mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot())
        };
    }

    // ================= Place ESP =================

    @SubscribeEvent
    private void onRender3D(WorldRenderEvent event) {
        if (!placeEsp.getValue() || mc.level == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long fadeMs = Math.max(100L, espFade.getValue().longValue());
        int baseRgb = espColor.getValue().getRGB();
        int baseAlpha = (baseRgb >>> 24) & 0xFF;

        renderPoses.entrySet().removeIf(entry -> now - entry.getValue() > fadeMs);

        for (Map.Entry<BlockPos, Long> entry : renderPoses.entrySet()) {
            float progress = Math.min(1f, (now - entry.getValue()) / (float) fadeMs);
            int alpha = (int) (baseAlpha * (1f - progress));
            int color = (baseRgb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);

            AABB box = new AABB(entry.getKey());
            Render3D.drawBox(box, color, 1.0f, true, true, false);
            Render3D.drawBoxOverlay(box, color, 1.25f);
        }
    }

    // ================= Slots =================

    private int getWebSlot(SwitchMode mode) {
        if (mode == SwitchMode.None) {
            return mc.player.getMainHandItem().is(Items.COBWEB) ? mc.player.getInventory().getSelectedSlot() : -1;
        }

        if (mc.player.getMainHandItem().is(Items.COBWEB)) {
            return mc.player.getInventory().getSelectedSlot();
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.COBWEB)) {
                return i;
            }
        }

        if (mode == SwitchMode.Inventory) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getItem(i).is(Items.COBWEB)) {
                    return i;
                }
            }
        }
        return -1;
    }
}