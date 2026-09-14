package polaris.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.impl.StringSetting;
import polaris.utils.inventory.lookup.InventoryUtils;
import polaris.utils.player.BaritoneMovementHelper;
import polaris.utils.render.Render3D;
import polaris.utils.repository.way.WayRepository;
import polaris.utils.string.chat.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BaseFinder extends Module {

    private static final String SIGN_VILLAGER = "Жители";
    private static final String SIGN_SHULKER = "Шалкеры";
    private static final String SIGN_CHEST = "Сундуки";
    private static final String SIGN_SPAWNER = "Спавнеры";
    private static final String SIGN_BEACON = "Маяки";
    private static final String SIGN_PORTAL = "Портал-рамки";
    private static final String SIGN_HOPPER = "Воронки";
    private static final String SIGN_BOOKSHELF = "Книжные полки";
    private static final String SIGN_ENCHANT = "Стол зачарования";
    private static final String SIGN_BREWING = "Зельеварки";
    private static final String SIGN_FURNACE = "Печи";
    private static final String SIGN_CRAFT = "Верстаки";
    private static final String SIGN_ANVIL = "Наковальни";
    private static final String SIGN_UTILITY = "Точило/кузня/станок";
    private static final String SIGN_LECTERN = "Кафедры";

    private static final String[] ALL_SIGNS = {
            SIGN_VILLAGER, SIGN_SHULKER, SIGN_CHEST, SIGN_SPAWNER, SIGN_BEACON, SIGN_PORTAL,
            SIGN_HOPPER, SIGN_BOOKSHELF, SIGN_ENCHANT, SIGN_BREWING, SIGN_FURNACE,
            SIGN_CRAFT, SIGN_ANVIL, SIGN_UTILITY, SIGN_LECTERN
    };

    private final ModeSetting digMode = register(new ModeSetting(
            "Режим dig",
            "1 = GoalXZ path · 2 = Baritone #tunnel (прямой тоннель).",
            "1 GoalXZ", "1 GoalXZ", "2 Tunnel"));

    private final NumberSetting digDistance = register(new NumberSetting(
            "Дистанция копа", "GoalXZ / глубина tunnel (0 = бесконечный tunnel).",
            5000.0, 0.0, 30000.0, 100.0));
    private final NumberSetting repathEvery = register(new NumberSetting(
            "Repath (б)", "Режим 1: продлить GoalXZ. Режим 2: перезапуск tunnel.",
            120.0, 40.0, 400.0, 5.0));
    private final NumberSetting tunnelHeight = register(new NumberSetting(
            "Tunnel высота", "Высота #tunnel (обычно 2).", 2.0, 1.0, 6.0, 1.0));
    private final NumberSetting tunnelWidth = register(new NumberSetting(
            "Tunnel ширина", "Ширина #tunnel (обычно 1).", 1.0, 1.0, 5.0, 1.0));
    private final BooleanSetting snapCardinal = register(new BooleanSetting(
            "Только 90°", "При старте привязать dig к N/E/S/W — ровный тоннель.", true));
    private final BooleanSetting turnEnabled = register(new BooleanSetting(
            "Смена направления", "Поворачивать dig каждые N блоков.", false));
    private final NumberSetting turnEvery = register(new NumberSetting(
            "Поворот каждые", "Блоков до смены направления.",
            500.0, 50.0, 5000.0, 10.0));
    private final ModeSetting turnMode = register(new ModeSetting(
            "Тип поворота",
            "Куда крутить dig-yaw.",
            "Вправо 90", "Вправо 90", "Влево 90", "Разворот 180", "Змейка"));

    private final NumberSetting markerEvery = register(new NumberSetting(
            "Маркер каждые", "Ставить изумрудную руду каждые N блоков.",
            50.0, 10.0, 500.0, 5.0));
    private final BooleanSetting placeEmerald = register(new BooleanSetting(
            "Ставить изумруд", "Трейл из emerald ore по пути.", true));
    private final BooleanSetting digBreak = register(new BooleanSetting(
            "Baritone break", "Разрешить ломать блоки.", true));
    private final BooleanSetting digPlace = register(new BooleanSetting(
            "Baritone place", "Разрешить ставить блоки (мосты).", true));
    private final BooleanSetting smoothBaritone = register(new BooleanSetting(
            "Плавный Baritone", "Без parkour, спокойный repath, freeLook.", true));
    private final BooleanSetting avoidSoft = register(new BooleanSetting(
            "Обход земли/гравия", "Не копать dirt/gravel/sand — обходить.", true));
    private final BooleanSetting alwaysSprint = register(new BooleanSetting(
            "Всегда спринт", "Держать спринт пока можно бежать.", true));
    private final BooleanSetting autoEat = register(new BooleanSetting(
            "Авто-еда", "Еда в левой руке; есть когда нельзя спринт (hunger ≤ 6).", true));
    private final NumberSetting eatUntil = register(new NumberSetting(
            "Есть до", "Прекратить есть при hunger ≥ N.", 14.0, 8.0, 20.0, 1.0));

    private final BooleanSetting claimWay = register(new BooleanSetting(
            "Way по чату", "Метка при сообщениях о территории/привате.", true));
    private final StringSetting claimKeywords = register(new StringSetting(
            "Ключевые слова",
            "Через | — подстроки чата (lowercase).",
            "территория|занят|пересеч|приват|claim|protected|владе|нельзя строить|нельзя ломать|регион|rg |this land|already claimed|belongs to",
            512));
    private final StringSetting claimWayPrefix = register(new StringSetting(
            "Префикс приват", "Имена: приват, приват1, приват2…", "приват", 32));

    private final BooleanSetting scanSigns = register(new BooleanSetting(
            "Скан признаков", "Блоки/мобы баз → way (сундук, воронка, зельеварка…)", true));
    private final MultiModeSetting signTypes = register(new MultiModeSetting(
            "Признаки",
            "Что искать на базах.",
            ALL_SIGNS,
            SIGN_VILLAGER, SIGN_SHULKER, SIGN_CHEST, SIGN_SPAWNER, SIGN_BEACON,
            SIGN_HOPPER, SIGN_BOOKSHELF, SIGN_ENCHANT, SIGN_BREWING,
            SIGN_FURNACE, SIGN_CRAFT, SIGN_ANVIL, SIGN_UTILITY, SIGN_LECTERN));
    private final NumberSetting scanRange = register(new NumberSetting(
            "Скан range", "Радиус поиска признаков (чанки/BE + лёгкий скан).", 32.0, 12.0, 64.0, 4.0));
    private final NumberSetting scanInterval = register(new NumberSetting(
            "Скан интервал (т)", "Раз в N тиков сканировать (выше = меньше лагов).", 40.0, 10.0, 200.0, 5.0));
    private final BooleanSetting heavyScan = register(new BooleanSetting(
            "Тяжёлый скан", "Полный voxel (полки/верстак). Лагает — выкл. если FPS падает.", false));
    private final NumberSetting wayCooldown = register(new NumberSetting(
            "Way CD (с)", "Мин. дистанция/время между похожими метками.", 8.0, 2.0, 60.0, 1.0));
    private final NumberSetting wayMinDist = register(new NumberSetting(
            "Way min dist", "Не ставить way ближе N блоков к существующей.",
            40.0, 10.0, 200.0, 5.0));

    private final BooleanSetting notify = register(new BooleanSetting(
            "Чат", "Логи модуля.", true));
    private final BooleanSetting renderTrail = register(new BooleanSetting(
            "ESP маркеры", "Показывать последние emerald-точки.", true));
    private final BooleanSetting stopOnClaim = register(new BooleanSetting(
            "Стоп на claim", "Остановить dig при срабатывании claim-чата.", false));

    private float digYaw;
    private double lastSampleX, lastSampleZ;
    private double traveledSinceRepath;
    private double traveledSinceMarker;
    private double traveledSinceTurn;
    private double totalTraveled;
    private int tickCounter;
    private int selectedSlotBackup = -1;
    private long lastPlaceMs;
    private boolean wasPathing;

    private boolean snakeNextLeft = true;
    private boolean eating;
    private boolean pausedForFood;
    private long lastFoodSwapMs;

    private final List<BlockPos> recentMarkers = new ArrayList<>();
    private final Set<BlockPos> protectedMarkers = ConcurrentHashMap.newKeySet();
    private final Set<String> claimedWayKeys = ConcurrentHashMap.newKeySet();
    private final Set<Long> signedChunks = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> wayNameCounters = new HashMap<>();
    private long lastClaimWayMs;
    private long lastSignWayMs;
    private int busyStuckTicks;
    private double busyStuckTravelSnapshot;

    public BaseFinder() {
        super("BaseFinder",
                "Dig: GoalXZ или #tunnel · emerald/way · claim→приват1 · скан сундук1…",
                ModuleCategory.MISC);

        repathEvery.visibleWhen(() -> digMode.is("1 GoalXZ"));
        digDistance.visibleWhen(() -> digMode.is("1 GoalXZ") || digMode.is("2 Tunnel"));
        tunnelHeight.visibleWhen(() -> digMode.is("2 Tunnel"));
        tunnelWidth.visibleWhen(() -> digMode.is("2 Tunnel"));
        turnEvery.visibleWhen(turnEnabled::getValue);
        turnMode.visibleWhen(turnEnabled::getValue);
        eatUntil.visibleWhen(autoEat::getValue);
        claimKeywords.visibleWhen(claimWay::getValue);
        claimWayPrefix.visibleWhen(claimWay::getValue);
        signTypes.visibleWhen(scanSigns::getValue);
        scanRange.visibleWhen(scanSigns::getValue);
        scanInterval.visibleWhen(scanSigns::getValue);
        heavyScan.visibleWhen(scanSigns::getValue);
    }

    private boolean isTunnelMode() {
        return digMode.is("2 Tunnel");
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        if (!BaritoneMovementHelper.isAvailable()) {
            msg("§cBaritone не найден (libs/mods).");
            setEnabled(false);
            return;
        }

        digYaw = mc.player.getYRot();
        if (snapCardinal.getValue()) {
            digYaw = snapToCardinal(digYaw);
        }
        lastSampleX = mc.player.getX();
        lastSampleZ = mc.player.getZ();
        traveledSinceRepath = 0;
        traveledSinceMarker = 0;
        traveledSinceTurn = 0;
        totalTraveled = 0;
        tickCounter = 0;
        wasPathing = false;
        snakeNextLeft = true;
        recentMarkers.clear();
        protectedMarkers.clear();
        signedChunks.clear();
        wayNameCounters.clear();

        if (smoothBaritone.getValue()) {
            BaritoneMovementHelper.applySmoothDigProfile(digBreak.getValue(), digPlace.getValue());
        }
        BaritoneMovementHelper.setAllowSprint(true);
        BaritoneMovementHelper.avoidBreakingEmeraldOre();
        if (avoidSoft.getValue()) {
            BaritoneMovementHelper.avoidSoftTerrain();
        }
        eating = false;
        pausedForFood = false;
        busyStuckTicks = 0;
        busyStuckTravelSnapshot = 0;
        issueDigGoal(true);
        String modeLabel = isTunnelMode() ? "§d#tunnel" : "§bGoalXZ";
        msg("§aDig " + modeLabel + " §f" + cardinalName(digYaw) + " §7("
                + String.format(Locale.ROOT, "%.0f°", digYaw) + ")"
                + (isTunnelMode()
                ? " §7· " + tunnelHeight.getValue().intValue() + "x" + tunnelWidth.getValue().intValue()
                : "")
                + " §7· marker §f" + markerEvery.getValue().intValue() + "b"
                + (turnEnabled.getValue()
                ? " §7· turn §f" + turnEvery.getValue().intValue() + "b"
                : ""));
    }

    @Override
    protected void onDisable() {
        BaritoneMovementHelper.cancel();
        stopEating(Minecraft.getInstance());
        if (mc != null && mc.options != null) {
            mc.options.keySprint.setDown(false);
            mc.options.keyUse.setDown(false);
        }
        restoreHotbar();
        recentMarkers.clear();
        eating = false;
        pausedForFood = false;
    }

    @SubscribeEvent
    private void onTick(TickEvent.Pre event) {
        Minecraft client = event.getClient();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        if (!BaritoneMovementHelper.isAvailable()) {
            msg("§cBaritone пропал — выкл.");
            setEnabled(false);
            return;
        }

        
        
        tickCounter = (tickCounter + 1) % 1_000_000;
        updateTravel(client);

        if (autoEat.getValue()) {
            handleAutoEat(client);
        }
        if (alwaysSprint.getValue() && !eating) {
            forceSprint(client);
        }

        
        
        if (scanSigns.getValue() && tickCounter % Math.max(1, scanInterval.getValue().intValue()) == 0) {
            scanBaseSigns(client);
        }

        if (eating || pausedForFood) {
            return;
        }

        if (turnEnabled.getValue() && traveledSinceTurn >= turnEvery.getValue()) {
            applyTurn();
            traveledSinceTurn = 0;
            traveledSinceRepath = 0;
            issueDigGoal(true);
        }

        boolean busy = BaritoneMovementHelper.isBusy();
        if (busy) {
            
            if (Math.abs(traveledSinceRepath - busyStuckTravelSnapshot) < 0.05D) {
                busyStuckTicks++;
            } else {
                busyStuckTicks = 0;
                busyStuckTravelSnapshot = traveledSinceRepath;
            }
            if (busyStuckTicks >= 100) {
                issueDigGoal(true);
                busyStuckTicks = 0;
                traveledSinceRepath = 0;
            }
        } else {
            busyStuckTicks = 0;
            busyStuckTravelSnapshot = traveledSinceRepath;
        }

        if (!busy && wasPathing) {
            issueDigGoal(false);
        } else if (!isTunnelMode() && traveledSinceRepath >= repathEvery.getValue()) {
            issueDigGoal(false);
            traveledSinceRepath = 0;
        } else if (tickCounter % 40 == 0 && !busy) {
            
            issueDigGoal(false);
        } else if (tickCounter % 100 == 0 && busy) {
            
            issueDigGoal(false);
        }
        wasPathing = busy;

        if (traveledSinceMarker >= markerEvery.getValue()) {
            doTrailMarker(client);
            traveledSinceMarker = 0;
        }
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        if (!isEnabled() || !event.isReceive() || !claimWay.getValue()) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSystemChatPacket packet)) {
            return;
        }
        try {
            Component content = packet.content();
            String raw = content == null ? "" : content.getString();
            if (raw.isBlank()) {
                return;
            }
            String low = stripFormatting(raw).toLowerCase(Locale.ROOT);
            if (matchesClaim(low)) {
                onClaimDetected(raw);
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    private void onRender(WorldRenderEvent event) {
        if (!renderTrail.getValue() || recentMarkers.isEmpty()) {
            return;
        }
        for (BlockPos p : recentMarkers) {
            Render3D.drawBox(new AABB(p).inflate(0.02), 0x8800FF66, 1.2f);
        }
        if (mc.player != null) {

            double rad = Math.toRadians(digYaw);
            Vec3 from = mc.player.position().add(0, 0.1, 0);
            Vec3 to = from.add(-Math.sin(rad) * 3.0, 0, Math.cos(rad) * 3.0);
            Render3D.drawLine(from, to, 0xAA55CCFF, 2.0f, false);
        }
    }

    private void issueDigGoal(boolean forceProfile) {
        if (forceProfile && smoothBaritone.getValue()) {
            BaritoneMovementHelper.applySmoothDigProfile(digBreak.getValue(), digPlace.getValue());
            BaritoneMovementHelper.setAllowSprint(true);

        }
        if (isTunnelMode()) {
            int h = tunnelHeight.getValue().intValue();
            int w = tunnelWidth.getValue().intValue();
            int depth = digDistance.getValue().intValue();
            boolean ok = BaritoneMovementHelper.startTunnel(digYaw, h, w, depth);
            if (!ok && notify.getValue() && forceProfile) {
                msg("§c#tunnel не запустился — проверь Baritone / prefix");
            }
            return;
        }

        int dist = Math.max(200, digDistance.getValue().intValue());
        BaritoneMovementHelper.goForwardXZ(digYaw, dist);
    }

    private void applyTurn() {
        float before = digYaw;
        String mode = turnMode.getValue();
        if ("Влево 90".equals(mode)) {
            digYaw -= 90f;
        } else if ("Разворот 180".equals(mode)) {
            digYaw += 180f;
        } else if ("Змейка".equals(mode)) {
            digYaw += snakeNextLeft ? -90f : 90f;
            snakeNextLeft = !snakeNextLeft;
        } else {

            digYaw += 90f;
        }
        digYaw = normalizeYaw(digYaw);
        if (snapCardinal.getValue()) {
            digYaw = snapToCardinal(digYaw);
        }
        if (notify.getValue()) {
            msg("§bПоворот §7" + String.format(Locale.ROOT, "%.0f°", before)
                    + " → §f" + cardinalName(digYaw)
                    + " §7(" + String.format(Locale.ROOT, "%.0f°", digYaw) + ")");
        }
    }

    private static float snapToCardinal(float yaw) {
        float n = normalizeYaw(yaw);

        int q = Math.round(n / 90f);
        return normalizeYaw(q * 90f);
    }

    private static float normalizeYaw(float yaw) {
        yaw %= 360f;
        if (yaw >= 180f) {
            yaw -= 360f;
        }
        if (yaw < -180f) {
            yaw += 360f;
        }
        return yaw;
    }

    private static String cardinalName(float yaw) {
        float n = normalizeYaw(yaw);
        int q = Math.floorMod(Math.round(n / 90f), 4);
        return switch (q) {
            case 0 -> "Юг (+Z)";
            case 1 -> "Запад (-X)";
            case 2 -> "Север (-Z)";
            default -> "Восток (+X)";
        };
    }

    private void updateTravel(Minecraft client) {
        double x = client.player.getX();
        double z = client.player.getZ();
        double step = horizontal(x, z, lastSampleX, lastSampleZ);
        if (step < 0.12) {
            return;
        }
        lastSampleX = x;
        lastSampleZ = z;
        traveledSinceRepath += step;
        traveledSinceMarker += step;
        traveledSinceTurn += step;
        totalTraveled += step;
    }

    private static double horizontal(double x1, double z1, double x0, double z0) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void doTrailMarker(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        BlockPos here = client.player.blockPosition();

        boolean placed = false;
        if (placeEmerald.getValue()) {
            placed = tryPlaceEmeraldOffPath(client);
        }

        if (placed) {

            BaritoneMovementHelper.avoidBreakingEmeraldOre();
            return;
        }

        String name = nextWayName("trail");
        if (addWayUnique(name, here, "trail")) {
            if (notify.getValue()) {
                msg("§eМетка §f" + name + " §7(руда не поставилась)");
            }
        } else if (notify.getValue()) {
            msg("§8Метка пропущена (уже есть рядом)");
        }
    }

    private boolean tryPlaceEmeraldOffPath(Minecraft client) {
        if (client.gameMode == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastPlaceMs < 350L) {
            return false;
        }

        int slot = InventoryUtils.findItemInHotbar(Items.EMERALD_ORE);
        if (slot < 0) {
            slot = InventoryUtils.findItemInHotbar(Items.DEEPSLATE_EMERALD_ORE);
        }
        if (slot < 0) {
            return false;
        }

        BlockPos feet = client.player.blockPosition();
        BlockPos target = findOffPathPlaceTarget(client, feet);
        if (target == null) {
            return false;
        }

        if (isOnDigPath(target, feet)) {
            target = offsetToSide(client, feet, 2);
            if (target == null || isOnDigPath(target, feet)) {
                return false;
            }
        }

        int prev = client.player.getInventory().getSelectedSlot();
        selectedSlotBackup = prev;
        client.player.getInventory().setSelectedSlot(slot);

        BlockPos against = target.below();
        Direction face = Direction.UP;
        if (client.level.getBlockState(against).isAir() || client.level.getBlockState(against).canBeReplaced()) {
            boolean found = false;
            for (Direction d : Direction.values()) {
                BlockPos n = target.relative(d);
                BlockState ns = client.level.getBlockState(n);
                if (!ns.isAir() && !ns.canBeReplaced()) {
                    against = n;
                    face = d.getOpposite();
                    found = true;
                    break;
                }
            }
            if (!found) {
                client.player.getInventory().setSelectedSlot(prev);
                selectedSlotBackup = -1;
                return false;
            }
        }

        Vec3 hitVec = Vec3.atCenterOf(against).add(
                face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult bhr = new BlockHitResult(hitVec, face, against, false);
        InteractionResult result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, bhr);
        client.player.getInventory().setSelectedSlot(prev);
        selectedSlotBackup = -1;

        if (!result.consumesAction()) {
            return false;
        }

        BlockState after = client.level.getBlockState(target);
        boolean ok = after.is(Blocks.EMERALD_ORE) || after.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || after.getBlock().getDescriptionId().contains("emerald_ore");

        if (after.isAir()) {
            return false;
        }

        lastPlaceMs = System.currentTimeMillis();
        BlockPos imm = target.immutable();
        recentMarkers.add(imm);
        if (recentMarkers.size() > 40) {
            recentMarkers.removeFirst();
        }

        protectedMarkers.add(imm);
        if (notify.getValue()) {
            msg("§aEmerald §7@ §f" + imm.getX() + " " + imm.getY() + " " + imm.getZ()
                    + (ok ? "" : " §8(placed block)"));
        }
        return true;
    }

    private BlockPos findOffPathPlaceTarget(Minecraft client, BlockPos feet) {

        for (int side : new int[]{2, 3, -2, -3, 1, -1}) {
            BlockPos p = offsetToSide(client, feet, side);
            if (p != null && canPlaceAt(client, p) && !isOnDigPath(p, feet)) {
                return p;
            }
        }
        double rad = Math.toRadians(digYaw);
        int backX = feet.getX() - (int) Math.round(-Math.sin(rad) * 2);
        int backZ = feet.getZ() - (int) Math.round(Math.cos(rad) * 2);
        for (int side : new int[]{2, -2, 3, -3}) {
            double sideRad = rad + Math.PI / 2.0;
            int sx = backX + (int) Math.round(-Math.sin(sideRad) * side);
            int sz = backZ + (int) Math.round(Math.cos(sideRad) * side);
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos p = new BlockPos(sx, feet.getY() + dy, sz);
                if (canPlaceAt(client, p) && !isOnDigPath(p, feet)) {
                    return p;
                }
            }
        }
        return null;
    }

    private BlockPos offsetToSide(Minecraft client, BlockPos feet, int sideBlocks) {
        double rad = Math.toRadians(digYaw);

        double sideRad = rad + Math.PI / 2.0;
        int sx = feet.getX() + (int) Math.round(-Math.sin(sideRad) * sideBlocks);
        int sz = feet.getZ() + (int) Math.round(Math.cos(sideRad) * sideBlocks);
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos p = new BlockPos(sx, feet.getY() + dy, sz);
            if (canPlaceAt(client, p)) {
                return p;
            }
        }
        return null;
    }

    private boolean canPlaceAt(Minecraft client, BlockPos p) {
        BlockState st = client.level.getBlockState(p);
        BlockState under = client.level.getBlockState(p.below());
        return (st.isAir() || st.canBeReplaced())
                && !under.isAir()
                && !under.canBeReplaced();
    }

    private boolean isOnDigPath(BlockPos p, BlockPos feet) {
        double rad = Math.toRadians(digYaw);
        int fx = (int) Math.round(-Math.sin(rad));
        int fz = (int) Math.round(Math.cos(rad));
        if (p.getX() == feet.getX() && p.getZ() == feet.getZ()) {
            return true;
        }
        if (p.getX() == feet.getX() + fx && p.getZ() == feet.getZ() + fz) {
            return true;
        }

        if (p.getX() == feet.getX() + fx * 2 && p.getZ() == feet.getZ() + fz * 2) {
            return true;
        }
        return false;
    }

    private void restoreHotbar() {
        if (selectedSlotBackup >= 0 && mc.player != null) {
            try {
                mc.player.getInventory().setSelectedSlot(selectedSlotBackup);
            } catch (Throwable ignored) {
            }
            selectedSlotBackup = -1;
        }
    }

    private void forceSprint(Minecraft client) {
        if (client.player == null || client.options == null) {
            return;
        }
        int food = client.player.getFoodData().getFoodLevel();

        if (food <= 6 || client.player.isUsingItem()) {
            return;
        }
        boolean moving = client.player.input != null
                && (client.player.input.getMoveVector().x != 0 || client.player.input.getMoveVector().y != 0);

        if (!moving && !BaritoneMovementHelper.isBusy()) {
            return;
        }
        client.options.keySprint.setDown(true);
        if (!client.player.isSprinting() && client.player.onGround()) {
            client.player.setSprinting(true);
        }
    }

    private void handleAutoEat(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            return;
        }
        int food = client.player.getFoodData().getFoodLevel();
        int stopAt = eatUntil.getValue().intValue();

        if (eating) {
            if (food >= stopAt || client.player.isDeadOrDying()) {
                stopEating(client);
                if (pausedForFood) {
                    pausedForFood = false;
                    issueDigGoal(true);
                }
            } else if (!client.player.isUsingItem()) {

                startEatingOffhand(client);
            }
            return;
        }

        if (food > 6) {
            return;
        }

        ensureFoodInOffhand(client);
        if (!isFood(client.player.getOffhandItem())) {
            if (notify.getValue() && tickCounter % 80 == 0) {
                msg("§eНет еды для offhand");
            }
            return;
        }

        if (!pausedForFood) {
            BaritoneMovementHelper.cancel();
            pausedForFood = true;
        }
        startEatingOffhand(client);
    }

    private void startEatingOffhand(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            return;
        }
        if (!isFood(client.player.getOffhandItem())) {
            return;
        }
        try {
            client.gameMode.useItem(client.player, InteractionHand.OFF_HAND);
            client.options.keyUse.setDown(true);
            eating = true;
        } catch (Throwable ignored) {
        }
    }

    private void stopEating(Minecraft client) {
        if (client != null && client.options != null) {
            client.options.keyUse.setDown(false);
        }
        if (client != null && client.player != null && client.player.isUsingItem()) {
            try {
                client.player.releaseUsingItem();
            } catch (Throwable ignored) {
            }
        }
        eating = false;
    }

    private void ensureFoodInOffhand(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (isFood(client.player.getOffhandItem())) {
            return;
        }
        if (System.currentTimeMillis() - lastFoodSwapMs < 400L) {
            return;
        }

        int hotbar = findFoodHotbar(client);
        if (hotbar >= 0) {
            int prev = client.player.getInventory().getSelectedSlot();
            client.player.getInventory().setSelectedSlot(hotbar);
            try {
                if (client.getConnection() != null) {
                    client.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            BlockPos.ZERO, Direction.DOWN));
                }
            } catch (Throwable ignored) {
            }
            client.player.getInventory().setSelectedSlot(prev);
            lastFoodSwapMs = System.currentTimeMillis();
            return;
        }

        int inv = findFoodInventory(client);
        if (inv >= 0 && client.gameMode != null) {
            try {
                int containerSlot = inv < 9 ? inv + 36 : inv;
                client.gameMode.handleInventoryMouseClick(
                        client.player.containerMenu.containerId,
                        containerSlot, 40, ClickType.SWAP, client.player);
                lastFoodSwapMs = System.currentTimeMillis();
            } catch (Throwable ignored) {
            }
        }
    }

    private static int findFoodHotbar(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            if (isFood(client.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int findFoodInventory(Minecraft client) {
        for (int i = 9; i < 36; i++) {
            if (isFood(client.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            if (stack.get(DataComponents.FOOD) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {

            Consumable c = stack.get(DataComponents.CONSUMABLE);
            if (c != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        var item = stack.getItem();
        return item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP
                || item == Items.COOKED_CHICKEN || item == Items.COOKED_MUTTON
                || item == Items.COOKED_RABBIT || item == Items.COOKED_SALMON
                || item == Items.COOKED_COD || item == Items.BREAD
                || item == Items.GOLDEN_CARROT || item == Items.GOLDEN_APPLE
                || item == Items.BAKED_POTATO || item == Items.APPLE
                || item == Items.DRIED_KELP || item == Items.COOKED_COD;
    }

    private boolean matchesClaim(String low) {
        String raw = claimKeywords.getValue();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String part : raw.split("\\|")) {
            String k = part.trim().toLowerCase(Locale.ROOT);
            if (!k.isEmpty() && low.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private void onClaimDetected(String original) {
        if (mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastClaimWayMs < wayCooldown.getValue() * 1000L) {
            return;
        }
        BlockPos pos = mc.player.blockPosition();

        String prefix = safeName(claimWayPrefix.getValue(), "приват");
        String name = nextWayName(prefix);
        if (!addWayUnique(name, pos, "приват")) {
            return;
        }
        lastClaimWayMs = now;
        msg("§c" + name + " §7@ §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " §8→ dig дальше");
        if (notify.getValue()) {
            String shortMsg = original.length() > 80 ? original.substring(0, 80) + "…" : original;
            msg("§8«" + stripFormatting(shortMsg) + "»");
        }
        if (stopOnClaim.getValue()) {
            BaritoneMovementHelper.cancel();
            setEnabled(false);
            msg("§eDig остановлен (Стоп на claim)");
        }
    }

    private void scanBaseSigns(Minecraft client) {
        double r = scanRange.getValue();
        BlockPos origin = client.player.blockPosition();
        AABB box = client.player.getBoundingBox().inflate(r);
        double r2 = r * r;

        if (signTypes.isSelected(SIGN_VILLAGER)) {
            for (Entity e : client.level.getEntities(client.player, box,
                    ent -> ent instanceof AbstractVillager)) {
                markSign("житель", e.blockPosition());
            }
        }

        int chunkR = Math.max(1, (int) Math.ceil(r / 16.0));
        int cx0 = origin.getX() >> 4;
        int cz0 = origin.getZ() >> 4;

        for (int dx = -chunkR; dx <= chunkR; dx++) {
            for (int dz = -chunkR; dz <= chunkR; dz++) {
                int cx = cx0 + dx;
                int cz = cz0 + dz;
                if (!client.level.getChunkSource().hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = client.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                try {
                    for (var entry : chunk.getBlockEntities().entrySet()) {
                        BlockPos pos = entry.getKey();
                        if (pos.distSqr(origin) > r2) {
                            continue;
                        }
                        BlockEntity be = entry.getValue();
                        Block b = chunk.getBlockState(pos).getBlock();
                        tryMarkBaseBlock(b, be, pos);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (heavyScan.getValue()) {
            scanNonBeBlocksCheap(client, origin, r, r2);
        }
    }

    private void scanNonBeBlocksCheap(Minecraft client, BlockPos origin, double r, double r2) {
        int step = 2;
        int yPad = 16;
        int budget = 12_000;
        int ri = (int) Math.ceil(r);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

        int phase = tickCounter % step;

        for (int x = -ri + phase; x <= ri && budget > 0; x += step) {
            for (int z = -ri; z <= ri && budget > 0; z += step) {
                if (x * x + z * z > r2) {
                    continue;
                }
                int wx = origin.getX() + x;
                int wz = origin.getZ() + z;
                mut.set(wx, origin.getY(), wz);
                if (!client.level.hasChunkAt(mut)) {
                    continue;
                }
                for (int y = -yPad; y <= yPad && budget > 0; y += step) {
                    budget--;
                    mut.set(wx, origin.getY() + y, wz);
                    BlockState st = client.level.getBlockState(mut);
                    if (st.isAir()) {
                        continue;
                    }
                    Block b = st.getBlock();

                    if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF
                            || b == Blocks.ENCHANTING_TABLE || b == Blocks.CRAFTING_TABLE
                            || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL
                            || b == Blocks.GRINDSTONE || b == Blocks.SMITHING_TABLE
                            || b == Blocks.STONECUTTER || b == Blocks.LOOM
                            || b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.FLETCHING_TABLE
                            || b == Blocks.COMPOSTER || b == Blocks.BELL
                            || b == Blocks.END_PORTAL_FRAME) {
                        tryMarkBaseBlock(b, null, mut.immutable());
                    }
                }
            }
        }
    }

    private void tryMarkBaseBlock(Block b, BlockEntity be, BlockPos pos) {
        if (b == null || pos == null) {
            return;
        }

        if (signTypes.isSelected(SIGN_SHULKER)
                && (b instanceof ShulkerBoxBlock || be instanceof ShulkerBoxBlockEntity)) {
            if (isEmptyContainer(be)) {
                return;
            }
            markSign("шалкер", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_CHEST)
                && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST
                || b == Blocks.ENDER_CHEST || b == Blocks.BARREL)) {
            
            if (b != Blocks.ENDER_CHEST && isEmptyContainer(be)) {
                return;
            }
            markSign(b == Blocks.ENDER_CHEST ? "эндер" : "сундук", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_HOPPER)
                && (b == Blocks.HOPPER || b == Blocks.DROPPER || b == Blocks.DISPENSER
                || b == Blocks.CRAFTER)) {
            String lab = b == Blocks.HOPPER ? "воронка"
                    : b == Blocks.DROPPER ? "дроппер"
                    : b == Blocks.DISPENSER ? "раздатчик" : "крафтер";
            markSign(lab, pos);
            return;
        }

        if (signTypes.isSelected(SIGN_BREWING) && b == Blocks.BREWING_STAND) {
            markSign("зельеварка", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_ENCHANT) && b == Blocks.ENCHANTING_TABLE) {
            markSign("зачарование", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_BOOKSHELF)
                && (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF)) {
            markSign("полка", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_FURNACE)
                && (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER)) {
            String lab = b == Blocks.BLAST_FURNACE ? "плавильня"
                    : b == Blocks.SMOKER ? "коптильня" : "печь";
            markSign(lab, pos);
            return;
        }

        if (signTypes.isSelected(SIGN_CRAFT) && b == Blocks.CRAFTING_TABLE) {
            markSign("верстак", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_ANVIL)
                && (b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL)) {
            markSign("наковальня", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_UTILITY)
                && (b == Blocks.GRINDSTONE || b == Blocks.SMITHING_TABLE
                || b == Blocks.STONECUTTER || b == Blocks.LOOM
                || b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.FLETCHING_TABLE
                || b == Blocks.COMPOSTER || b == Blocks.CAULDRON
                || b == Blocks.WATER_CAULDRON || b == Blocks.LAVA_CAULDRON
                || b == Blocks.POWDER_SNOW_CAULDRON || b == Blocks.BELL)) {
            String lab = b == Blocks.GRINDSTONE ? "точило"
                    : b == Blocks.SMITHING_TABLE ? "кузня"
                    : b == Blocks.STONECUTTER ? "камнерез"
                    : b == Blocks.LOOM ? "ткацкий"
                    : b == Blocks.CARTOGRAPHY_TABLE ? "картограф"
                    : b == Blocks.FLETCHING_TABLE ? "лучник"
                    : b == Blocks.BELL ? "колокол"
                    : b == Blocks.COMPOSTER ? "компостер" : "котёл";
            markSign(lab, pos);
            return;
        }

        if (signTypes.isSelected(SIGN_LECTERN) && b == Blocks.LECTERN) {
            markSign("кафедра", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_BEACON) && b == Blocks.BEACON) {
            markSign("маяк", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_SPAWNER)
                && (b == Blocks.SPAWNER || b == Blocks.TRIAL_SPAWNER)) {
            
            if (b == Blocks.TRIAL_SPAWNER && isEmptyTrialSpawner(be)) {
                return;
            }
            markSign(b == Blocks.TRIAL_SPAWNER ? "trial" : "спавнер", pos);
            return;
        }

        if (signTypes.isSelected(SIGN_PORTAL) && b == Blocks.END_PORTAL_FRAME) {
            markSign("портал", pos);
        }
    }

    private static boolean isEmptyContainer(BlockEntity be) {
        if (!(be instanceof Container container)) {
            
            return false;
        }
        return container.isEmpty();
    }

    private static boolean isEmptyTrialSpawner(BlockEntity be) {
        if (!(be instanceof TrialSpawnerBlockEntity trial)) {
            return false;
        }
        TrialSpawnerState state = trial.getState();
        
        
        return state == TrialSpawnerState.COOLDOWN
                || state == TrialSpawnerState.EJECTING_REWARD;
    }

    private void markSign(String kindLabel, BlockPos pos) {

        long ckey = (((long) (pos.getX() >> 4)) << 32) ^ (pos.getZ() >> 4)
                ^ ((long) kindLabel.hashCode() << 16);
        if (!signedChunks.add(ckey)) {
            return;
        }
        String name = nextWayName(kindLabel);
        if (addWayUnique(name, pos, kindLabel)) {
            lastSignWayMs = System.currentTimeMillis();
            msg("§d" + name + " §7@ §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " §8→ dig дальше");
        }
    }

    private String nextWayName(String base) {
        String key = base == null ? "метка" : base.trim();
        if (key.isEmpty()) {
            key = "метка";
        }
        int n = wayNameCounters.getOrDefault(key, 0);
        wayNameCounters.put(key, n + 1);
        return n == 0 ? key : key + n;
    }

    private boolean addWayUnique(String name, BlockPos pos, String kind) {
        WayRepository repo = WayRepository.getInstance();
        String server = repo.getCurrentServer();
        if (server == null || server.isBlank()) {
            server = "local";
        }

        String cellKey = server + "|" + (pos.getX() / Math.max(1, wayMinDist.getValue().intValue()))
                + "|" + (pos.getZ() / Math.max(1, wayMinDist.getValue().intValue())) + "|" + kind;
        if (!claimedWayKeys.add(cellKey)) {
            return false;
        }

        double minD = wayMinDist.getValue();
        double minD2 = minD * minD;
        for (var way : repo.getWaysForServer(server)) {
            if (way.pos().distSqr(pos) < minD2) {
                return false;
            }
        }

        String finalName = name;
        int guard = 0;
        while (repo.hasWay(finalName, server) && guard++ < 200) {
            finalName = nextWayName(kind);
        }
        if (repo.hasWay(finalName, server)) {
            return false;
        }

        try {
            repo.addWayAndSave(finalName, pos.immutable(), server);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String safeName(String s, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        return s.trim().replaceAll("[^\\w\\-а-яА-ЯёЁ]+", "_");
    }

    private static String stripFormatting(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("§.", "").replaceAll("\\u00a7.", "");
    }

    private static void msg(String text) {
        ChatMessage.brandmessage(text);
    }
}
