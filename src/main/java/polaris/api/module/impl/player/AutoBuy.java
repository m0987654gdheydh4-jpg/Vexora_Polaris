package polaris.api.module.impl.player;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.modules.autobuy.AuctionUtils;
import polaris.utils.modules.autobuy.AutoBuyItem;
import polaris.utils.modules.autobuy.AutoBuyManager;
import polaris.utils.string.chat.ChatMessage;
import polaris.utils.timer.TimerUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;


public final class AutoBuy extends Module {
    private static AutoBuy instance;

    
    private static final boolean TEMPORARILY_UNAVAILABLE = false;
    private boolean suppressingDisableMessage;

    private final ModeSetting serverMode = register(new ModeSetting(
            "Режим сервера", "FunTime / SpookyTime / HolyWorld",
            "FunTime", "FunTime", "SpookyTime", "HolyWorld"));

    private final BooleanSetting autoParse = register(new BooleanSetting(
            "Auto Parse", "Периодический парсинг цен через /ah search.", false));
    private final NumberSetting parseDiscount = register(new NumberSetting(
            "Парс скидка %", "Скидка от мин. цены при парсе.", 20.0, 1.0, 100.0, 1.0));
    private final NumberSetting reparseMinutes = register(new NumberSetting(
            "ReParse (мин)", "Повторный полный парсинг (0 = только вручную).", 0.0, 0.0, 240.0, 5.0));

    private final NumberSetting updateDelay = register(new NumberSetting(
            "Кд обновления (мс)", "Задержка обновления аукциона.", 350.0, 100.0, 5000.0, 50.0));
    private final NumberSetting buyDelay = register(new NumberSetting(
            "Кд покупки (мс)", "Задержка между покупками.", 120.0, 50.0, 5000.0, 10.0));
    private final NumberSetting buyHoldMs = register(new NumberSetting(
            "Пауза перед покупкой (мс)",
            "Сколько держать цель перед shift-кликом (меньше ложных переносов в инвентарь).",
            100.0, 40.0, 400.0, 10.0));
    private final NumberSetting postBuyPauseMs = register(new NumberSetting(
            "Пауза после покупки (мс)",
            "Не кликать/не обновлять AH сразу после shift-покупки.",
            250.0, 50.0, 1500.0, 25.0));
    private final NumberSetting confirmDelay = register(new NumberSetting(
            "Кд подтверждения (мс)", "Задержка клика подтверждения.", 50.0, 0.0, 1000.0, 10.0));

    private final BooleanSetting anarchySwap = register(new BooleanSetting(
            "Свап анархии", "Случайный /anN (FT 90–120с · SP/HW 5–10 мин).", true));
    private final NumberSetting anarchyMinSec = register(new NumberSetting(
            "Анархия мин (с)", "Мин. интервал /an (FunTime).", 90.0, 30.0, 600.0, 5.0));
    private final NumberSetting anarchyMaxSec = register(new NumberSetting(
            "Анархия макс (с)", "Макс. интервал /an (FunTime).", 120.0, 40.0, 900.0, 5.0));
    private final BooleanSetting spWalk = register(new BooleanSetting(
            "SP ходьба", "SpookyTime: отойти ~N блоков и снова /ah.", true));
    private final NumberSetting spWalkBlocks = register(new NumberSetting(
            "SP блоков", "Дистанция ухода перед скупкой.", 5.0, 2.0, 15.0, 0.5));
    private final NumberSetting spWalkIntervalSec = register(new NumberSetting(
            "SP интервал (с)", "Как часто отходить (сек).", 60.0, 15.0, 300.0, 5.0));
    private final BooleanSetting autoOpenAh = register(new BooleanSetting(
            "Авто /ah", "Открывать аукцион если закрыт.", true));
    private final NumberSetting ahReopenMs = register(new NumberSetting(
            "Кд /ah (мс)", "Как часто пытаться открыть /ah.", 1500.0, 500.0, 30000.0, 100.0));

    private final NumberSetting shulkerProfitPct = register(new NumberSetting(
            "Shulker Profit %", "Мин. профит % для шалкера (зарезерв.).", 18.0, 0.0, 200.0, 1.0));
    private final NumberSetting shulkerProfitAbs = register(new NumberSetting(
            "Shulker Profit $", "Мин. профит $ для шалкера (зарезерв.).", 50000.0, 0.0, 1.0e9, 10000.0));
    private final NumberSetting shulkerValue = register(new NumberSetting(
            "Shulker Value $", "Мин. ценность содержимого (зарезерв.).", 100000.0, 0.0, 1.0e9, 10000.0));

    private final BooleanSetting notifications = register(new BooleanSetting(
            "Уведомления", "Сообщения о покупках в чат.", true));
    private final BindSetting menuBind = register(new BindSetting(
            "Бинд меню", "Открыть ClickGUI (категория AutoBuy).", KeyBind.NONE));

    private final TimerUtil updateTimer = TimerUtil.create();
    private final TimerUtil buyTimer = TimerUtil.create();
    private final TimerUtil confirmTimer = TimerUtil.create();
    private final TimerUtil ahTimer = TimerUtil.create();
    private final TimerUtil anarchyTimer = TimerUtil.create();
    private final TimerUtil parseCmdTimer = TimerUtil.create();
    private final TimerUtil parseWaitTimer = TimerUtil.create();
    private final TimerUtil reparseTimer = TimerUtil.create();

    private final Set<String> boughtKeys = new HashSet<>();
    private long nextAnarchyMs = 0L;
    private boolean inAuction;

    
    private enum WalkState { IDLE, CLOSING, MOVING, OPENING }
    private WalkState walkState = WalkState.IDLE;
    private long nextWalkAtMs;
    private long walkCloseAtMs;
    private boolean walkForward = true;
    private double walkStartX, walkStartZ;

    
    private enum FtSwitch { IDLE, SEND, WAIT, OPEN_AH }
    private FtSwitch ftSwitch = FtSwitch.IDLE;
    private long ftPhaseAtMs;
    private int pendingAnarchy = -1;
    private static final int[] FT_ANARCHIES = {101, 102, 103, 104, 105, 201, 202, 203, 204, 205, 301, 302, 303};

    
    private int pendingSlot = -1;
    private String pendingKey = "";
    private String pendingName = "";
    private int pendingPrice = -1;
    private int pendingCount = -1;
    private long pendingSinceMs = 0L;
    private long postBuyUntilMs = 0L;
    private long lastBuyClickMs = 0L;
    private float cameraSwayTime;

    
    private boolean parseRunning;
    private boolean parseWaitingResult;
    private int parseIndex;
    private int parseRetries;
    private String parseCurrentName = "";
    private final List<String> parseQueue = new java.util.ArrayList<>();
    private int parseUpdatedCount;

    public AutoBuy() {
        super("AutoBuy", "Автопокупка предметов на аукционе", ModuleCategory.AUTOBUY);
        instance = this;
        parseDiscount.visibleWhen(autoParse::getValue);
        reparseMinutes.visibleWhen(autoParse::getValue);
        anarchyMinSec.visibleWhen(() -> anarchySwap.getValue() && serverMode.is("FunTime"));
        anarchyMaxSec.visibleWhen(() -> anarchySwap.getValue() && serverMode.is("FunTime"));
        spWalk.visibleWhen(() -> serverMode.is("SpookyTime"));
        spWalkBlocks.visibleWhen(() -> serverMode.is("SpookyTime") && spWalk.getValue());
        spWalkIntervalSec.visibleWhen(() -> serverMode.is("SpookyTime") && spWalk.getValue());
        ahReopenMs.visibleWhen(autoOpenAh::getValue);
    }

    public NumberSetting getUpdateDelaySetting() {
        return updateDelay;
    }

    public NumberSetting getBuyDelaySetting() {
        return buyDelay;
    }

    public BooleanSetting getAnarchySwapSetting() {
        return anarchySwap;
    }

    public NumberSetting getAnarchyMinSecSetting() {
        return anarchyMinSec;
    }

    public NumberSetting getAnarchyMaxSecSetting() {
        return anarchyMaxSec;
    }

    public BooleanSetting getSpWalkSetting() {
        return spWalk;
    }

    public NumberSetting getSpWalkBlocksSetting() {
        return spWalkBlocks;
    }

    public NumberSetting getSpWalkIntervalSetting() {
        return spWalkIntervalSec;
    }

    public NumberSetting getAhReopenSetting() {
        return ahReopenMs;
    }

    public BooleanSetting getAutoOpenAhSetting() {
        return autoOpenAh;
    }

    public NumberSetting getReparseMinutesSetting() {
        return reparseMinutes;
    }

    public void cycleServerMode() {
        String[] modes = {"FunTime", "SpookyTime", "HolyWorld"};
        String cur = serverMode.getValue();
        int idx = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(cur)) {
                idx = i;
                break;
            }
        }
        serverMode.setValue(modes[(idx + 1) % modes.length]);
        msg("§eРежим: §f" + serverMode.getValue());
    }

    public void adjustParseDiscount(int delta) {
        int v = parseDiscount.getValue().intValue() + delta;
        parseDiscount.setValue((double) Math.max(1, Math.min(100, v)));
    }

    public void adjustUpdateDelay(int deltaMs) {
        int v = updateDelay.getValue().intValue() + deltaMs;
        updateDelay.setValue((double) Math.max(100, Math.min(5000, v)));
    }

    public static AutoBuy getInstance() {
        return instance;
    }

    public ModeSetting getServerMode() {
        return serverMode;
    }

    public BooleanSetting getAutoParseSetting() {
        return autoParse;
    }

    public NumberSetting getParseDiscountSetting() {
        return parseDiscount;
    }

    public boolean isAutoParseEnabled() {
        return autoParse.getValue();
    }

    public boolean isParseRunning() {
        return parseRunning;
    }

    public int getParseIndex() {
        return parseIndex;
    }

    public int getParseQueueSize() {
        return parseQueue.size();
    }

    public String getParseCurrentName() {
        return parseCurrentName == null ? "" : parseCurrentName;
    }

    
    public void toggleAutoParse() {
        ensureModuleOn();
        autoParse.setValue(!autoParse.getValue());
        if (autoParse.getValue()) {
            msg("§d[AutoParse] §aвключён (скидка " + parseDiscount.getValue().intValue() + "%)");
            startParse(false);
        } else {
            stopParse(false);
            msg("§d[AutoParse] §cвыключен");
        }
    }

    
    public void startParseNow() {
        ensureModuleOn();
        if (!autoParse.getValue()) {
            autoParse.setValue(true);
        }
        startParse(false);
    }

    private void ensureModuleOn() {
        if (!isEnabled()) {
            setEnabled(true);
        }
    }

    @Override
    protected void onEnable() {
        if (TEMPORARILY_UNAVAILABLE) {
            msg("§cAutoBuy временно недоступен");
            
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> {
                    if (!isEnabled()) {
                        return;
                    }
                    suppressingDisableMessage = true;
                    setEnabled(false);
                    suppressingDisableMessage = false;
                });
            }
            return;
        }
        AutoBuyManager.get().ensureLoaded();
        
        if (!parseRunning) {
            resetBuyRuntime();
        }
        nextAnarchyMs = randomAnarchyDelay();
        anarchyTimer.resetCounter();
        scheduleNextWalk();
        walkState = WalkState.IDLE;
        ftSwitch = FtSwitch.IDLE;
        pendingAnarchy = -1;
        stopWalkKeys();
        reparseTimer.resetCounter();
        msg("§aAutoBuy включён §7(" + serverMode.getValue() + ")");
    }

    @Override
    protected void onDisable() {
        if (suppressingDisableMessage) {
            return;
        }
        stopParse(false);
        resetBuyRuntime();
        stopWalkKeys();
        walkState = WalkState.IDLE;
        ftSwitch = FtSwitch.IDLE;
        msg("§cAutoBuy выключен");
    }

    private void resetBuyRuntime() {
        boughtKeys.clear();
        inAuction = false;
        clearPendingBuy();
        postBuyUntilMs = 0L;
        lastBuyClickMs = 0L;
        updateTimer.resetCounter();
        buyTimer.resetCounter();
        confirmTimer.resetCounter();
        ahTimer.resetCounter();
        anarchyTimer.resetCounter();
    }

    private void scheduleNextWalk() {
        long sec = Math.max(5L, spWalkIntervalSec.getValue().longValue());
        nextWalkAtMs = System.currentTimeMillis() + sec * 1000L;
    }

    private void stopWalkKeys() {
        if (mc == null || mc.options == null) {
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
    }

    private void clearPendingBuy() {
        pendingSlot = -1;
        pendingKey = "";
        pendingName = "";
        pendingPrice = -1;
        pendingCount = -1;
        pendingSinceMs = 0L;
    }

    private void stopParse(boolean completedMsg) {
        parseRunning = false;
        parseWaitingResult = false;
        parseIndex = 0;
        parseRetries = 0;
        parseCurrentName = "";
        parseQueue.clear();
        parseUpdatedCount = 0;
        parseCmdTimer.resetCounter();
        parseWaitTimer.resetCounter();
        if (completedMsg) {
            msg("§d[AutoParse] §aГотово · обновлено " + parseUpdatedCount + " поз.");
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (!isEnabled()) {
            return;
        }

        handleMenuBind();

        
        if (parseRunning) {
            tickParse();
            return;
        }

        
        if (handleFunTimeAnarchy()) {
            return;
        }

        
        if (handleSpookyWalk()) {
            return;
        }

        
        if (autoParse.getValue()) {
            long reparseMs = reparseMinutes.getValue().longValue() * 60_000L;
            if (reparseMs > 0L && reparseTimer.hasTimeElapsed(reparseMs)) {
                startParse(true);
                return;
            }
        }

        
        if (!serverMode.is("FunTime")) {
            handleAnarchySwap();
        }

        if (!(mc.screen instanceof ContainerScreen screen)) {
            inAuction = false;
            boughtKeys.clear();
            clearPendingBuy();
            if (autoOpenAh.getValue() && ahTimer.hasTimeElapsed(ahReopenMs.getValue().longValue())) {
                mc.player.connection.sendCommand("ah");
                ahTimer.resetCounter();
            }
            return;
        }

        String title = screen.getTitle().getString();
        int slotCount = screen.getMenu().slots.size();

        
        if (AuctionUtils.isConfirmTitle(title) || isLikelyConfirm(title, slotCount)) {
            clearPendingBuy();
            if (confirmTimer.hasTimeElapsed(confirmDelay.getValue().longValue())) {
                clickConfirm(screen);
                confirmTimer.resetCounter();
                
                postBuyUntilMs = System.currentTimeMillis() + 120L;
            }
            return;
        }

        if (!AuctionUtils.isAuctionTitle(title) && !AuctionUtils.isSearchTitle(title)) {
            inAuction = false;
            clearPendingBuy();
            return;
        }

        if (!inAuction) {
            inAuction = true;
            boughtKeys.clear();
            clearPendingBuy();
            msg("§aАукцион. Включено: §b" + AutoBuyManager.get().enabledCount(serverMode.getValue()));
        }

        long now = System.currentTimeMillis();
        boolean postBuyFreeze = now < postBuyUntilMs;
        boolean aiming = pendingSlot >= 0;

        
        if (!postBuyFreeze && !aiming && updateTimer.hasTimeElapsed(effectiveUpdateDelay())) {
            refreshAuction(screen);
            updateTimer.resetCounter();
            if (boughtKeys.size() > 200) {
                boughtKeys.clear();
            }
        }

        if (postBuyFreeze) {
            return;
        }

        if (buyTimer.hasTimeElapsed(effectiveBuyDelay())) {
            if (scanAndBuy(screen)) {
                buyTimer.resetCounter();
            }
        }
    }

    

    private void startParse(boolean isReparse) {
        AutoBuyManager.get().ensureLoaded();
        parseQueue.clear();
        List<AutoBuyItem> source = AutoBuyManager.get().enabledItems(serverMode.getValue());
        if (source.isEmpty()) {
            msg("§c[AutoParse] Нет включённых предметов для " + serverMode.getValue()
                    + " — кликни иконки в AutoBuy GUI");
            parseRunning = false;
            return;
        }
        for (AutoBuyItem item : source) {
            parseQueue.add(item.getName());
        }
        parseRunning = true;
        parseWaitingResult = false;
        parseIndex = 0;
        parseRetries = 0;
        parseUpdatedCount = 0;
        parseCurrentName = parseQueue.get(0);
        parseCmdTimer.resetCounter();
        parseWaitTimer.resetCounter();
        reparseTimer.resetCounter();
        
        if (mc.player != null && mc.screen != null) {
            mc.player.closeContainer();
        }
        msg((isReparse ? "§e[AutoParse] ReParse · " : "§d[AutoParse] §fСтарт · ")
                + parseQueue.size() + " предм. · скидка " + parseDiscount.getValue().intValue() + "%");
    }

    private void tickParse() {
        if (parseQueue.isEmpty()) {
            finishParse();
            return;
        }
        if (parseIndex >= parseQueue.size()) {
            finishParse();
            return;
        }

        parseCurrentName = parseQueue.get(parseIndex);

        if (!parseWaitingResult) {
            
            if (!parseCmdTimer.hasTimeElapsed(1100L)) {
                return;
            }
            if (mc.player == null) {
                return;
            }
            
            if (mc.screen != null) {
                mc.player.closeContainer();
                parseCmdTimer.resetCounter();
                return;
            }
            String search = toSearchQuery(parseCurrentName);
            if (serverMode.is("HolyWorld")) {
                mc.player.connection.sendCommand("ah " + search);
            } else {
                mc.player.connection.sendCommand("ah search " + search);
            }
            parseWaitingResult = true;
            parseWaitTimer.resetCounter();
            return;
        }

        
        if (!(mc.screen instanceof ContainerScreen screen)) {
            
            if (parseWaitTimer.hasTimeElapsed(4500L)) {
                if (++parseRetries >= 3) {
                    msg("§c[AutoParse] §f" + shortName(parseCurrentName)
                            + " §7пропущен: GUI не открылся (3 попытки)");
                    advanceParse();
                } else {
                    msg("§e[AutoParse] §f" + shortName(parseCurrentName)
                            + " §7GUI не открылся, повтор " + parseRetries + "/3");
                    parseWaitingResult = false;
                    parseCmdTimer.resetCounter();
                }
            }
            return;
        }

        
        if (!parseWaitTimer.hasTimeElapsed(700L)) {
            return;
        }

        
        String title = screen.getTitle().getString();
        if (AuctionUtils.isConfirmTitle(title)) {
            clickConfirm(screen);
            parseWaitTimer.resetCounter();
            return;
        }

        
        boolean okGui = AuctionUtils.isAuctionTitle(title)
                || AuctionUtils.isSearchTitle(title)
                || titleLooksLikeSearchFor(title, parseCurrentName)
                || hasPricedLots(screen);

        if (!okGui) {
            if (parseWaitTimer.hasTimeElapsed(5000L)) {
                if (++parseRetries >= 3) {
                    msg("§c[AutoParse] §f" + shortName(parseCurrentName)
                            + " §7пропущен: неверный GUI");
                    advanceParse();
                } else {
                    parseWaitingResult = false;
                    parseCmdTimer.resetCounter();
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                }
            }
            return;
        }

        
        ParsePrice result = findLowestUnitPrice(screen, parseCurrentName);
        if (result == null) {
            
            if (!parseWaitTimer.hasTimeElapsed(2500L)) {
                return;
            }
            if (++parseRetries >= 3) {
                msg("§e[AutoParse] §f" + shortName(parseCurrentName) + " §7не найден на AH");
                advanceParse();
            } else {
                msg("§e[AutoParse] §f" + shortName(parseCurrentName)
                        + " §7не найден, повтор " + parseRetries + "/3");
                parseWaitingResult = false;
                parseCmdTimer.resetCounter();
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
            }
            return;
        }

        int discount = parseDiscount.getValue().intValue();
        long discounted = Math.max(1L, result.unitPrice * (100L - discount) / 100L);
        AutoBuyItem item = AutoBuyManager.get().findByName(parseCurrentName, serverMode.getValue());
        if (item != null) {
            item.setBuyPrice((int) Math.min(Integer.MAX_VALUE, discounted));
            AutoBuyManager.get().scheduleSave();
            parseUpdatedCount++;
            msg("§d[AutoParse] §f" + shortName(parseCurrentName)
                    + " §7→ §a" + discounted + "$ §8(-" + discount + "% от " + result.unitPrice
                    + "$/шт, лот " + result.lotPrice + "$ x" + result.count + ")");
        }
        advanceParse();
    }

    private void advanceParse() {
        parseIndex++;
        parseRetries = 0;
        parseWaitingResult = false;
        parseCmdTimer.resetCounter();
        parseWaitTimer.resetCounter();
        if (mc.player != null && mc.screen != null) {
            mc.player.closeContainer();
        }
        if (parseIndex < parseQueue.size()) {
            parseCurrentName = parseQueue.get(parseIndex);
        } else {
            finishParse();
        }
    }

    private void finishParse() {
        int updated = parseUpdatedCount;
        parseRunning = false;
        parseWaitingResult = false;
        parseIndex = 0;
        parseRetries = 0;
        parseCurrentName = "";
        parseQueue.clear();
        parseUpdatedCount = 0;
        reparseTimer.resetCounter();
        
        if (reparseMinutes.getValue().intValue() <= 0) {
            autoParse.setValue(false);
        }
        msg("§d[AutoParse] §aГотово · обновлено " + updated + " поз.");
    }

    private ParsePrice findLowestUnitPrice(ContainerScreen screen, String targetName) {
        int lowestUnit = -1;
        int bestLot = -1;
        int bestCount = 1;
        int limit = Math.min(45, screen.getMenu().slots.size());
        int matched = 0;

        for (int i = 0; i < limit; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int price = AuctionUtils.getPrice(stack);
            if (price <= 0) {
                continue;
            }
            
            boolean nameOk = matchName(stack, targetName);
            if (!nameOk && !titleLooksLikeSearchFor(screen.getTitle().getString(), targetName)) {
                continue;
            }
            
            if (!nameOk) {
                
                if (!looseMatch(stack, targetName)) {
                    continue;
                }
            }
            matched++;
            int count = Math.max(1, stack.getCount());
            int unit = AuctionUtils.unitPrice(price, count);
            if (lowestUnit < 0 || unit < lowestUnit || (unit == lowestUnit && price < bestLot)) {
                lowestUnit = unit;
                bestLot = price;
                bestCount = count;
            }
        }

        
        if (lowestUnit < 0 && titleLooksLikeSearchFor(screen.getTitle().getString(), targetName)) {
            for (int i = 0; i < limit; i++) {
                Slot slot = screen.getMenu().slots.get(i);
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                int price = AuctionUtils.getPrice(stack);
                if (price <= 0) {
                    continue;
                }
                int count = Math.max(1, stack.getCount());
                int unit = AuctionUtils.unitPrice(price, count);
                if (lowestUnit < 0 || unit < lowestUnit) {
                    lowestUnit = unit;
                    bestLot = price;
                    bestCount = count;
                    matched++;
                }
            }
        }

        if (lowestUnit <= 0 || matched == 0 && bestLot <= 0) {
            return null;
        }
        return new ParsePrice(lowestUnit, bestLot, bestCount);
    }

    private boolean hasPricedLots(ContainerScreen screen) {
        int limit = Math.min(45, screen.getMenu().slots.size());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (!stack.isEmpty() && AuctionUtils.getPrice(stack) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean titleLooksLikeSearchFor(String title, String itemName) {
        if (title == null || itemName == null) {
            return false;
        }
        String t = AuctionUtils.cleanName(title);
        String n = AuctionUtils.cleanName(itemName);
        if (t.isEmpty() || n.isEmpty()) {
            return false;
        }
        if (t.contains(n) || n.contains(t)) {
            return true;
        }
        
        String[] parts = n.split(" ");
        if (parts.length > 0 && parts[0].length() >= 4 && t.contains(parts[0])) {
            return true;
        }
        String search = AuctionUtils.cleanName(toSearchQuery(itemName));
        return !search.isEmpty() && t.contains(search);
    }

    private static String toSearchQuery(String name) {
        if (name == null) {
            return "";
        }
        return switch (name) {
            case "Опыт 15" -> "Опыт с уровнем 15";
            case "Опыт 30" -> "Опыт с уровнем 30";
            case "Опыт 45" -> "Опыт с уровнем 45";
            case "Опыт 50" -> "Опыт с уровнем 50";
            default -> name.replaceAll("[★\\[\\]⚒❄🍹]", "").trim();
        };
    }

    private static String shortName(String name) {
        if (name == null) {
            return "?";
        }
        return name.length() > 28 ? name.substring(0, 28) + "…" : name;
    }

    private boolean matchName(ItemStack stack, String target) {
        if (stack == null || stack.isEmpty() || target == null) {
            return false;
        }
        AutoBuyItem item = AutoBuyManager.get().findByName(target, serverMode.getValue());
        if (item != null) {
            return item.matchesName(stack.getHoverName().getString());
        }
        String a = AuctionUtils.cleanName(stack.getHoverName().getString());
        String b = AuctionUtils.cleanName(target);
        return !a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a));
    }

    private boolean looseMatch(ItemStack stack, String target) {
        String a = AuctionUtils.cleanName(stack.getHoverName().getString());
        String b = AuctionUtils.cleanName(target);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.contains(b) || b.contains(a)) {
            return true;
        }
        for (String token : b.split(" ")) {
            if (token.length() >= 4 && a.contains(token)) {
                return true;
            }
        }
        return false;
    }

    

    private void handleMenuBind() {
        
    }

    private void handleAnarchySwap() {
        if (!anarchySwap.getValue() || parseRunning) {
            return;
        }
        if (nextAnarchyMs <= 0L) {
            nextAnarchyMs = randomAnarchyDelay();
        }
        if (!anarchyTimer.hasTimeElapsed(nextAnarchyMs)) {
            return;
        }
        int n = ThreadLocalRandom.current().nextInt(1, 101);
        if (mc.screen != null && mc.player != null) {
            mc.player.closeContainer();
        }
        mc.player.connection.sendCommand("an" + n);
        msg("§eСвап анархии → /an" + n);
        nextAnarchyMs = randomAnarchyDelay();
        anarchyTimer.resetCounter();
        inAuction = false;
        boughtKeys.clear();
        ahTimer.resetCounter();
    }

    
    private boolean handleFunTimeAnarchy() {
        if (!anarchySwap.getValue() || !serverMode.is("FunTime") || parseRunning || mc.player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        switch (ftSwitch) {
            case IDLE -> {
                if (nextAnarchyMs <= 0L) {
                    nextAnarchyMs = randomAnarchyDelay();
                    anarchyTimer.resetCounter();
                    return false;
                }
                if (!anarchyTimer.hasTimeElapsed(nextAnarchyMs)) {
                    return false;
                }
                pendingAnarchy = FT_ANARCHIES[ThreadLocalRandom.current().nextInt(FT_ANARCHIES.length)];
                stopWalkKeys();
                if (mc.screen != null) {
                    mc.player.closeContainer();
                }
                ftSwitch = FtSwitch.SEND;
                ftPhaseAtMs = now;
                return true;
            }
            case SEND -> {
                if (mc.screen != null) {
                    mc.player.closeContainer();
                    return true;
                }
                mc.player.connection.sendCommand("an" + pendingAnarchy);
                msg("§e[FT] Анархия → /an" + pendingAnarchy);
                ftPhaseAtMs = now;
                ftSwitch = FtSwitch.WAIT;
                return true;
            }
            case WAIT -> {
                if (mc.screen != null) {
                    mc.player.closeContainer();
                }
                if (now - ftPhaseAtMs < 12_000L) {
                    return true;
                }
                ftSwitch = FtSwitch.OPEN_AH;
                return true;
            }
            case OPEN_AH -> {
                if (mc.screen != null) {
                    mc.player.closeContainer();
                    return true;
                }
                if (autoOpenAh.getValue()) {
                    mc.player.connection.sendCommand("ah");
                }
                nextAnarchyMs = randomAnarchyDelay();
                anarchyTimer.resetCounter();
                ftSwitch = FtSwitch.IDLE;
                pendingAnarchy = -1;
                inAuction = false;
                boughtKeys.clear();
                clearPendingBuy();
                return true;
            }
            default -> {
                ftSwitch = FtSwitch.IDLE;
                return false;
            }
        }
    }

    
    private boolean handleSpookyWalk() {
        if (!spWalk.getValue() || !serverMode.is("SpookyTime") || parseRunning || mc.player == null) {
            if (walkState != WalkState.IDLE) {
                stopWalkKeys();
                walkState = WalkState.IDLE;
            }
            return false;
        }
        long now = System.currentTimeMillis();
        double need = Math.max(1.0, spWalkBlocks.getValue());
        double needSq = need * need;

        switch (walkState) {
            case IDLE -> {
                if (now < nextWalkAtMs) {
                    return false;
                }
                if (!(mc.screen instanceof ContainerScreen)) {
                    scheduleNextWalk();
                    return false;
                }
                walkForward = !walkForward;
                mc.player.closeContainer();
                walkCloseAtMs = now;
                walkState = WalkState.CLOSING;
                return true;
            }
            case CLOSING -> {
                if (mc.screen != null) {
                    if (now - walkCloseAtMs > 2000L) {
                        stopWalkKeys();
                        walkState = WalkState.IDLE;
                        scheduleNextWalk();
                    } else {
                        mc.player.closeContainer();
                    }
                    return true;
                }
                walkStartX = mc.player.getX();
                walkStartZ = mc.player.getZ();
                if (walkForward) {
                    mc.options.keyUp.setDown(true);
                } else {
                    mc.options.keyDown.setDown(true);
                }
                walkState = WalkState.MOVING;
                return true;
            }
            case MOVING -> {
                double dx = mc.player.getX() - walkStartX;
                double dz = mc.player.getZ() - walkStartZ;
                if (dx * dx + dz * dz < needSq) {
                    if (walkForward) {
                        mc.options.keyUp.setDown(true);
                    } else {
                        mc.options.keyDown.setDown(true);
                    }
                    return true;
                }
                stopWalkKeys();
                walkState = WalkState.OPENING;
                return true;
            }
            case OPENING -> {
                if (mc.screen != null) {
                    return true;
                }
                if (autoOpenAh.getValue()) {
                    mc.player.connection.sendCommand("ah");
                }
                scheduleNextWalk();
                walkState = WalkState.IDLE;
                return true;
            }
            default -> {
                walkState = WalkState.IDLE;
                return false;
            }
        }
    }

    private long randomAnarchyDelay() {
        if (serverMode.is("FunTime")) {
            long min = Math.max(10L, anarchyMinSec.getValue().longValue()) * 1000L;
            long max = Math.max(min + 1000L, anarchyMaxSec.getValue().longValue() * 1000L);
            return ThreadLocalRandom.current().nextLong(min, max + 1L);
        }
        return ThreadLocalRandom.current().nextLong(5L * 60_000L, 10L * 60_000L + 1L);
    }

    private boolean isLikelyConfirm(String title, int slots) {
        if (AuctionUtils.isAuctionTitle(title) || AuctionUtils.isSearchTitle(title)) {
            return false;
        }
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (t.contains("инвентарь") || t.contains("inventory")) {
            return false;
        }
        return slots == 27 || slots == 36 || slots == 45 || slots == 54 || slots == 63;
    }

    private void clickConfirm(ContainerScreen screen) {
        int syncId = screen.getMenu().containerId;
        
        int confirmSlot = findConfirmSlot(screen);
        if (confirmSlot >= 0) {
            mc.gameMode.handleInventoryMouseClick(syncId, confirmSlot, 0, ClickType.PICKUP, mc.player);
            msg("§a✓ Подтверждение покупки");
            return;
        }
        int[] candidates = {11, 13, 15, 1, 2, 3, 20, 21, 22, 24};
        for (int slot : candidates) {
            if (slot < screen.getMenu().slots.size()) {
                ItemStack stack = screen.getMenu().slots.get(slot).getItem();
                if (!stack.isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(syncId, slot, 0, ClickType.PICKUP, mc.player);
                    msg("§a✓ Подтверждение покупки");
                    return;
                }
            }
        }
        if (screen.getMenu().slots.size() > 1) {
            mc.gameMode.handleInventoryMouseClick(syncId, 1, 0, ClickType.PICKUP, mc.player);
            msg("§a✓ Подтверждение (fallback)");
        }
    }

    private int findConfirmSlot(ContainerScreen screen) {
        int limit = Math.min(screen.getMenu().slots.size(), 54);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = screen.getMenu().slots.get(i).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String n = AuctionUtils.cleanName(stack.getHoverName().getString());
            if (n.contains("купить") || n.contains("подтверд") || n.contains("accept") || n.contains("buy")) {
                return i;
            }
            
            if (stack.getItem() == net.minecraft.world.item.Items.LIME_STAINED_GLASS_PANE
                    || stack.getItem() == net.minecraft.world.item.Items.GREEN_STAINED_GLASS_PANE
                    || stack.getItem() == net.minecraft.world.item.Items.LIME_CONCRETE
                    || stack.getItem() == net.minecraft.world.item.Items.GREEN_CONCRETE) {
                return i;
            }
        }
        return -1;
    }

    private void refreshAuction(ContainerScreen screen) {
        int syncId = screen.getMenu().containerId;
        int slots = screen.getMenu().slots.size();
        int refreshSlot = 49;
        
        if (serverMode.is("HolyWorld")) {
            int containerSize = Math.max(9, slots - 36);
            refreshSlot = Math.max(0, containerSize - 7);
            
            boughtKeys.clear();
        }
        if (refreshSlot < slots) {
            mc.gameMode.handleInventoryMouseClick(syncId, refreshSlot, 0, ClickType.PICKUP, mc.player);
        }
    }

    
    private boolean scanAndBuy(ContainerScreen screen) {
        List<AutoBuyItem> enabled = AutoBuyManager.get().enabledItems(serverMode.getValue());
        if (enabled.isEmpty()) {
            clearPendingBuy();
            return false;
        }

        
        if (mc.player != null && !mc.player.containerMenu.getCarried().isEmpty()) {
            clearPendingBuy();
            return false;
        }

        int syncId = screen.getMenu().containerId;
        int containerSlots = Math.max(0, screen.getMenu().slots.size() - 36);
        int limit = Math.min(45, containerSlots > 0 ? containerSlots : 45);
        limit = Math.min(limit, screen.getMenu().slots.size());

        BuyCandidate best = null;

        for (int i = 0; i < limit; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            
            if (slot.container == mc.player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int price = AuctionUtils.getPrice(stack);
            if (price <= 0) {
                continue; 
            }
            int count = Math.max(1, stack.getCount());
            int unit = AuctionUtils.unitPrice(price, count);
            String name = stack.getHoverName().getString();
            String key = i + "|" + price + "|" + count + "|" + name.hashCode();
            if (boughtKeys.contains(key)) {
                continue;
            }

            
            AutoBuyItem bestItem = null;
            int bestScore = 0;
            for (AutoBuyItem item : enabled) {
                int score = item.matchScore(name);
                if (score <= 0) {
                    continue;
                }
                if (count < item.getMinQty()) {
                    continue;
                }
                
                int maxUnit = item.getBuyPrice();
                if (unit > maxUnit) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestItem = item;
                }
            }
            if (bestItem != null) {
                
                if (best == null
                        || unit < best.unit
                        || (unit == best.unit && bestScore > best.matchScore)
                        || (unit == best.unit && bestScore == best.matchScore && price < best.price)) {
                    best = new BuyCandidate(i, key, bestItem.getName(), price, count, unit, name, bestScore);
                }
            }
        }

        long now = System.currentTimeMillis();

        if (best == null) {
            clearPendingBuy();
            return false;
        }

        
        if (pendingSlot != best.slot || !pendingKey.equals(best.key)) {
            pendingSlot = best.slot;
            pendingKey = best.key;
            pendingName = best.ruleName;
            pendingPrice = best.price;
            pendingCount = best.count;
            pendingSinceMs = now;
            return false; 
        }

        
        if (pendingSlot < 0 || pendingSlot >= screen.getMenu().slots.size()) {
            clearPendingBuy();
            return false;
        }
        Slot live = screen.getMenu().slots.get(pendingSlot);
        ItemStack liveStack = live.getItem();
        if (liveStack.isEmpty()) {
            clearPendingBuy();
            return false;
        }
        int livePrice = AuctionUtils.getPrice(liveStack);
        int liveCount = Math.max(1, liveStack.getCount());
        if (livePrice != pendingPrice || liveCount != pendingCount) {
            
            pendingKey = pendingSlot + "|" + livePrice + "|" + liveCount + "|"
                    + liveStack.getHoverName().getString().hashCode();
            pendingPrice = livePrice;
            pendingCount = liveCount;
            pendingSinceMs = now;
            return false;
        }
        if (livePrice <= 0) {
            clearPendingBuy();
            return false;
        }

        
        long hold = serverMode.is("HolyWorld")
                ? Math.min(60L, buyHoldMs.getValue().longValue())
                : buyHoldMs.getValue().longValue();
        if (now - pendingSinceMs < hold) {
            return false; 
        }

        
        if (now - lastBuyClickMs < 40L) {
            return false;
        }

        
        boolean holy = serverMode.is("HolyWorld");
        ClickType click = holy ? ClickType.PICKUP : ClickType.QUICK_MOVE;
        int clickSlot = pendingSlot;
        mc.gameMode.handleInventoryMouseClick(syncId, clickSlot, 0, click, mc.player);

        boughtKeys.add(pendingKey);
        lastBuyClickMs = now;
        
        long pause = holy
                ? Math.max(postBuyPauseMs.getValue().longValue(), 650L)
                : postBuyPauseMs.getValue().longValue();
        postBuyUntilMs = now + pause;
        msg("§a⚡ Покупка: §f" + pendingName + " §7x" + pendingCount
                + " §aза §e" + pendingPrice + "$ §8(" + best.unit + "$/шт · "
                + (holy ? "click" : "shift") + ")");
        clearPendingBuy();
        
        updateTimer.resetCounter();
        return true;
    }

    private void tickCameraSway() {
        if (!serverMode.is("FunTime") || mc.player == null) return;
        if (!isEnabled() || parseRunning) return;

        cameraSwayTime += mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float t = cameraSwayTime * 0.02f;

        float yawOffset = (float) (Math.sin(t * 1.7f) * 0.4f);
        float pitchOffset = (float) (Math.cos(t * 2.3f) * 0.25f);

        mc.player.setYRot(mc.player.getYRot() + yawOffset);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + pitchOffset, -89f, 89f));
    }

    private long effectiveUpdateDelay() {
        long base = updateDelay.getValue().longValue();
        if (serverMode.is("HolyWorld")) {
            
            return Math.max(50L, Math.min(base, 450L));
        }
        if (serverMode.is("FunTime")) {
            
            return Math.max(base, ThreadLocalRandom.current().nextLong(180L, 320L));
        }
        if (serverMode.is("SpookyTime")) {
            return Math.max(base, ThreadLocalRandom.current().nextLong(220L, 400L));
        }
        return base;
    }

    private long effectiveBuyDelay() {
        long base = buyDelay.getValue().longValue();
        if (serverMode.is("FunTime")) {
            
            return Math.max(50L, base);
        }
        if (serverMode.is("SpookyTime")) {
            return Math.max(50L, base);
        }
        if (serverMode.is("HolyWorld")) {
            
            return Math.max(40L, base);
        }
        return base;
    }

    private record BuyCandidate(int slot, String key, String ruleName, int price, int count, int unit, String rawName, int matchScore) {
    }

    private void msg(String message) {
        if (!notifications.getValue()) {
            return;
        }
        ChatMessage.autobuymessage(message);
    }

    private record ParsePrice(int unitPrice, int lotPrice, int count) {
    }
}
