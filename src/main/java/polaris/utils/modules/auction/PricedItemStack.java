package polaris.utils.modules.auction;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class PricedItemStack {
    private final int price;
    private final ItemStack stack;

    public PricedItemStack(int price, ItemStack stack) {
        this.price = price;
        this.stack = stack;
    }

    public int price() {
        return price;
    }

    public ItemStack stack() {
        return stack;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PricedItemStack other)) {
            return false;
        }
        return price == other.price && Objects.equals(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, stack);
    }
}
