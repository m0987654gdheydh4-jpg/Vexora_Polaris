package polaris.api.events.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import polaris.api.events.Event;

public final class HandledScreenEvent implements Event {
    public enum Phase {
        
        SLOTS_BACKGROUND,
        
        POST
    }

    private final Phase phase;
    private final GuiGraphics graphics;
    private final Slot slotHover;
    private final int imageWidth;
    private final int imageHeight;

    public HandledScreenEvent(Slot slotHover, int imageWidth, int imageHeight) {
        this(Phase.POST, null, slotHover, imageWidth, imageHeight);
    }

    public HandledScreenEvent(GuiGraphics graphics, Slot slotHover, int imageWidth, int imageHeight) {
        this(Phase.POST, graphics, slotHover, imageWidth, imageHeight);
    }

    public HandledScreenEvent(Phase phase, GuiGraphics graphics, Slot slotHover, int imageWidth, int imageHeight) {
        this.phase = phase == null ? Phase.POST : phase;
        this.graphics = graphics;
        this.slotHover = slotHover;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isSlotsBackground() {
        return phase == Phase.SLOTS_BACKGROUND;
    }

    public boolean isPost() {
        return phase == Phase.POST;
    }

    public GuiGraphics getGraphics() {
        return graphics;
    }

    public Slot getSlotHover() {
        return slotHover;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
}
