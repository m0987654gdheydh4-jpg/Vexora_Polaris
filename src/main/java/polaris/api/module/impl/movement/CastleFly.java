package polaris.api.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.string.chat.ChatMessage;


public final class CastleFly extends Module {
    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "Placement mode.",
            "Safe", "Safe", "Risk"));
    private final NumberSetting delayMs = register(new NumberSetting(
            "Delay", "Minimum delay between places (ms).", 100.0, 0.0, 500.0, 10.0));

    private long lastPlaceMs;
    private int placeCooldownTicks;
    private int riskFloorY = Integer.MIN_VALUE;

    public CastleFly() {
        super("CastleFly", "Places blocks under/around you for tower fly (Zenith-style).", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        ChatMessage.brandmessage("Держи ЛЮБОЙ блок в руке");
        lastPlaceMs = 0L;
        placeCooldownTicks = 0;
        riskFloorY = Integer.MIN_VALUE;
    }

    @Override
    protected void onDisable() {
        placeCooldownTicks = 0;
        riskFloorY = Integer.MIN_VALUE;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        if (client.screen != null) {
            return;
        }
        if (placeCooldownTicks > 0) {
            placeCooldownTicks--;
            return;
        }
        if (!isHoldingBlock(client)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlaceMs < delayMs.getValue().longValue()) {
            return;
        }

        if (mode.is("Safe")) {
            tryPlaceSafe(client);
        } else {
            tryPlaceRisk(client);
        }
    }

    private void tryPlaceSafe(Minecraft client) {
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        
        if (placeAgainst(client, blockHit)) {
            lastPlaceMs = System.currentTimeMillis();
            placeCooldownTicks = 2;
        }
    }

    private void tryPlaceRisk(Minecraft client) {
        
        if (client.options.keyJump.isDown() && client.player.onGround()) {
            riskFloorY = (int) Math.floor(client.player.getY() - 1.0);
        }
        if (client.player.onGround() && !client.options.keyJump.isDown()) {
            return;
        }

        BlockPos under = BlockPos.containing(client.player.getX(), client.player.getY() - 1.0, client.player.getZ());
        PlaceCandidate candidate = findPlaceable(client, under);
        if (candidate == null) {
            
            Vec3 vel = client.player.getDeltaMovement();
            BlockPos ahead = BlockPos.containing(
                    client.player.getX() + vel.x * 2.0,
                    client.player.getY() - 1.0,
                    client.player.getZ() + vel.z * 2.0
            );
            candidate = findPlaceable(client, ahead);
        }
        if (candidate == null) {
            return;
        }

        Vec3 hitVec = Vec3.atCenterOf(candidate.support).add(
                candidate.face.getStepX() * 0.5,
                candidate.face.getStepY() * 0.5,
                candidate.face.getStepZ() * 0.5
        );
        
        lookAt(client, hitVec);

        BlockHitResult hit = new BlockHitResult(hitVec, candidate.face, candidate.support, false);
        if (placeAgainst(client, hit)) {
            lastPlaceMs = System.currentTimeMillis();
            placeCooldownTicks = 1;
        }
    }

    private boolean placeAgainst(Minecraft client, BlockHitResult hit) {
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) {
            client.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        
        ItemStack off = client.player.getOffhandItem();
        if (off.getItem() instanceof BlockItem) {
            result = client.gameMode.useItemOn(client.player, InteractionHand.OFF_HAND, hit);
            if (result.consumesAction()) {
                client.player.swing(InteractionHand.OFF_HAND);
                return true;
            }
        }
        return false;
    }

    private PlaceCandidate findPlaceable(Minecraft client, BlockPos targetAir) {
        
        BlockPos[] offsets = {
                targetAir.below(),
                targetAir.north(), targetAir.south(), targetAir.east(), targetAir.west(),
                targetAir.north().east(), targetAir.north().west(),
                targetAir.south().east(), targetAir.south().west(),
                targetAir.below().north(), targetAir.below().south(),
                targetAir.below().east(), targetAir.below().west(),
                targetAir.above()
        };
        for (BlockPos support : offsets) {
            BlockState state = client.level.getBlockState(support);
            if (state.isAir() || !state.isSolidRender()) {
                continue;
            }
            Direction face = faceToward(support, targetAir);
            BlockPos placePos = support.relative(face);
            if (!client.level.getBlockState(placePos).canBeReplaced()) {
                continue;
            }
            
            if (client.player.distanceToSqr(Vec3.atCenterOf(placePos)) > 36.0) {
                continue;
            }
            return new PlaceCandidate(support, face);
        }
        return null;
    }

    private static Direction faceToward(BlockPos from, BlockPos to) {
        int dx = Integer.signum(to.getX() - from.getX());
        int dy = Integer.signum(to.getY() - from.getY());
        int dz = Integer.signum(to.getZ() - from.getZ());
        if (Math.abs(to.getY() - from.getY()) >= Math.abs(to.getX() - from.getX())
                && Math.abs(to.getY() - from.getY()) >= Math.abs(to.getZ() - from.getZ())) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }
        if (Math.abs(to.getX() - from.getX()) >= Math.abs(to.getZ() - from.getZ())) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void lookAt(Minecraft client, Vec3 point) {
        Vec3 eye = client.player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        client.player.setYRot(yaw);
        client.player.setXRot(net.minecraft.util.Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private static boolean isHoldingBlock(Minecraft client) {
        return client.player.getMainHandItem().getItem() instanceof BlockItem
                || client.player.getOffhandItem().getItem() instanceof BlockItem;
    }

    private record PlaceCandidate(BlockPos support, Direction face) {
    }
}
