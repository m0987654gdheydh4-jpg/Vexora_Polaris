package polaris.utils.modules.auction;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class AuctionItemListing {
    private final int price;
    private final Slot slot;
    private final ItemStack itemStack;

    public AuctionItemListing(int price, Slot slot, ItemStack itemStack) {
        this.price = price;
        this.slot = slot;
        this.itemStack = itemStack;
    }

    public int price() {
        return price;
    }

    public Slot slot() {
        return slot;
    }

    public ItemStack itemStack() {
        return itemStack;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AuctionItemListing other)) {
            return false;
        }
        return price == other.price
                && Objects.equals(slot, other.slot)
                && Objects.equals(itemStack, other.itemStack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, slot, itemStack);
    }
}
