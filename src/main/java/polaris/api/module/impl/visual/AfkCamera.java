package polaris.api.module.impl.visual;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.CameraEvent;
import polaris.api.events.impl.CameraPositionEvent;
import polaris.api.events.impl.InputEvent;
import polaris.api.events.impl.KeyEvent;
import polaris.api.events.impl.MouseRotationEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.player.FreeCam;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;


public final class AfkCamera extends Module {
    private static AfkCamera instance;

    private final NumberSetting delayMinutes = register(new NumberSetting(
            "AFK (мин)", "Через сколько минут бездействия включить камеру.", 3.0, 1.0, 30.0, 1.0));
    private final NumberSetting orbitSpeed = register(new NumberSetting(
            "Скорость", "Скорость вращения камеры (град/сек).", 8.0, 1.0, 40.0, 0.5));
    private final NumberSetting distance = register(new NumberSetting(
            "Дистанция", "Радиус орбиты вокруг игрока.", 5.5, 2.0, 16.0, 0.1));
    private final NumberSetting height = register(new NumberSetting(
            "Высота", "Смещение камеры по Y относительно глаз.", 0.6, -1.5, 4.0, 0.1));
    private final NumberSetting lookPitch = register(new NumberSetting(
            "Наклон", "Дополнительный наклон взгляда вниз (+).", 8.0, -20.0, 35.0, 1.0));
    private final BooleanSetting hideHud = register(new BooleanSetting(
            "Скрыть HUD", "Прячет HUD / хотбар / боссбар как F1.", true));
    private final BooleanSetting onlyWhenStanding = register(new BooleanSetting(
            "Только стоя", "Не запускать если игрок двигается / в воздухе / в GUI.", true));

    private long lastActivityMs;
    private boolean cinematic;
    private boolean savedHideGui;
    private boolean hideGuiTouched;
    private CameraType savedCameraType;

    
    private long cinematicStartNs;
    private float startOrbitDeg;
    private float fadeIn;
    private long fadeStartNs;

    private Angle lookAngle;
    private float smoothYaw;
    private float smoothPitch;
    private boolean lookInit;

    public AfkCamera() {
        super("AFK Camera", "Cinematic orbit camera after AFK idle time.", ModuleCategory.VISUAL);
        instance = this;
        lastActivityMs = System.currentTimeMillis();
    }

    public static AfkCamera getInstance() {
        return instance;
    }

    public static boolean isCinematicActive() {
        return instance != null && instance.isEnabled() && instance.cinematic;
    }

    public static boolean shouldHideAllHud() {
        return isCinematicActive() && instance.hideHud.getValue();
    }

    public static Angle getActiveAngle() {
        AfkCamera cam = instance;
        if (cam == null || !cam.isEnabled() || !cam.cinematic || cam.lookAngle == null) {
            return null;
        }
        return cam.lookAngle;
    }

    @Override
    protected void onEnable() {
        resetIdle();
        stopCinematic(false);
    }

    @Override
    protected void onDisable() {
        stopCinematic(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent.Post event) {
        Minecraft mc = event.getClient();
        if (mc.player == null || mc.level == null) {
            stopCinematic(true);
            resetIdle();
            return;
        }

        FreeCam freeCam = FreeCam.getInstance();
        if (freeCam != null && freeCam.isEnabled()) {
            if (cinematic) {
                stopCinematic(true);
            }
            resetIdle();
            return;
        }

        if (cinematic) {
            if (isPlayerActive(mc) || hasMovementInput(mc) || mc.screen != null) {
                stopCinematic(true);
                resetIdle();
                return;
            }
            if (hideHud.getValue() && mc.options != null && !mc.options.hideGui) {
                mc.options.hideGui = true;
            }
            return;
        }

        if (isPlayerActive(mc) || hasMovementInput(mc) || mc.screen != null) {
            resetIdle();
            return;
        }

        if (onlyWhenStanding.getValue()) {
            if (!mc.player.onGround() || mc.player.isPassenger() || mc.player.isSwimming() || mc.player.isFallFlying()) {
                resetIdle();
                return;
            }
        }

        long needMs = (long) (delayMinutes.getValue() * 60_000.0);
        if (System.currentTimeMillis() - lastActivityMs >= needMs) {
            startCinematic(mc);
        }
    }

    @SubscribeEvent
    public void onKey(KeyEvent event) {
        if (event.action() != 0) {
            if (cinematic) {
                stopCinematic(true);
            }
            resetIdle();
        }
    }

    @SubscribeEvent
    public void onMouse(MouseRotationEvent event) {
        if (Math.abs(event.getCursorDeltaX()) > 0.01 || Math.abs(event.getCursorDeltaY()) > 0.01) {
            if (cinematic) {
                stopCinematic(true);
            }
            resetIdle();
        }
    }

    @SubscribeEvent
    public void onInput(InputEvent event) {
        if (Math.abs(event.forward()) > 0.01f || Math.abs(event.sideways()) > 0.01f
                || event.getInput().jump() || event.getInput().shift()) {
            if (cinematic) {
                stopCinematic(true);
            }
            resetIdle();
        }
    }

    @SubscribeEvent
    public void onCameraPosition(CameraPositionEvent event) {
        if (!cinematic) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        float pt = mc.getDeltaTracker() != null
                ? Mth.clamp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0f, 1f)
                : 1f;

        
        double elapsedSec = (System.nanoTime() - cinematicStartNs) / 1_000_000_000.0;
        float orbitDeg = startOrbitDeg + (float) (elapsedSec * orbitSpeed.getFloat());
        double yawRad = Math.toRadians(orbitDeg);

        double radius = distance.getValue();
        double yOff = height.getValue();
        Vec3 focus = mc.player.getEyePosition(pt).add(0.0, -0.15, 0.0);

        double cx = focus.x + (-Math.sin(yawRad) * radius);
        double cz = focus.z + (Math.cos(yawRad) * radius);
        double cy = focus.y + yOff;
        Vec3 orbitPos = new Vec3(cx, cy, cz);

        
        double fadeElapsed = (System.nanoTime() - fadeStartNs) / 1_000_000_000.0;
        fadeIn = Mth.clamp((float) (fadeElapsed / 1.6), 0f, 1f);
        float fade = smoothstep(fadeIn);
        Vec3 eyes = mc.player.getEyePosition(pt);
        Vec3 finalPos = eyes.lerp(orbitPos, fade);
        event.setPos(finalPos);

        
        double dx = focus.x - finalPos.x;
        double dy = focus.y - finalPos.y;
        double dz = focus.z - finalPos.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = (float) (-(Mth.atan2(dy, horiz) * (180.0 / Math.PI)));
        targetPitch = Mth.clamp(targetPitch + lookPitch.getFloat(), -89.0f, 89.0f);

        if (!lookInit) {
            smoothYaw = targetYaw;
            smoothPitch = targetPitch;
            lookInit = true;
        } else {
            float yawDelta = Mth.wrapDegrees(targetYaw - smoothYaw);
            smoothYaw = smoothYaw + yawDelta * 0.22f;
            smoothPitch = smoothPitch + (targetPitch - smoothPitch) * 0.22f;
        }
        lookAngle = new Angle(smoothYaw, smoothPitch);

        if (mc.options != null) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    @SubscribeEvent
    public void onCamera(CameraEvent event) {
        if (!cinematic || lookAngle == null) {
            return;
        }
        event.setAngle(lookAngle);
        event.setDistance(0.05f);
        event.setReverseAmount(0f);
        event.setCameraClip(true);
        event.setCancelled(true);
    }

    private void startCinematic(Minecraft mc) {
        if (cinematic || mc.player == null) {
            return;
        }
        cinematic = true;
        cinematicStartNs = System.nanoTime();
        fadeStartNs = cinematicStartNs;
        fadeIn = 0f;
        startOrbitDeg = mc.player.getYRot() + 180.0f;
        lookInit = false;
        lookAngle = null;

        if (mc.options != null) {
            savedCameraType = mc.options.getCameraType();
            savedHideGui = mc.options.hideGui;
            if (hideHud.getValue()) {
                mc.options.hideGui = true;
                hideGuiTouched = true;
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private void stopCinematic(boolean restoreGui) {
        if (!cinematic) {
            cinematic = false;
            lookAngle = null;
            lookInit = false;
            fadeIn = 0f;
            return;
        }
        cinematic = false;
        lookAngle = null;
        lookInit = false;
        fadeIn = 0f;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            if (restoreGui && hideGuiTouched) {
                mc.options.hideGui = savedHideGui;
            }
            if (savedCameraType != null) {
                mc.options.setCameraType(savedCameraType);
            }
        }
        hideGuiTouched = false;
        savedCameraType = null;
    }

    private void resetIdle() {
        lastActivityMs = System.currentTimeMillis();
    }

    private static boolean isPlayerActive(Minecraft mc) {
        if (mc.player == null) {
            return true;
        }
        Vec3 vel = mc.player.getDeltaMovement();
        if (vel.horizontalDistanceSqr() > 1.0E-4 || Math.abs(vel.y) > 0.08) {
            return true;
        }
        return mc.player.swinging || mc.player.isUsingItem() || mc.player.hurtTime > 0;
    }

    private static boolean hasMovementInput(Minecraft mc) {
        if (mc.player == null || mc.player.input == null) {
            return false;
        }
        var input = mc.player.input.getMoveVector();
        return Math.abs(input.x) > 0.01f || Math.abs(input.y) > 0.01f
                || mc.player.input.keyPresses.jump()
                || mc.player.input.keyPresses.shift();
    }

    private static float smoothstep(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
