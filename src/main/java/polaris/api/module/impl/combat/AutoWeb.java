package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.BooleanSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoWeb extends Module {

    private final NumberSetting range = register(new NumberSetting("Range", "Target scan range.", 5.0, 1.0, 7.0, 0.5));
    private final NumberSetting blocksPerTick = register(new NumberSetting("Blocks/Tick", "How many webs to place per tick.", 4.0, 1.0, 8.0, 1.0));
    private final NumberSetting placeDelay = register(new NumberSetting("Delay", "Tick delay between actions.", 1.0, 0.0, 10.0, 1.0));

    private final BooleanSetting head = register(new BooleanSetting("Head", "Place on enemy head", true));
    private final BooleanSetting legs = register(new BooleanSetting("Legs", "Place on enemy legs", true));
    private final BooleanSetting surround = register(new BooleanSetting("Surround", "Place web around enemy legs", false));

    private final Minecraft mc = Minecraft.getInstance();
    private int ticksDelay = 0;

    public AutoWeb() {
        super("AutoWebLite", "Traps nearby enemies in cobwebs dynamically.", ModuleCategory.COMBAT);
    }

    @Override
    public void onEnable() {
        ticksDelay = 0;
    }

    @SubscribeEvent
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

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

        int slot = getWebSlot();
        if (slot == -1) return;

        BlockPos targetBp = BlockPos.containing(target.getX(), target.getY(), target.getZ());
        List<BlockPos> targets = new ArrayList<>();

        if (legs.getValue()) targets.add(targetBp);
        if (head.getValue()) targets.add(targetBp.above());

        if (surround.getValue()) {
            targets.add(targetBp.east());
            targets.add(targetBp.west());
            targets.add(targetBp.south());
            targets.add(targetBp.north());
        }

        int placed = 0;
        // ЧТЕНИЕ через геттер getSelectedSlot()
        int originalSlot = mc.player.getInventory().getSelectedSlot();

        for (BlockPos pos : targets) {
            if (placed >= blocksPerTick.getValue().intValue()) break;

            if (canPlace(pos)) {
                if (placeBlockLegit(pos, slot, originalSlot)) {
                    placed++;
                }
            }
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
    private boolean placeBlockLegit(BlockPos pos, int slot, int originalSlot) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = mc.level.getBlockState(neighbor);

            if (!neighborState.canBeReplaced()) {
                Direction sideToClick = dir.getOpposite();

                // Расчет вектора клика через step-методы Mojang
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(
                        new Vec3(sideToClick.getStepX(), sideToClick.getStepY(), sideToClick.getStepZ()).scale(0.5)
                );

                // Расчет углов для бесшумного поворота (Silent Rotation)
                // Вычисляем ротации
                float[] rotations = getRotations(hitVec);

// Сохраняем реальные углы обзора, которые были у игрока
                float pitchBefore = mc.player.getXRot();
                float yawBefore = mc.player.getYRot();

// Моментально поворачиваем голову игрока на блок
                mc.player.setYRot(rotations[0]);
                mc.player.setXRot(rotations[1]);

// --- [Тут идет ваш код смены слота, клика useItemOn и свинга] ---

// После клика моментально возвращаем камеру игрока на исходное место
                mc.player.setYRot(yawBefore);
                mc.player.setXRot(pitchBefore);


                // Смена слота на паутину через сеттер setSelectedSlot
                if (slot != originalSlot) {
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
                    mc.player.getInventory().setSelectedSlot(slot);
                }

                // Клик по блоку и визуальный замах
                BlockHitResult hitResult = new BlockHitResult(hitVec, sideToClick, neighbor, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
                mc.player.swing(InteractionHand.MAIN_HAND);

                // Возврат к оружию/исходному предмету
                if (slot != originalSlot) {
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(originalSlot));
                    mc.player.getInventory().setSelectedSlot(originalSlot);
                }

                return true;
            }
        }
        return false;
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

    private int getWebSlot() {
        if (mc.player.getMainHandItem().is(Items.COBWEB)) {
            // ЧТЕНИЕ через геттер getSelectedSlot()
            return mc.player.getInventory().getSelectedSlot();
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.COBWEB)) {
                return i;
            }
        }
        return -1;
    }
}
