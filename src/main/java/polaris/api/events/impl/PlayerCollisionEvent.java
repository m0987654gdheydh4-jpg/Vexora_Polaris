package polaris.api.events.impl;

import net.minecraft.world.level.block.Block;
import polaris.api.events.CancellableEvent;

public final class PlayerCollisionEvent extends CancellableEvent {
    private final Block block;

    public PlayerCollisionEvent(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }
}

