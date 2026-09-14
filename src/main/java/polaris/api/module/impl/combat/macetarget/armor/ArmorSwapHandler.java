package polaris.api.module.impl.combat.macetarget.armor;

import net.minecraft.client.Minecraft;
import polaris.api.module.impl.combat.macetarget.state.MaceState.SwapPhase;
import polaris.utils.inventory.lookup.InventoryUtils;
import polaris.utils.inventory.movement.MovementController;
import polaris.utils.inventory.swap.SwapSettings;

public class ArmorSwapHandler {
    private static final Minecraft MC = Minecraft.getInstance();
    private final MovementController movement = new MovementController();
    private final SwapSettingsProvider settingsProvider;
    private SwapPhase phase = SwapPhase.IDLE;
    private int slot = -1;
    private long phaseStartTime;
    private int currentDelay;

    @FunctionalInterface
    public interface SwapSettingsProvider {
        SwapSettings get();
    }

    public ArmorSwapHandler(SwapSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    public MovementController getMovement() {
        return movement;
    }

    public boolean isActive() {
        return phase != SwapPhase.IDLE;
    }

    public void startSwap(int slot, boolean silent) {
        if (silent) {
            int wrappedSlot = InventoryUtils.wrapSlot(slot);
            InventoryUtils.swap(wrappedSlot, 6);
            InventoryUtils.closeScreen();
            return;
        }
        this.slot = slot;
        SwapSettings settings = settingsProvider.get();
        startPhase(settings.shouldStopMovement() ? SwapPhase.PRE_STOP : SwapPhase.DO_SWAP, settings.shouldStopMovement() ? settings.randomPreStopDelay() : 0);
    }

    public void processLoop() {
        int iterations = 0;
        while (phase != SwapPhase.IDLE && iterations++ < 10 && processTick()) {
            
        }
    }

    private boolean processTick() {
        if (MC.player == null) {
            reset();
            return false;
        }
        long elapsed = System.currentTimeMillis() - phaseStartTime;
        SwapSettings settings = settingsProvider.get();
        switch (phase) {
            case PRE_STOP -> {
                if (elapsed >= currentDelay) {
                    movement.saveState();
                    movement.block();
                    if (settings.shouldStopSprint()) {
                        movement.stopSprint();
                    }
                    startPhase(SwapPhase.STOPPING, 0);
                    return true;
                }
            }
            case STOPPING -> {
                movement.block();
                if (settings.shouldStopSprint()) {
                    movement.stopSprint();
                }
                startPhase(SwapPhase.WAIT_STOP, settings.randomWaitStopDelay());
                return currentDelay == 0;
            }
            case WAIT_STOP -> {
                movement.block();
                if (movement.isPlayerStopped(settings.getVelocityThreshold()) || elapsed >= currentDelay) {
                    startPhase(SwapPhase.PRE_SWAP, settings.randomPreSwapDelay());
                    return currentDelay == 0;
                }
            }
            case PRE_SWAP -> {
                movement.block();
                if (elapsed >= currentDelay) {
                    startPhase(SwapPhase.DO_SWAP, 0);
                    return true;
                }
            }
            case DO_SWAP -> {
                InventoryUtils.swap(InventoryUtils.wrapSlot(slot), 6);
                startPhase(SwapPhase.POST_SWAP, settings.randomPostSwapDelay());
                return currentDelay == 0;
            }
            case POST_SWAP -> {
                if (elapsed >= currentDelay) {
                    if (settings.shouldCloseInventory()) {
                        InventoryUtils.closeScreen();
                    }
                    startPhase(SwapPhase.RESUMING, settings.randomResumeDelay());
                    return currentDelay == 0;
                }
            }
            case RESUMING -> {
                if (elapsed >= currentDelay) {
                    movement.restoreFromCurrent();
                    reset();
                }
            }
        }
        return false;
    }

    private void startPhase(SwapPhase phase, int delay) {
        this.phase = phase;
        phaseStartTime = System.currentTimeMillis();
        currentDelay = delay;
    }

    public void reset() {
        movement.reset();
        phase = SwapPhase.IDLE;
        slot = -1;
        phaseStartTime = 0L;
        currentDelay = 0;
    }

    public void forceRestore() {
        if (movement.isBlocked()) {
            movement.restoreFromCurrent();
        }
    }
}

