package polaris.api.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionLookPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.PlayerPostUpdateEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.TimerUtil; // твой утиль-класс для setTimer(float) / reset()

/**
 * Timer: Normal / Matrix / Shift / Grim.
 * Портирован с ThunderHack под Polaris API.
 *
 * Энергия (0..1) — общий счётчик заряда для режимов Matrix и Grim.
 * Публичный статический {@link #energy} используется TimerIndicator HUD.
 */
public final class TimerModule extends Module {
    private static TimerModule instance;

    /** Публичный заряд (0..1) — читается из TimerIndicator. */
    public static float energy = 0f;

    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "Timer behaviour.", "Normal",
            "Normal", "Matrix", "Shift", "Grim"));
    private final BooleanSetting old = register(new BooleanSetting(
            "Old", "Older Matrix drain formula.", false));
    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Tick speed multiplier.", 2.0, 0.1, 10.0, 0.01));
    private final NumberSetting shiftTicks = register(new NumberSetting(
            "Shift Ticks", "Ticks to skip in Shift mode.", 10.0, 1.0, 40.0, 1.0));
    private final BindSetting boostKey = register(new BindSetting(
            "Boost Key", "Key held to spend Grim energy.", KeyBind.NONE));
    private final ModeSetting onFlag = register(new ModeSetting(
            "On Flag", "Reaction to server setback.", "Reset",
            "Reset", "Disable", "None"));

    private long cancelTime;
    private double prevX, prevY, prevZ;
    private float prevYaw, prevPitch;

    public TimerModule() {
        super("Timer", "Changes client tick speed.", ModuleCategory.MOVEMENT);
        instance = this;

        old.visibleWhen(() -> mode.is("Matrix"));
        speed.visibleWhen(() -> !mode.is("Shift"));
        shiftTicks.visibleWhen(() -> mode.is("Shift"));
        boostKey.visibleWhen(() -> mode.is("Grim"));
    }

    public static TimerModule getInstance() { return instance; }

    public boolean isEnabledHere() {
        return instance != null && instance.isEnabled();
    }

    // ====================== ЖИЗНЕННЫЙ ЦИКЛ ======================
    @Override
    protected void onEnable() {
        TimerUtil.setTimer(1.0f);
        if (!mode.is("Matrix")) energy = 0f;
        if (mode.is("Grim")) cancelTime = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        TimerUtil.reset();
        energy = 0f;
    }

    // ====================== ТИК ======================
    @SubscribeEvent
    private void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        switch (mode.getValue()) {
            case "Normal" -> TimerUtil.setTimer(speed.getFloat());

            case "Matrix" -> {
                if (!isMoving()) { TimerUtil.setTimer(1.0f); return; }
                TimerUtil.setTimer(Math.max(speed.getFloat(), 1.0f));
                if (energy > 0f) {
                    energy = clamp(energy - ((0.1f * speed.getFloat()) - 0.1f), 0f, 1f);
                } else {
                    setEnabled(false);
                }
            }

            case "Grim" -> {
                boolean boostPressed = isBoostKeyDown();
                boolean canBoost = boostPressed && energy > 0f /* && Grim set-back time > 2000, если у тебя есть такой метод */;
                if (!canBoost) { TimerUtil.setTimer(1.0f); return; }
                TimerUtil.setTimer(Math.max(speed.getFloat(), 1.0f));
                energy = clamp(energy - ((0.0025f * speed.getFloat()) - 0.0025f), 0f, 1f);
            }
        }
    }

    // ====================== SHIFT: ПРОПУСК ТИКОВ ======================
    @SubscribeEvent
    private void onPostPlayerUpdate(PlayerPostUpdateEvent event) {
        if (!mode.is("Shift")) return;
        if (energy < 0.9f) {
            setEnabled(false);
            return;
        }
        event.setCancelled(true);
        event.setIterations(shiftTicks.getInt());
        setEnabled(false);
    }

    // ====================== ПАКЕТЫ ======================
    @SubscribeEvent
    private void onPacketReceive(PacketEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.isSend()) return;

        Object p = event.getPacket();

        // Grim: копим энергию на пинг-пакетах, пока не двигаемся
        if (mode.is("Grim") && p instanceof ClientboundPingPacket) {
            if (System.currentTimeMillis() - cancelTime > 25_000L) {
                cancelTime = System.currentTimeMillis();
                energy = 0f;
                return;
            }
            if (!isMoving()) energy = clamp(energy + 0.005f, 0f, 1f);
            event.setCancelled(true);
        }

        // Сетбэк (PlayerPositionLook) — реакция по onFlag
        if (p instanceof ClientboundPlayerPositionLookPacket) {
            switch (onFlag.getValue()) {
                case "Reset" -> {
                    TimerUtil.setTimer(1.0f);
                    energy = 0f;
                }
                case "Disable" -> {
                    energy = 0f;
                    setEnabled(false);
                }
                case "None" -> { }
            }
        }

        // Grim: сброс на velocity-пакет игрока
        if (mode.is("Grim") && p instanceof ClientboundSetEntityMotionPacket velo
                && velo.getId() == mc.player.getId()) {
            TimerUtil.setTimer(1.0f);
            energy = 0f;
        }
    }

    // ====================== Зарядка Matrix (вызывается из твоего PlayerUpdate/Sync) ======================
    /**
     * Вызывай это из глобального пост-обновления игрока (один раз за тик),
     * чтобы Matrix-энергия заряжалась в простое и тратилась в движении.
     */
    public void onEntitySync() {
        if (!isEnabledHere()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mode.is("Matrix")) {
            boolean still = notMoving();
            float drain = old.getValue() ? 0.005f : 0f;
            energy = clamp(still ? energy + 0.025f : energy - drain, 0f, 1f);
        }

        prevX = mc.player.getX();
        prevY = mc.player.getY();
        prevZ = mc.player.getZ();
        prevYaw = mc.player.getYRot();
        prevPitch = mc.player.getXRot();
    }

    // ====================== УТИЛИТЫ ======================
    private boolean isBoostKeyDown() {
        KeyBind b = boostKey.getValue();
        if (b == null || !b.isBound()) return false;
        return org.lwjgl.glfw.GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), b.getCode())
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static boolean isMoving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.input.forwardImpulse != 0f
                || mc.player.input.leftImpulse != 0f
                || mc.player.getDeltaMovement().horizontalDistanceSqr() > 1e-7;
    }

    private boolean notMoving() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return prevX == mc.player.getX()
                && prevY == mc.player.getY()
                && prevZ == mc.player.getZ()
                && prevYaw == mc.player.getYRot()
                && prevPitch == mc.player.getXRot();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }
}
