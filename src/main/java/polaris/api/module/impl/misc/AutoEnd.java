package polaris.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;
import polaris.utils.network.Network;
import polaris.utils.player.BaritoneMovementHelper;
import polaris.utils.string.chat.ChatMessage;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoEnd extends Module {

    private enum State {
        BOOT,
        TP_STAGE_CMD,
        TP_STAGE_WAIT,
        FIND_CONSUMABLES,
        OPEN_CONSUMABLES,
        LOOT_CONSUMABLES,
        TP_END_CMD,
        TP_END_WAIT,

        FARM_LOOP,
        GO_POT,
        BREAK_POT,
        PICKUP_WAIT,
        GO_SUSPICIOUS,
        BRUSH_SUSPICIOUS,
        CHECK_LOOT,
        WALK_AWAY,
        WAIT_PVP,
        TP_DEPOSIT_CMD,
        TP_DEPOSIT_WAIT,
        FIND_STORAGE,
        OPEN_STORAGE,
        DEPOSIT,
        DEATH_RECOVER
    }

    private final StringSetting stageCmd = register(new StringSetting(
            "Команда stash", "Телепорт на stash/stage.", "clan home stage", 64));
    private final StringSetting endCmd = register(new StringSetting(
            "Команда end", "Телепорт в энд.", "home end", 64));
    private final StringSetting consumableSign = register(new StringSetting(
            "Табличка расходники", "Текст на табличке у сундука с едой/инвизом.", "расходники", 48));
    private final StringSetting storageSign = register(new StringSetting(
            "Табличка склад", "Текст на табличке у сундука для сдачи лута.", "склад", 48));
    private final NumberSetting tpDelay = register(new NumberSetting(
            "TP delay (с)", "Ожидание после /команды (8с).", 8.0, 3.0, 20.0, 0.5));
    private final NumberSetting foodCount = register(new NumberSetting(
            "Еда (шт)", "Сколько еды брать из «расходники».", 16.0, 0.0, 64.0, 1.0));
    private final NumberSetting invisCount = register(new NumberSetting(
            "Инвиз (шт)", "Сколько зелий невидимости брать (потом выпить 1).", 2.0, 1.0, 16.0, 1.0));
    private final NumberSetting scanRange = register(new NumberSetting(
            "Скан range", "Поиск ваз / suspicious sand.", 48.0, 16.0, 96.0, 4.0));
    private final NumberSetting fullRows = register(new NumberSetting(
            "Строк лута", "Если заполнено > N*9 слотов ресурсами → deposit.", 2.0, 1.0, 4.0, 1.0));
    private final NumberSetting pvpWalk = register(new NumberSetting(
            "Отход PVP (б)", "Отойти на N блоков пока висит ПВП.", 40.0, 10.0, 80.0, 5.0));
    private final NumberSetting actionDelay = register(new NumberSetting(
            "Клик delay (мс)", "Задержка кликов по сундуку.", 120.0, 40.0, 400.0, 10.0));
    private final BooleanSetting useBaritone = register(new BooleanSetting(
            "Baritone", "Ходьба к вазам / sand / отход.", true));
    private final BooleanSetting hotbarToInv = register(new BooleanSetting(
            "Хотбар → инв", "Сразу перекладывать ресурсы с хотбара в инвентарь.", true));
    private final BooleanSetting notify = register(new BooleanSetting(
            "Чат", "Логи AutoEnd.", true));

    private State state = State.BOOT;
    private long stateEnterMs;
    private long lastActionMs;
    private long lastHotbarTidyMs;
    private long tpSentMs;
    private BlockPos targetPos;
    private BlockPos chestPos;
    private int takenFood;
    private int takenInvis;
    private boolean hadBrush;
    private boolean brushedThisCycle;
    private boolean sawPvpAfterBrush;
    private int breakTicks;
    private int stallTicks;
    private double walkAwayX, walkAwayZ;
    private boolean drinkingInvis;
    private long drinkStartMs;

    private boolean deathPending;

    private boolean tidyingHotbar;
    private State resumeAfterTidy;
    private long lastWanderMs;

    public AutoEnd() {
        super("AutoEnd",
                "Stash «расходники»→инвиз→end→лут→stash «склад».",
                ModuleCategory.MISC);
    }

    @Override
    protected void onEnable() {
        if (!BaritoneMovementHelper.isAvailable() && useBaritone.getValue()) {
            log("§eBaritone не найден — ходьба к сундукам/вазам хуже.");
        }
        resetCycle(true);
        setState(State.TP_STAGE_CMD);
        log("§aAutoEnd ON §7· расходники=«" + consumableSign.getValue()
                + "» · склад=«" + storageSign.getValue() + "»");
    }

    @Override
    protected void onDisable() {
        BaritoneMovementHelper.cancel();
        closeScreen();
        if (mc != null && mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
        drinkingInvis = false;
        state = State.BOOT;
        log("§cAutoEnd OFF");
    }

    private void resetCycle(boolean full) {
        BaritoneMovementHelper.cancel();
        targetPos = null;
        chestPos = null;
        takenFood = 0;
        takenInvis = 0;
        hadBrush = false;
        brushedThisCycle = false;
        sawPvpAfterBrush = false;
        breakTicks = 0;
        stallTicks = 0;
        drinkingInvis = false;
        drinkStartMs = 0;
        if (full) {
            lastActionMs = 0;
        }
    }

    private void setState(State next) {
        if (state != next && notify.getValue()) {
            if (next == State.TP_STAGE_CMD || next == State.TP_END_CMD
                    || next == State.TP_DEPOSIT_CMD || next == State.DEATH_RECOVER
                    || next == State.WAIT_PVP || next == State.DEPOSIT
                    || next == State.FIND_CONSUMABLES || next == State.FIND_STORAGE
                    || next == State.FARM_LOOP || next == State.TP_END_WAIT) {
                log("§7→ §f" + next.name());
            }
        }
        state = next;
        stateEnterMs = System.currentTimeMillis();
        stallTicks = 0;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Pre event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }

        if (mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen) {
            deathPending = true;
            if (state != State.DEATH_RECOVER) {
                BaritoneMovementHelper.cancel();
                setState(State.DEATH_RECOVER);
            }
        }

        Network.tick();

        if (state == State.TP_END_WAIT) {
            tickDrinkDuringEndTp(mc);
        }

        if (hotbarToInv.getValue() && shouldTidyHotbar(state)) {
            if (hotbarHasLoot(mc)) {
                if (!tidyingHotbar) {
                    tidyingHotbar = true;
                    resumeAfterTidy = state;
                    BaritoneMovementHelper.cancel();
                }
                boolean done = tidyHotbarToInventoryAll(mc);
                if (!done) {
                    holdEmptyHandWhileFarming(mc);
                    return;
                }
                tidyingHotbar = false;

                if (resumeAfterTidy == State.GO_POT || resumeAfterTidy == State.GO_SUSPICIOUS) {
                    setState(resumeAfterTidy);
                } else if (isFarmState(resumeAfterTidy)) {
                    setState(State.FARM_LOOP);
                }
                resumeAfterTidy = null;
            } else {
                tidyingHotbar = false;
            }
        }

        if (state != State.TP_END_WAIT && state != State.BRUSH_SUSPICIOUS) {
            holdEmptyHandWhileFarming(mc);
        }

        switch (state) {
            case BOOT -> setState(State.TP_STAGE_CMD);
            case DEATH_RECOVER -> tickDeath(mc);
            case TP_STAGE_CMD -> sendTp(mc, stageCmd.getValue(), State.TP_STAGE_WAIT);
            case TP_STAGE_WAIT -> waitTp(State.FIND_CONSUMABLES);
            case FIND_CONSUMABLES -> findLabeledChest(mc, consumableSign.getValue(), State.OPEN_CONSUMABLES, true);
            case OPEN_CONSUMABLES -> openChest(mc, State.LOOT_CONSUMABLES, true);
            case LOOT_CONSUMABLES -> lootConsumables(mc);
            case TP_END_CMD -> {
                drinkingInvis = false;
                drinkStartMs = 0;
                sendTp(mc, endCmd.getValue(), State.TP_END_WAIT);
            }
            case TP_END_WAIT -> waitTpEnd(mc);
            case FARM_LOOP -> farmLoop(mc);
            case GO_POT -> goToTarget(mc, State.BREAK_POT);
            case BREAK_POT -> breakPot(mc);
            case PICKUP_WAIT -> pickupWait(mc);
            case GO_SUSPICIOUS -> goToTarget(mc, State.BRUSH_SUSPICIOUS);
            case BRUSH_SUSPICIOUS -> brushSuspicious(mc);
            case CHECK_LOOT -> checkLoot(mc);
            case WALK_AWAY -> walkAway(mc);
            case WAIT_PVP -> waitPvp(mc);
            case TP_DEPOSIT_CMD -> sendTp(mc, stageCmd.getValue(), State.TP_DEPOSIT_WAIT);
            case TP_DEPOSIT_WAIT -> waitTp(State.FIND_STORAGE);
            case FIND_STORAGE -> findLabeledChest(mc, storageSign.getValue(), State.OPEN_STORAGE, false);
            case OPEN_STORAGE -> openChest(mc, State.DEPOSIT, false);
            case DEPOSIT -> deposit(mc);
            default -> setState(State.TP_STAGE_CMD);
        }
    }

    private static boolean shouldTidyHotbar(State s) {
        return s == State.FARM_LOOP || s == State.GO_POT
                || s == State.BREAK_POT || s == State.PICKUP_WAIT
                || s == State.GO_SUSPICIOUS || s == State.WALK_AWAY || s == State.WAIT_PVP
                || s == State.CHECK_LOOT;
    }

    private static boolean isFarmState(State s) {
        return s == State.FARM_LOOP || s == State.GO_POT
                || s == State.BREAK_POT || s == State.PICKUP_WAIT
                || s == State.GO_SUSPICIOUS || s == State.BRUSH_SUSPICIOUS;
    }

    private void tickDeath(Minecraft mc) {
        if (mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen) {

            try {
                mc.player.respawn();
                mc.setScreen(null);
            } catch (Throwable ignored) {
            }
            try {
                if (mc.getConnection() != null) {
                    mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                            net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                }
            } catch (Throwable ignored) {
            }
            deathPending = true;
            return;
        }

        if (deathPending || state == State.DEATH_RECOVER) {
            deathPending = false;
            resetCycle(true);
            log("§cСмерть → сразу /" + stageCmd.getValue());
            sendTp(mc, stageCmd.getValue(), State.TP_STAGE_WAIT);
        }
    }

    private void sendTp(Minecraft mc, String cmd, State waitState) {
        if (mc.player.connection == null) {
            return;
        }
        closeScreen();
        BaritoneMovementHelper.cancel();
        String c = cmd == null ? "" : cmd.trim();
        if (c.startsWith("/")) {
            c = c.substring(1);
        }
        try {
            mc.player.connection.sendCommand(c);
        } catch (Throwable t) {
            try {
                mc.player.connection.sendChat("/" + c);
            } catch (Throwable ignored) {
            }
        }
        tpSentMs = System.currentTimeMillis();
        setState(waitState);
        log("§b/" + c + " §7… +" + tpDelay.getValue().intValue() + "s");
    }

    private void waitTp(State next) {
        long need = (long) (tpDelay.getValue() * 1000.0);
        if (System.currentTimeMillis() - tpSentMs >= need) {
            setState(next);
        }
    }

    private void findLabeledChest(Minecraft mc, String signKey, State openState, boolean consumables) {
        BlockPos found = findChestWithSign(mc, signKey);
        if (found != null) {
            chestPos = found;
            targetPos = found;

            if (useBaritone.getValue() && mc.player.distanceToSqr(Vec3.atCenterOf(found)) > 9) {
                BaritoneMovementHelper.goNear(found, 2);
            }
            setState(openState);
            log("§aСундук «" + signKey + "» §7@ §f"
                    + found.getX() + " " + found.getY() + " " + found.getZ());
            return;
        }
        stallTicks++;

        if (useBaritone.getValue() && stallTicks % 50 == 20) {
            BlockPos o = mc.player.blockPosition();
            BaritoneMovementHelper.goXZ(
                    o.getX() + ThreadLocalRandom.current().nextInt(-8, 9),
                    o.getZ() + ThreadLocalRandom.current().nextInt(-8, 9));
        }
        if (stallTicks > 160) {
            log("§cСундук с табличкой «" + signKey + "» не найден — снова /"
                    + stageCmd.getValue());
            setState(State.TP_STAGE_CMD);
        }
    }

    private BlockPos findChestWithSign(Minecraft mc, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = normalizeSign(key);
        BlockPos origin = mc.player.blockPosition();
        int r = 32;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -6; dy <= 8; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!mc.level.hasChunkAt(p)) {
                        continue;
                    }
                    Block block = mc.level.getBlockState(p).getBlock();
                    boolean isChest = block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                            || block == Blocks.BARREL
                            || mc.level.getBlockEntity(p) instanceof ChestBlockEntity;
                    if (!isChest) {
                        continue;
                    }
                    String sign = normalizeSign(nearbySignText(mc, p));
                    if (sign.contains(k) || matchesSignVariants(sign, k)) {
                        double d = p.distSqr(origin);
                        if (d < bestD) {
                            bestD = d;
                            best = p.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static String normalizeSign(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§.", "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean matchesSignVariants(String sign, String key) {
        if (sign.contains(key)) {
            return true;
        }
        if (key.startsWith("расход") && (sign.contains("расходник") || sign.contains("расходн"))) {
            return true;
        }
        if (key.startsWith("склад") && (sign.contains("склад") || sign.contains("ресурс"))) {
            return true;
        }
        return false;
    }

    private String nearbySignText(Minecraft mc, BlockPos chest) {
        StringBuilder sb = new StringBuilder();
        for (BlockPos p : BlockPos.betweenClosed(chest.offset(-2, -2, -2), chest.offset(2, 2, 2))) {
            if (mc.level.getBlockEntity(p) instanceof SignBlockEntity sign) {
                sb.append(readSign(sign)).append(' ');
            }
        }
        return sb.toString().replaceAll("§.", "");
    }

    private static String readSign(SignBlockEntity sign) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Component line : sign.getFrontText().getMessages(false)) {
                sb.append(line.getString()).append(' ');
            }
            for (Component line : sign.getBackText().getMessages(false)) {
                sb.append(line.getString()).append(' ');
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    private void openChest(Minecraft mc, State next, boolean consumables) {
        if (chestPos == null) {
            setState(consumables ? State.FIND_CONSUMABLES : State.FIND_STORAGE);
            return;
        }
        if (mc.player.containerMenu instanceof ChestMenu) {
            BaritoneMovementHelper.cancel();
            setState(next);
            return;
        }
        double dist = mc.player.distanceToSqr(Vec3.atCenterOf(chestPos));
        if (useBaritone.getValue() && dist > 12) {
            if (!BaritoneMovementHelper.isBusy() || stallTicks % 30 == 0) {
                BaritoneMovementHelper.goNear(chestPos, 2);
            }
            stallTicks++;
            return;
        }
        if (!actionReady()) {
            return;
        }
        BaritoneMovementHelper.cancel();
        interactBlock(mc, chestPos);
        lastActionMs = System.currentTimeMillis();
        if (mc.player.containerMenu instanceof ChestMenu) {
            setState(next);
            return;
        }
        if (System.currentTimeMillis() - stateEnterMs > 10000) {
            log("§eНе открылся сундук «"
                    + (consumables ? consumableSign.getValue() : storageSign.getValue())
                    + "» — ищем снова");
            setState(consumables ? State.FIND_CONSUMABLES : State.FIND_STORAGE);
        }
    }

    private void lootConsumables(Minecraft mc) {

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.slots.size() <= 36 || mc.screen == null) {
            setState(State.OPEN_CONSUMABLES);
            return;
        }
        int needFood = foodCount.getValue().intValue();
        int needInvis = invisCount.getValue().intValue();

        int haveFood = countInPlayer(mc, this::isFoodItem);
        int haveInvis = countInPlayer(mc, this::isInvisPotion);
        int wantFood = Math.max(0, needFood - haveFood);
        int wantInvis = Math.max(0, needInvis - haveInvis);

        if (wantFood <= 0 && wantInvis <= 0) {
            closeScreen();
            log("§aРасходники взяты → TP end (инвиз пьём во время TP)");
            drinkingInvis = false;
            setState(State.TP_END_CMD);
            return;
        }
        if (!actionReady()) {
            return;
        }

        int chestSlots = menu.slots.size() - 36;
        if (chestSlots < 1) {
            chestSlots = menu instanceof ChestMenu cm ? cm.getRowCount() * 9 : 27;
        }
        for (int i = 0; i < chestSlots; i++) {
            Slot slot = menu.getSlot(i);
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            ItemStack st = slot.getItem();
            boolean take = false;

            if (wantInvis > 0 && isInvisPotion(st)) {
                take = true;
            } else if (wantFood > 0 && isFoodItem(st)) {
                take = true;
            }
            if (!take) {
                continue;
            }
            mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
            lastActionMs = System.currentTimeMillis();
            if (notify.getValue()) {
                log("§7+ §f" + st.getHoverName().getString());
            }
            return;
        }

        closeScreen();
        log("§eВ «расходники» пусто/не распознано → TP end");
        drinkingInvis = false;
        setState(State.TP_END_CMD);
    }

    private void waitTpEnd(Minecraft mc) {
        long need = (long) (tpDelay.getValue() * 1000.0);
        boolean timeUp = System.currentTimeMillis() - tpSentMs >= need;
        if (timeUp) {
            if (mc.options != null) {
                mc.options.keyUse.setDown(false);
            }
            drinkingInvis = false;
            setState(State.FARM_LOOP);
            log("§aВ энде → постоянный обход + скан");
        }
    }

    private void tickDrinkDuringEndTp(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        try {
            if (mc.player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                if (mc.options != null) {
                    mc.options.keyUse.setDown(false);
                }
                drinkingInvis = false;
                return;
            }
        } catch (Throwable ignored) {
        }

        if (drinkingInvis) {
            if (mc.player.isUsingItem()) {
                mc.options.keyUse.setDown(true);
                return;
            }
            if (System.currentTimeMillis() - drinkStartMs < 1800) {
                startDrinkUse(mc);
            } else {
                mc.options.keyUse.setDown(false);
                drinkingInvis = false;
            }
            return;
        }

        int slot = findInvisHotbar(mc);
        if (slot < 0) {
            int inv = findInvisInventory(mc);
            if (inv >= 0) {
                int containerFrom = inv < 9 ? inv + 36 : inv;
                int emptyHot = findEmptyHotbarSlot(mc);
                int hotBtn = emptyHot >= 0 ? emptyHot : 0;
                try {
                    mc.gameMode.handleInventoryMouseClick(
                            mc.player.inventoryMenu.containerId, containerFrom, hotBtn, ClickType.SWAP, mc.player);
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        startDrinkUse(mc);
    }

    private void startDrinkUse(Minecraft mc) {
        int slot = findInvisHotbar(mc);
        if (slot < 0 || mc.gameMode == null) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.options.keyUse.setDown(true);
        drinkingInvis = true;
        drinkStartMs = System.currentTimeMillis();
        lastActionMs = System.currentTimeMillis();
        if (!drinkingInvis || System.currentTimeMillis() - stateEnterMs < 500) {
            log("§dПьём инвиз во время TP…");
        }
    }

    private int findInvisHotbar(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (isInvisPotion(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findInvisInventory(Minecraft mc) {
        for (int i = 9; i < 36; i++) {
            if (isInvisPotion(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private void farmLoop(Minecraft mc) {

        if (resourceFilledSlots(mc) > fullRows.getValue().intValue() * 9) {
            BaritoneMovementHelper.cancel();
            setState(State.TP_DEPOSIT_CMD);
            return;
        }

        if (hasBrush(mc)) {
            hadBrush = true;
            BlockPos sand = findNearest(mc, this::isSuspicious);
            if (sand != null) {
                targetPos = sand;
                setState(State.GO_SUSPICIOUS);
                return;
            }
        }

        BlockPos pot = findNearest(mc, this::isDecoratedPot);
        if (pot != null) {
            targetPos = pot;
            setState(State.GO_POT);
            return;
        }

        ensureWandering(mc);
        stallTicks++;
    }

    private void ensureWandering(Minecraft mc) {
        if (!useBaritone.getValue()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean busy = BaritoneMovementHelper.isBusy();

        if (!busy || now - lastWanderMs > 12_000L) {
            BlockPos o = mc.player.blockPosition();

            int dx = ThreadLocalRandom.current().nextInt(-80, 81);
            int dz = ThreadLocalRandom.current().nextInt(-80, 81);
            if (Math.abs(dx) < 20 && Math.abs(dz) < 20) {
                dx = 30 + ThreadLocalRandom.current().nextInt(40);
                dz = ThreadLocalRandom.current().nextBoolean() ? dz : -dz;
            }
            BaritoneMovementHelper.goXZ(o.getX() + dx, o.getZ() + dz);
            lastWanderMs = now;
        }
    }

    private void goToTarget(Minecraft mc, State arrived) {
        if (targetPos == null) {
            setState(State.FARM_LOOP);
            return;
        }

        if (mc.level.getBlockState(targetPos).isAir()) {
            if (arrived == State.BREAK_POT) {
                setState(State.PICKUP_WAIT);
            } else {
                setState(State.FARM_LOOP);
            }
            return;
        }

        boolean onPot = isStandingOn(mc, targetPos);
        double dist = mc.player.distanceToSqr(Vec3.atCenterOf(targetPos));

        boolean arrivedOk = arrived == State.BREAK_POT
                ? onPot
                : dist <= 9.0;

        if (arrivedOk) {
            BaritoneMovementHelper.cancel();
            setState(arrived);
            return;
        }

        if (useBaritone.getValue()) {

            int near = arrived == State.BREAK_POT ? 0 : 1;
            if (!BaritoneMovementHelper.isBusy() || stallTicks % 25 == 0) {
                BaritoneMovementHelper.goNear(targetPos, near);
            }
        } else {

            faceBlock(mc, targetPos);
        }
        stallTicks++;

        if (stallTicks > 350) {
            targetPos = null;
            setState(State.FARM_LOOP);
        }
    }

    private boolean isStandingOn(Minecraft mc, BlockPos pot) {
        BlockPos feet = mc.player.blockPosition();
        if (feet.getX() == pot.getX() && feet.getZ() == pot.getZ()) {
            return Math.abs(feet.getY() - pot.getY()) <= 1;
        }

        return feet.getX() == pot.getX() && feet.getZ() == pot.getZ()
                && feet.getY() == pot.getY() + 1;
    }

    private void breakPot(Minecraft mc) {
        if (targetPos == null || mc.level.getBlockState(targetPos).isAir()) {
            setState(State.PICKUP_WAIT);
            return;
        }

        if (!isStandingOn(mc, targetPos)) {
            setState(State.GO_POT);
            return;
        }
        BaritoneMovementHelper.cancel();

        faceBlock(mc, targetPos);
        if (mc.gameMode != null) {
            Direction face = Direction.UP;

            if (breakTicks == 0) {
                mc.gameMode.startDestroyBlock(targetPos, face);
            }
            mc.gameMode.continueDestroyBlock(targetPos, face);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        breakTicks++;
        if (mc.level.getBlockState(targetPos).isAir()) {
            breakTicks = 0;
            setState(State.PICKUP_WAIT);
            return;
        }

        if (breakTicks > 0 && breakTicks % 40 == 0 && mc.gameMode != null) {
            mc.gameMode.startDestroyBlock(targetPos, Direction.UP);
        }
        if (breakTicks > 120) {
            breakTicks = 0;
            setState(State.PICKUP_WAIT);
        }
    }

    private void pickupWait(Minecraft mc) {

        if (System.currentTimeMillis() - stateEnterMs < 600) {
            return;
        }
        boolean brushNow = hasBrush(mc);
        if (brushNow) {
            hadBrush = true;
            BlockPos sand = findNearest(mc, this::isSuspicious);
            if (sand != null) {
                targetPos = sand;
                setState(State.GO_SUSPICIOUS);
                return;
            }
        }

        setState(State.FARM_LOOP);
    }

    private void brushSuspicious(Minecraft mc) {
        if (!hasBrush(mc)) {

            brushedThisCycle = true;
            if (Network.isPvp()) {
                sawPvpAfterBrush = true;
            }
            targetPos = null;
            setState(State.CHECK_LOOT);
            return;
        }
        if (targetPos == null || !isSuspicious(mc.level.getBlockState(targetPos))) {
            BlockPos sand = findNearest(mc, this::isSuspicious);
            if (sand == null) {
                setState(State.CHECK_LOOT);
                return;
            }
            targetPos = sand;
            setState(State.GO_SUSPICIOUS);
            return;
        }
        if (mc.player.distanceToSqr(Vec3.atCenterOf(targetPos)) > 16) {
            setState(State.GO_SUSPICIOUS);
            return;
        }
        BaritoneMovementHelper.cancel();

        int brushSlot = findBrushHotbar(mc);
        if (brushSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(brushSlot);
        }
        faceBlock(mc, targetPos);

        if (mc.gameMode != null && actionReady()) {
            Vec3 hit = Vec3.atCenterOf(targetPos).add(0, 0.5, 0);
            BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, targetPos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            mc.player.swing(InteractionHand.MAIN_HAND);
            lastActionMs = System.currentTimeMillis();
            brushedThisCycle = true;
        }

        if (mc.gameMode != null && breakTicks % 5 == 0) {
            mc.gameMode.startDestroyBlock(targetPos, Direction.UP);
            mc.gameMode.continueDestroyBlock(targetPos, Direction.UP);
        }
        breakTicks++;
        if (Network.isPvp()) {
            sawPvpAfterBrush = true;
        }
        if (mc.level.getBlockState(targetPos).isAir()) {

            BlockPos next = findNearest(mc, this::isSuspicious);
            if (next != null && hasBrush(mc)) {
                targetPos = next;
                breakTicks = 0;
                setState(State.GO_SUSPICIOUS);
            } else {
                setState(State.CHECK_LOOT);
            }
        }
        if (breakTicks > 600) {
            setState(State.CHECK_LOOT);
        }
    }

    private void checkLoot(Minecraft mc) {
        if (Network.isPvp()) {
            sawPvpAfterBrush = true;
        }
        boolean full = resourceFilledSlots(mc) > fullRows.getValue().intValue() * 9;
        boolean afterBrush = brushedThisCycle || (hadBrush && !hasBrush(mc));

        if (afterBrush && (Network.isPvp() || sawPvpAfterBrush)) {

            prepareWalkAway(mc);
            setState(State.WALK_AWAY);
            return;
        }
        if (full) {
            setState(State.TP_DEPOSIT_CMD);
            return;
        }

        setState(State.FARM_LOOP);
    }

    private void prepareWalkAway(Minecraft mc) {
        double yaw = Math.toRadians(mc.player.getYRot() + ThreadLocalRandom.current().nextInt(-90, 91));
        double dist = pvpWalk.getValue();
        walkAwayX = mc.player.getX() + (-Math.sin(yaw) * dist);
        walkAwayZ = mc.player.getZ() + (Math.cos(yaw) * dist);
        if (useBaritone.getValue()) {
            BaritoneMovementHelper.goXZ((int) walkAwayX, (int) walkAwayZ);
        }
    }

    private void walkAway(Minecraft mc) {
        if (useBaritone.getValue() && !BaritoneMovementHelper.isBusy()) {
            BaritoneMovementHelper.goXZ((int) walkAwayX, (int) walkAwayZ);
        }
        double dx = mc.player.getX() - walkAwayX;
        double dz = mc.player.getZ() - walkAwayZ;
        if (dx * dx + dz * dz < 36 || System.currentTimeMillis() - stateEnterMs > 15000) {
            BaritoneMovementHelper.cancel();
            setState(State.WAIT_PVP);
        }
    }

    private void waitPvp(Minecraft mc) {
        Network.tick();
        if (!Network.isPvp()) {

            if (resourceFilledSlots(mc) > 0) {
                setState(State.TP_DEPOSIT_CMD);
            } else {
                setState(State.FARM_LOOP);
            }
            return;
        }

        if (System.currentTimeMillis() - stateEnterMs > 180_000) {

            setState(State.TP_DEPOSIT_CMD);
        }
    }

    private void deposit(Minecraft mc) {
        if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
            setState(State.OPEN_STORAGE);
            return;
        }
        if (!actionReady()) {
            return;
        }

        int chestSize = menu.getRowCount() * 9;

        boolean moved = false;
        for (int i = chestSize; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            ItemStack st = slot.getItem();
            if (isKeepItem(st)) {
                continue;
            }
            if (isResourceLoot(st)) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                lastActionMs = System.currentTimeMillis();
                moved = true;
                break;
            }
        }
        if (!moved) {
            closeScreen();
            if (mc.options != null) {
                mc.options.keyUse.setDown(false);
            }
            resetCycle(false);
            log("§aСклад: лут сдан → снова end");

            setState(State.FIND_CONSUMABLES);
        }
    }

    private boolean actionReady() {
        return System.currentTimeMillis() - lastActionMs >= actionDelay.getValue().longValue();
    }

    private void holdEmptyHandWhileFarming(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (state == State.BRUSH_SUSPICIOUS || state == State.TP_END_WAIT) {
            return;
        }
        if (!isFarmState(state) && state != State.WALK_AWAY && state != State.WAIT_PVP
                && state != State.CHECK_LOOT) {
            return;
        }

        if (mc.player.containerMenu instanceof ChestMenu) {
            return;
        }

        int empty = findEmptyHotbarSlot(mc);
        if (empty < 0) {

            return;
        }
        if (mc.player.getInventory().getSelectedSlot() != empty) {
            mc.player.getInventory().setSelectedSlot(empty);
        }
    }

    private static int findEmptyHotbarSlot(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean hotbarHasLoot(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        for (int h = 0; h < 9; h++) {
            ItemStack stack = mc.player.getInventory().getItem(h);
            if (!stack.isEmpty() && !isKeepItem(stack) && isResourceLoot(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean tidyHotbarToInventoryAll(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null) {
            return true;
        }
        if (mc.player.containerMenu instanceof ChestMenu) {
            return true;
        }
        if (System.currentTimeMillis() - lastHotbarTidyMs < 50L) {
            return false;
        }

        var inv = mc.player.getInventory();
        boolean moved = false;
        for (int h = 0; h < 9; h++) {
            ItemStack stack = inv.getItem(h);
            if (stack.isEmpty() || isKeepItem(stack) || !isResourceLoot(stack)) {
                continue;
            }
            int dest = findInventoryDest(mc, stack);
            if (dest < 0) {

                return true;
            }
            int fromMenu = 36 + h;
            int toMenu = dest;
            int sync = mc.player.inventoryMenu.containerId;
            try {
                mc.gameMode.handleInventoryMouseClick(sync, fromMenu, 0, ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(sync, toMenu, 0, ClickType.PICKUP, mc.player);
                if (!mc.player.containerMenu.getCarried().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(sync, fromMenu, 0, ClickType.PICKUP, mc.player);
                }
            } catch (Throwable ignored) {
            }
            lastHotbarTidyMs = System.currentTimeMillis();
            moved = true;
            break;
        }
        if (!moved) {
            return !hotbarHasLoot(mc);
        }
        return !hotbarHasLoot(mc);
    }

    private int findInventoryDest(Minecraft mc, ItemStack stack) {
        var inv = mc.player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack cur = inv.getItem(i);
            if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack)
                    && cur.getCount() < cur.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void interactBlock(Minecraft mc, BlockPos pos) {
        if (mc.gameMode == null) {
            return;
        }
        faceBlock(mc, pos);
        Vec3 hit = Vec3.atCenterOf(pos);
        BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, pos, false);
        InteractionResult r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        if (!r.consumesAction()) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.OFF_HAND, bhr);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void faceBlock(Minecraft mc, BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
    }

    private void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof AbstractContainerScreen) {
            mc.player.closeContainer();
        }
    }

    private BlockPos findNearest(Minecraft mc, java.util.function.Predicate<BlockState> pred) {
        BlockPos origin = mc.player.blockPosition();
        int r = scanRange.getValue().intValue();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        int yMin = Math.max(mc.level.getMinY(), origin.getY() - 16);
        int yMax = Math.min(origin.getY() + 24, origin.getY() + 48);
        for (int x = -r; x <= r; x += 1) {
            for (int z = -r; z <= r; z += 1) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                if (!mc.level.getChunkSource().hasChunk(wx >> 4, wz >> 4)) {
                    continue;
                }
                for (int y = yMin; y <= yMax; y++) {
                    BlockPos p = new BlockPos(wx, y, wz);
                    BlockState st = mc.level.getBlockState(p);
                    if (!pred.test(st)) {
                        continue;
                    }
                    double d = p.distSqr(origin);
                    if (d < bestD) {
                        bestD = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    private boolean isDecoratedPot(BlockState st) {
        return st.is(Blocks.DECORATED_POT);
    }

    private boolean isSuspicious(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.SUSPICIOUS_SAND || b == Blocks.SUSPICIOUS_GRAVEL;
    }

    private boolean hasBrush(Minecraft mc) {
        return findBrushHotbar(mc) >= 0 || countInPlayer(mc, s -> s.is(Items.BRUSH)) > 0;
    }

    private int findBrushHotbar(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.BRUSH)) {
                return i;
            }
        }
        return -1;
    }

    private int countInPlayer(Minecraft mc, java.util.function.Predicate<ItemStack> pred) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (pred.test(s)) {
                n += s.getCount();
            }
        }
        ItemStack off = mc.player.getOffhandItem();
        if (pred.test(off)) {
            n += off.getCount();
        }
        return n;
    }

    private int resourceFilledSlots(Minecraft mc) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty() || isKeepItem(s)) {
                continue;
            }
            if (isResourceLoot(s)) {
                n++;
            }
        }
        return n;
    }

    private boolean isKeepItem(ItemStack s) {
        if (s.isEmpty()) {
            return true;
        }
        if (s.is(Items.BRUSH)) {
            return true;
        }
        if (isFoodItem(s) || isInvisPotion(s)) {
            return true;
        }

        Item it = s.getItem();
        return it == Items.NETHERITE_PICKAXE || it == Items.DIAMOND_PICKAXE
                || it == Items.IRON_PICKAXE || it == Items.TOTEM_OF_UNDYING
                || it == Items.ELYTRA || it == Items.FIREWORK_ROCKET;
    }

    private boolean isResourceLoot(ItemStack s) {
        if (s.isEmpty() || isKeepItem(s)) {
            return false;
        }

        String id = s.getItem().toString().toLowerCase(Locale.ROOT);
        String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (id.contains("sherd") || id.contains("pottery") || id.contains("template")
                || id.contains("sniffer") || id.contains("torchflower") || id.contains("pitcher")
                || name.contains("череп") || name.contains("шард") || name.contains("фрагмент")
                || name.contains("артефакт") || name.contains("кист")) {
            return true;
        }

        return false;
    }

    private boolean isFoodItem(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        try {
            if (s.get(DataComponents.FOOD) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        Item it = s.getItem();
        return it == Items.COOKED_BEEF || it == Items.COOKED_PORKCHOP || it == Items.BREAD
                || it == Items.GOLDEN_CARROT || it == Items.COOKED_CHICKEN || it == Items.APPLE
                || it == Items.BAKED_POTATO || it == Items.COOKED_MUTTON;
    }

    private boolean isInvisPotion(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        String name = s.getHoverName().getString().toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("§.", "");
        String id = s.getItem().toString().toLowerCase(Locale.ROOT);

        if (name.contains("невид") || name.contains("invis") || name.contains("hide")
                || name.contains("stealth") || name.contains("ghost")
                || name.contains("невидим") || name.contains("inviz")) {
            return true;
        }
        if (id.contains("potion") && (name.contains("невид") || name.contains("invis"))) {
            return true;
        }

        Item it = s.getItem();
        boolean potionLike = it == Items.POTION || it == Items.SPLASH_POTION
                || it == Items.LINGERING_POTION;
        if (!potionLike) {
            return name.contains("зел") && name.contains("невид");
        }

        if (name.contains("water") || name.contains("вод") || name.contains("mundane")
                || name.contains("thick") || name.contains("awkward")) {
            return false;
        }

        try {
            PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
            if (pc != null) {
                String desc = pc.toString().toLowerCase(Locale.ROOT);
                if (desc.contains("invis")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return potionLike && it == Items.POTION;
    }

    private void log(String msg) {
        if (notify.getValue()) {
            ChatMessage.brandmessage(msg);
        }
    }
}
