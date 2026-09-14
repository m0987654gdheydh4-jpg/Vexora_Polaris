package polaris.utils.modules.autobuy;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.utils.modules.autobuy.catalog.AutoBuyItemCategory;

import java.util.Locale;
import java.util.function.Supplier;


public final class AutoBuyItem {
    private final String id;
    private final String name;
    private final AutoBuyItemCategory category;
    private final Supplier<ItemStack> iconFactory;
    private final String[] matchAliases;

    private boolean enabled;
    private int buyPrice;
    private int minQty;

    public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice, Item iconItem) {
        this(name, category, defaultPrice, () -> new ItemStack(iconItem != null ? iconItem : Items.BARRIER), new String[0]);
    }

    public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice, Item iconItem, String... aliases) {
        this(name, category, defaultPrice, () -> new ItemStack(iconItem != null ? iconItem : Items.BARRIER), aliases);
    }

    public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice, Supplier<ItemStack> iconFactory) {
        this(name, category, defaultPrice, iconFactory, new String[0]);
    }

    public AutoBuyItem(String name, AutoBuyItemCategory category, int defaultPrice,
                       Supplier<ItemStack> iconFactory, String... aliases) {
        this.name = name == null ? "" : name;
        this.id = normalizeKey(this.name);
        this.category = category == null ? AutoBuyItemCategory.MISC : category;
        this.iconFactory = iconFactory != null ? iconFactory : () -> new ItemStack(Items.BARRIER);
        this.matchAliases = aliases == null ? new String[0] : aliases;
        this.enabled = false;
        this.buyPrice = Math.max(1, defaultPrice);
        this.minQty = 1;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AutoBuyItemCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(int buyPrice) {
        this.buyPrice = Math.max(1, buyPrice);
    }

    public int getMinQty() {
        return minQty;
    }

    public void setMinQty(int minQty) {
        this.minQty = Math.max(1, minQty);
    }

    public ItemStack createIcon() {
        try {
            ItemStack stack = iconFactory.get();
            return stack == null || stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack.copy();
        } catch (Throwable t) {
            return new ItemStack(Items.BARRIER);
        }
    }

    public String[] getMatchAliases() {
        return matchAliases;
    }

    
    public boolean matchesName(String rawName) {
        String cleaned = AuctionUtils.cleanName(rawName);
        if (cleaned.isEmpty()) {
            return false;
        }
        String self = AuctionUtils.cleanName(name);
        if (!self.isEmpty() && nameMatches(cleaned, self)) {
            return true;
        }
        for (String alias : matchAliases) {
            String a = AuctionUtils.cleanName(alias);
            if (!a.isEmpty() && nameMatches(cleaned, a)) {
                return true;
            }
        }
        return false;
    }

    
    private static boolean nameMatches(String auctionName, String catalogKey) {
        if (auctionName.isEmpty() || catalogKey.isEmpty()) {
            return false;
        }
        if (auctionName.equals(catalogKey)) {
            return true;
        }
        
        if (catalogKey.length() >= 8) {
            if (auctionName.contains(catalogKey)) {
                return true;
            }
            
            return catalogKey.contains(auctionName) && auctionName.length() >= 10;
        }
        
        return false;
    }

    
    public int matchScore(String rawName) {
        if (!matchesName(rawName)) {
            return 0;
        }
        String cleaned = AuctionUtils.cleanName(rawName);
        String self = AuctionUtils.cleanName(name);
        if (cleaned.equals(self)) {
            return 2000 + self.length();
        }
        int best = 0;
        if (!self.isEmpty() && cleaned.contains(self)) {
            
            best = 1000 + self.length();
        }
        for (String alias : matchAliases) {
            String a = AuctionUtils.cleanName(alias);
            if (a.isEmpty()) {
                continue;
            }
            if (cleaned.equals(a)) {
                best = Math.max(best, 2000 + a.length());
            } else if (cleaned.contains(a)) {
                best = Math.max(best, 1000 + a.length());
            }
        }
        return best;
    }

    public static String normalizeKey(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("§.", "")
                .replaceAll("[★\\[\\]⚒❄🍹]", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
