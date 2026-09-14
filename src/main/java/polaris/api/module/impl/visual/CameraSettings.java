package polaris.api.module.impl.visual;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.CameraEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.combat.aura.Angle;
import polaris.api.module.impl.player.FreeLook;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;


public class CameraSettings extends Module {
    private static CameraSettings instance;

    private final BooleanSetting clipThroughBlocks = register(new BooleanSetting(
            "Сквозь блоки", "Камера проходит сквозь блоки в 3-м лице.", true));
    private final NumberSetting distance = register(new NumberSetting(
            "Дистанция F5", "Расстояние камеры в третьем лице.", 4.0, 1.0, 12.0, 0.1));
    private final NumberSetting smoothSpeed = register(new NumberSetting(
            "Скорость F5", "Базовая скорость анимации F5 (вкл/выкл — в модуле Animations).", 0.14, 0.04, 0.5, 0.01));
    private final BooleanSetting worldTransition = register(new BooleanSetting(
            "Анимация мира", "Наклон камеры вниз при смене мира / выходе.", true));
    private final NumberSetting transitionSpeed = register(new NumberSetting(
            "Скорость перехода", "Скорость анимации наклона камеры.", 0.12, 0.03, 0.4, 0.01));

    
    private float smoothDistance;
    
    private float smoothReverse;
    
    private float switchPunch;
    private CameraType lastType;
    private float transitionProgress;
    private boolean hadWorld;
    private boolean transitionActive;

    public CameraSettings() {
        super("Camera Settings", "Custom F5 distance, smooth perspective and world transition.", ModuleCategory.VISUAL);
        instance = this;
        setEnabled(true);
    }

    public static CameraSettings getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        CameraType type = currentType(mc);
        lastType = type;
        smoothDistance = targetDistance(type);
        smoothReverse = targetReverse(type);
        switchPunch = 0.0f;
        transitionProgress = 0.0f;
        hadWorld = mc.level != null;
        transitionActive = false;
    }

    @Override
    protected void onDisable() {
        smoothDistance = 0.0f;
        smoothReverse = 0.0f;
        switchPunch = 0.0f;
        transitionProgress = 0.0f;
        transitionActive = false;
        lastType = null;
    }

    @SubscribeEvent
    public void onCamera(CameraEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            return;
        }

        updateTransition(mc);

        CameraType type = currentType(mc);
        if (lastType == null) {
            lastType = type;
        }

        
        if (type != lastType) {
            if (smoothF5()) {
                switchPunch = 1.0f;
                
                if (isThird(lastType) && isThird(type)
                        && targetReverse(lastType) != targetReverse(type)) {
                    
                } else if (!isThird(lastType) && isThird(type)) {
                    
                    smoothDistance = Math.min(smoothDistance, 0.15f);
                    smoothReverse = targetReverse(type);
                } else if (isThird(lastType) && !isThird(type)) {
                    
                }
            } else {
                smoothDistance = targetDistance(type);
                smoothReverse = targetReverse(type);
            }
            lastType = type;
        }

        float targetDist = targetDistance(type);
        float targetRev = targetReverse(type);

        if (smoothF5()) {
            float factor = Mth.clamp(smoothSpeed.getFloat() * Animations.perspectiveRate(), 0.02f, 1.0f);
            
            float distFactor = factor * (1.0f + switchPunch * 0.35f);
            float revFactor = factor * 0.85f;

            smoothDistance += (targetDist - smoothDistance) * distFactor;
            smoothReverse += (targetRev - smoothReverse) * revFactor;
            switchPunch += (0.0f - switchPunch) * Math.min(1.0f, factor * 1.6f);

            if (Math.abs(targetDist - smoothDistance) < 0.01f) {
                smoothDistance = targetDist;
            }
            if (Math.abs(targetRev - smoothReverse) < 0.005f) {
                smoothReverse = targetRev;
            }
            if (switchPunch < 0.01f) {
                switchPunch = 0.0f;
            }
        } else {
            smoothDistance = targetDist;
            smoothReverse = targetRev;
            switchPunch = 0.0f;
        }

        
        float punchBoost = switchPunch * Math.max(0.35f, distance.getFloat() * 0.18f);
        float finalDistance = Math.max(0.0f, smoothDistance + punchBoost);

        
        boolean needCustom = finalDistance > 0.04f || smoothReverse > 0.01f || isThird(type);
        if (!needCustom) {
            return;
        }

        event.setCameraClip(clipThroughBlocks.getValue());
        event.setDistance(Math.max(0.05f, finalDistance));
        event.setReverseAmount(Mth.clamp(smoothReverse, 0.0f, 1.0f));

        Angle freeAngle = FreeLook.getActiveAngle();
        if (freeAngle != null) {
            event.setAngle(freeAngle);
        } else {
            event.setAngle(new Angle(mc.player.getYRot(), mc.player.getXRot()));
        }

        if (worldTransition.getValue() && transitionProgress > 0.001f) {
            Angle angle = event.getAngle();
            if (angle != null) {
                float tilted = angle.getPitch() + (90.0f - angle.getPitch()) * transitionProgress;
                event.setAngle(new Angle(angle.getYaw(), tilted));
            }
        }

        event.setCancelled(true);
    }

    @Override
    public void onTick(Minecraft client) {
        updateTransition(client);
    }

    private void updateTransition(Minecraft mc) {
        if (!worldTransition.getValue() || mc == null) {
            transitionProgress = 0.0f;
            transitionActive = false;
            hadWorld = mc != null && mc.level != null;
            return;
        }

        boolean hasWorld = mc.level != null && mc.player != null;
        if (hadWorld && !hasWorld) {
            transitionActive = true;
            transitionProgress = 1.0f;
        } else if (!hadWorld && hasWorld) {
            transitionActive = true;
            transitionProgress = 1.0f;
        }
        hadWorld = hasWorld;
        if (!hasWorld) {
            return;
        }

        boolean focused = mc.isWindowActive();
        float target = (transitionActive || !focused) ? 1.0f : 0.0f;
        if (focused && transitionProgress > 0.95f) {
            transitionActive = false;
            target = 0.0f;
        }

        float factor = Mth.clamp(transitionSpeed.getFloat(), 0.01f, 1.0f);
        transitionProgress += (target - transitionProgress) * factor;
        if (Math.abs(target - transitionProgress) < 0.01f) {
            transitionProgress = target;
        }
    }

    public static float applyTransitionPitch(float pitch) {
        CameraSettings settings = instance;
        if (settings == null || !settings.isEnabled() || !settings.worldTransition.getValue()) {
            return pitch;
        }
        float progress = settings.transitionProgress;
        if (progress <= 0.001f) {
            return pitch;
        }
        return pitch + (90.0f - pitch) * progress;
    }

    
    public static boolean isAnimatingDetached() {
        CameraSettings settings = instance;
        return settings != null
                && settings.isEnabled()
                && settings.smoothDistance > 0.04f;
    }

    
    private static boolean smoothF5() {
        return Animations.perspectiveEnabled();
    }

    private float targetDistance(CameraType type) {
        return isThird(type) ? distance.getFloat() : 0.0f;
    }

    private float targetReverse(CameraType type) {
        return type == CameraType.THIRD_PERSON_FRONT ? 1.0f : 0.0f;
    }

    private static boolean isThird(CameraType type) {
        return type != null && !type.isFirstPerson();
    }

    private static CameraType currentType(Minecraft mc) {
        if (mc == null || mc.options == null) {
            return CameraType.FIRST_PERSON;
        }
        return mc.options.getCameraType();
    }
}
