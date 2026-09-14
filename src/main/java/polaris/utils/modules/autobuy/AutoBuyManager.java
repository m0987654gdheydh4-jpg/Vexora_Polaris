package polaris.utils.modules.autobuy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import polaris.utils.modules.autobuy.catalog.AutoBuyCatalog;
import polaris.utils.modules.autobuy.catalog.AutoBuyItemCategory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public final class AutoBuyManager {
    private static final AutoBuyManager INSTANCE = new AutoBuyManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_DEBOUNCE_MS = 120L;
    private static final ScheduledExecutorService SAVE_EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AutoBuy-Save");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean savePending = new AtomicBoolean();
    private volatile JsonObject pendingSnapshot;
    private volatile boolean loaded;

    private AutoBuyManager() {
    }

    public static AutoBuyManager get() {
        return INSTANCE;
    }

    public List<AutoBuyItem> allItems() {
        ensureLoaded();
        return AutoBuyCatalog.all();
    }

    public List<AutoBuyItem> itemsForServer(String serverMode) {
        ensureLoaded();
        return AutoBuyCatalog.forServer(serverMode);
    }

    public List<AutoBuyItem> enabledItems() {
        return enabledItems(null);
    }

    
    public List<AutoBuyItem> enabledItems(String serverMode) {
        ensureLoaded();
        List<AutoBuyItem> source = serverMode == null || serverMode.isBlank()
                ? AutoBuyCatalog.all()
                : AutoBuyCatalog.forServer(serverMode);
        List<AutoBuyItem> out = new ArrayList<>();
        for (AutoBuyItem item : source) {
            if (item.isEnabled()) {
                out.add(item);
            }
        }
        return out;
    }

    public List<AutoBuyItem> byCategory(AutoBuyItemCategory category) {
        ensureLoaded();
        return AutoBuyCatalog.byCategory(category);
    }

    public List<AutoBuyItem> byCategoryForServer(AutoBuyItemCategory category, String serverMode) {
        ensureLoaded();
        return AutoBuyCatalog.byCategoryForServer(category, serverMode);
    }

    public AutoBuyItem findByName(String name) {
        return findByName(name, null);
    }

    public AutoBuyItem findByName(String name, String serverMode) {
        ensureLoaded();
        if (name == null) {
            return null;
        }
        String key = AutoBuyItem.normalizeKey(name);
        List<AutoBuyItem> source = serverMode == null || serverMode.isBlank()
                ? AutoBuyCatalog.all()
                : AutoBuyCatalog.forServer(serverMode);
        AutoBuyItem exact = null;
        AutoBuyItem best = null;
        int bestScore = 0;
        for (AutoBuyItem item : source) {
            if (item.getId().equals(key)) {
                exact = item;
                break;
            }
            int score = item.matchScore(name);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return exact != null ? exact : best;
    }

    public void toggle(AutoBuyItem item) {
        if (item == null) {
            return;
        }
        item.setEnabled(!item.isEnabled());
        scheduleSave();
    }

    public void setEnabled(AutoBuyItem item, boolean enabled) {
        if (item == null) {
            return;
        }
        item.setEnabled(enabled);
        scheduleSave();
    }

    public void setBuyPrice(AutoBuyItem item, int price) {
        if (item == null) {
            return;
        }
        item.setBuyPrice(price);
        scheduleSave();
    }

    public void setMinQty(AutoBuyItem item, int qty) {
        if (item == null) {
            return;
        }
        item.setMinQty(qty);
        scheduleSave();
    }

    public void enableAll() {
        enableAll(null);
    }

    public void enableAll(String serverMode) {
        for (AutoBuyItem item : itemsForServerOrAll(serverMode)) {
            item.setEnabled(true);
        }
        scheduleSave();
    }

    public void disableAll() {
        disableAll(null);
    }

    
    public void disableAll(String serverMode) {
        for (AutoBuyItem item : itemsForServerOrAll(serverMode)) {
            item.setEnabled(false);
        }
        scheduleSave();
    }

    public int enabledCount() {
        return enabledCount(null);
    }

    public int enabledCount(String serverMode) {
        int n = 0;
        for (AutoBuyItem item : itemsForServerOrAll(serverMode)) {
            if (item.isEnabled()) {
                n++;
            }
        }
        return n;
    }

    private List<AutoBuyItem> itemsForServerOrAll(String serverMode) {
        ensureLoaded();
        if (serverMode == null || serverMode.isBlank()) {
            return AutoBuyCatalog.all();
        }
        return AutoBuyCatalog.forServer(serverMode);
    }

    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        
        AutoBuyCatalog.all();
        load();
    }

    public void scheduleSave() {
        
        
        
        pendingSnapshot = buildSnapshot();
        
        
        
        if (!savePending.compareAndSet(false, true)) {
            return;
        }
        SAVE_EXEC.schedule(() -> {
            savePending.set(false);
            JsonObject snapshot = pendingSnapshot;
            if (snapshot != null) {
                writeSnapshot(snapshot);
            }
        }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void save() {
        writeSnapshot(buildSnapshot());
    }

    private JsonObject buildSnapshot() {
        JsonObject root = new JsonObject();
        JsonObject items = new JsonObject();
        for (AutoBuyItem item : AutoBuyCatalog.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", item.isEnabled());
            o.addProperty("buyPrice", item.getBuyPrice());
            o.addProperty("minQty", item.getMinQty());
            items.add(item.getId(), o);
        }
        root.add("items", items);
        return root;
    }

    private void writeSnapshot(JsonObject root) {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            System.err.println("[Polaris] AutoBuy save failed: " + exception);
        }
    }

    public synchronized void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el == null || !el.isJsonObject()) {
                return;
            }
            JsonObject root = el.getAsJsonObject();
            if (!root.has("items") || !root.get("items").isJsonObject()) {
                return;
            }
            JsonObject items = root.getAsJsonObject("items");
            for (AutoBuyItem item : AutoBuyCatalog.all()) {
                JsonElement entry = items.get(item.getId());
                if (entry == null || !entry.isJsonObject()) {
                    
                    entry = items.get(item.getName());
                }
                if (entry == null || !entry.isJsonObject()) {
                    continue;
                }
                JsonObject o = entry.getAsJsonObject();
                if (o.has("enabled")) {
                    item.setEnabled(o.get("enabled").getAsBoolean());
                }
                if (o.has("buyPrice")) {
                    item.setBuyPrice(o.get("buyPrice").getAsInt());
                }
                if (o.has("minQty")) {
                    item.setMinQty(o.get("minQty").getAsInt());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("Polaris")
                .resolve("system")
                .resolve("autobuy.json");
    }
}
