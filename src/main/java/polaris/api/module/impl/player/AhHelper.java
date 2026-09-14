package polaris.api.module.impl.player;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.HandledScreenEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.chat.ChatMessage;
import polaris.utils.modules.auction.AuctionFilterUtil;
import polaris.utils.network.Network;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class AhHelper extends Module {
    private static final Pattern CYRILLIC = Pattern.compile("[А-Яа-яЁё]+");

    private final ColorSetting cheapColor = register(new ColorSetting(
            "Cheap Color", "Cheapest lot highlight.", new Color(64, 255, 64, 140)));
    private final ColorSetting goodColor = register(new ColorSetting(
            "Good Color", "Second-best / good lot highlight.", new Color(255, 255, 64, 140)));
    private final ModeSetting serverMode = register(new ModeSetting(
            "Server", "Auction server mode.", "Auto", "Auto", "HolyWorld", "FunTime"));
    private final BooleanSetting autoConfirm = register(new BooleanSetting(
            "Auto Confirm", "Auto /ah sell auto confirm (FunTime).", true));
    private final ModeSetting priceFind = register(new ModeSetting(
            "Price Find", "How to resolve sell price.", "Auto Sell", "Auto Sell", "Search"));
    private final NumberSetting discount = register(new NumberSetting(
            "Discount", "Sell price multiplier from found lot.", 0.95, 0.1, 1.0, 0.01));
    private final BindSetting sellBind = register(new BindSetting(
            "Sell Bind", "Find price and list held item.", KeyBind.NONE));

    private enum State { IDLE, WAIT_SEARCH, WAIT_SELL, WAIT_CONFIRM }

    private State state = State.IDLE;
    private ItemStack heldCopy = ItemStack.EMPTY;
    private String searchQuery = "";
    private long sellPrice = -1L;
    private int priceIndex = 1;
    private long phaseStartMs;
    private long lastActionMs;
    private boolean lastSellPressed;

    public AhHelper() {
        super("AH Helper", "Highlights cheap auction lots and helps sell.", ModuleCategory.PLAYER);
        autoConfirm.visibleWhen(this::isFunTimeLike);
        priceFind.visibleWhen(this::isFunTimeLike);
        sellBind.visibleWhen(() -> true);
    }

    @Override
    protected void onEnable() {
        resetFlow();
    }

    @Override
    protected void onDisable() {
        resetFlow();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        boolean pressed = isBindDown(sellBind.getValue());
        if (pressed && !lastSellPressed && state == State.IDLE) {
            startSellFlow();
        }
        lastSellPressed = pressed;

        switch (state) {
            case WAIT_SEARCH -> pollSearchPrice();
            case WAIT_SELL -> pollSell();
            default -> {
            }
        }
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        if (!event.isReceive() || !(event.getPacket() instanceof ClientboundSystemChatPacket packet)) {
            return;
        }
        if (!autoConfirm.getValue() && state != State.WAIT_CONFIRM) {
            return;
        }
        if (!isFunTimeLike()) {
            return;
        }
        String msg = packet.content().getString();
        if (msg != null && msg.startsWith("▶ Введите /ah sell auto confirm,")) {
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand("ah sell auto confirm");
            }
            state = State.IDLE;
        }
    }

    @SubscribeEvent
    private void onHandledScreen(HandledScreenEvent event) {
        if (!event.isSlotsBackground() || event.getGraphics() == null) {
            return;
        }
        if (!(mc.screen instanceof ContainerScreen screen) || mc.player == null) {
            return;
        }
        String title = screen.getTitle().getString();
        String lower = title.toLowerCase(Locale.ROOT);
        if (!isAuctionScreen(title, lower)) {
            return;
        }

        List<PricedSlot> lots = new ArrayList<>();
        int limit = Math.min(45, screen.getMenu().slots.size());
        for (int i = 0; i < limit; i++) {
            Slot slot = screen.getMenu().getSlot(i);
            if (!slot.hasItem()) {
                continue;
            }
            int price = AuctionFilterUtil.getPrice(slot.getItem());
            if (price <= 0) {
                continue;
            }
            if (loreBlocksPartial(slot.getItem())) {
                continue;
            }
            lots.add(new PricedSlot(slot, price, price / Math.max(1, slot.getItem().getCount())));
        }
        if (lots.isEmpty()) {
            return;
        }

        lots.sort(Comparator.comparingInt(PricedSlot::unitPrice));
        PricedSlot cheap = lots.get(0);
        PricedSlot good = lots.size() > 1 ? lots.get(1) : null;

        
        
        GuiGraphics g = event.getGraphics();
        fillSlot(g, cheap.slot, cheapColor.getValue().getRGB());
        if (good != null && good.slot != cheap.slot) {
            fillSlot(g, good.slot, goodColor.getValue().getRGB());
        }
    }

    private void startSellFlow() {
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        ItemStack hand = mc.player.getMainHandItem();
        if (hand.isEmpty()) {
            ChatMessage.brandmessage("Возьми предмет в руку");
            return;
        }
        heldCopy = hand.copy();
        searchQuery = "";
        sellPrice = -1L;
        priceIndex = isHolyWorldLike() ? 2 : 1;
        phaseStartMs = System.currentTimeMillis();
        lastActionMs = 0L;

        if (isFunTimeLike()) {
            if (priceFind.is("Auto Sell")) {
                mc.getConnection().sendCommand("ah sell auto");
                state = State.WAIT_CONFIRM;
            } else {
                mc.getConnection().sendCommand("ah search");
                state = State.WAIT_SEARCH;
            }
        } else if (isHolyWorldLike()) {
            searchQuery = extractSearchName(heldCopy);
            if (searchQuery.isEmpty()) {
                ChatMessage.brandmessage("Не удалось получить имя предмета для поиска");
                resetFlow();
                return;
            }
            mc.getConnection().sendCommand("ah search " + searchQuery);
            state = State.WAIT_SEARCH;
        } else {
            
            searchQuery = extractSearchName(heldCopy);
            if (searchQuery.isEmpty()) {
                ChatMessage.brandmessage("Не удалось получить имя предмета");
                resetFlow();
                return;
            }
            mc.getConnection().sendCommand("ah search " + searchQuery);
            state = State.WAIT_SEARCH;
        }
    }

    private void pollSearchPrice() {
        if (System.currentTimeMillis() - lastActionMs < 250L) {
            return;
        }
        lastActionMs = System.currentTimeMillis();
        Integer price = findPriceFromOpenAuction();
        if (price == null) {
            if (System.currentTimeMillis() - phaseStartMs > 5000L) {
                ChatMessage.brandmessage("Не удалось найти цену на аукционе");
                resetFlow();
            }
            return;
        }
        sellPrice = Math.max(1L, Math.round(price * discount.getFloat()));
        if (mc.player != null && mc.screen != null) {
            mc.player.closeContainer();
        }
        ChatMessage.brandmessage("Найдена цена: " + sellPrice);
        state = State.WAIT_SELL;
        phaseStartMs = System.currentTimeMillis();
        lastActionMs = 0L;
    }

    private void pollSell() {
        if (sellPrice <= 0 || mc.getConnection() == null) {
            resetFlow();
            return;
        }
        if (mc.screen != null && System.currentTimeMillis() - phaseStartMs < 1500L) {
            return;
        }
        if (System.currentTimeMillis() - lastActionMs < 250L) {
            return;
        }
        lastActionMs = System.currentTimeMillis();
        mc.getConnection().sendCommand("ah sell " + sellPrice);
        resetFlow();
    }

    private Integer findPriceFromOpenAuction() {
        if (mc.screen == null || mc.player == null || mc.player.containerMenu == null) {
            return null;
        }
        List<Integer> unitPrices = new ArrayList<>();
        int limit = Math.min(45, mc.player.containerMenu.slots.size());
        for (int i = 0; i < limit; i++) {
            Slot slot = mc.player.containerMenu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            int price = AuctionFilterUtil.getPrice(stack);
            if (price <= 0 || loreBlocksPartial(stack)) {
                continue;
            }
            unitPrices.add(price / Math.max(1, stack.getCount()));
        }
        if (unitPrices.isEmpty()) {
            return null;
        }
        unitPrices.sort(Comparator.naturalOrder());
        int idx = Math.min(Math.max(0, priceIndex - 1), unitPrices.size() - 1);
        return unitPrices.get(idx) * Math.max(1, heldCopy.getCount());
    }

    private String extractSearchName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String custom = StringUtil.stripColor(stack.getHoverName().getString());
        String fromCustom = onlyCyrillicWords(custom);
        if (!fromCustom.isEmpty()) {
            return fromCustom;
        }
        String key = stack.getItem().getDescriptionId();
        String translated = I18n.get(key);
        return cleanSpaces(translated);
    }

    private static String onlyCyrillicWords(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        Matcher m = CYRILLIC.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(m.group());
        }
        return cleanSpaces(sb.toString());
    }

    private static String cleanSpaces(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static boolean loreBlocksPartial(ItemStack stack) {
        var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore == null) {
            return false;
        }
        String joined = lore.lines().stream()
                .map(c -> c.getString())
                .reduce("", String::concat);
        return joined.contains("только полностью.");
    }

    private static boolean isAuctionScreen(String title, String lower) {
        return title.contains("1A0")
                || lower.contains("аукцион")
                || lower.contains("auction")
                || lower.contains("поиск")
                || lower.contains("ah");
    }

    private boolean isFunTimeLike() {
        if (serverMode.is("FunTime")) {
            return true;
        }
        if (serverMode.is("HolyWorld")) {
            return false;
        }
        return Network.isFunTime() || "CopyTime".equals(Network.getServer());
    }

    private boolean isHolyWorldLike() {
        if (serverMode.is("HolyWorld")) {
            return true;
        }
        if (serverMode.is("FunTime")) {
            return false;
        }
        return Network.isHolyWorld();
    }

    private void fillSlot(GuiGraphics g, Slot slot, int argb) {
        int x = slot.x;
        int y = slot.y;
        g.fill(x, y, x + 16, y + 16, argb);
    }

    private boolean isBindDown(KeyBind bind) {
        if (bind == null || !bind.isBound() || mc.getWindow() == null) {
            return false;
        }
        long handle = mc.getWindow().handle();
        return switch (bind.getType()) {
            case KEYBOARD -> org.lwjgl.glfw.GLFW.glfwGetKey(handle, bind.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, bind.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            case NONE -> false;
        };
    }

    private void resetFlow() {
        state = State.IDLE;
        heldCopy = ItemStack.EMPTY;
        searchQuery = "";
        sellPrice = -1L;
        priceIndex = 1;
        phaseStartMs = 0L;
        lastActionMs = 0L;
        lastSellPressed = false;
    }

    private record PricedSlot(Slot slot, int price, int unitPrice) {
    }
}
