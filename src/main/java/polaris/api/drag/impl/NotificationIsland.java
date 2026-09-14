package polaris.api.drag.impl;

import polaris.api.settings.impl.BooleanSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.sounds.SoundManager;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

public final class NotificationIsland extends HudPanel {
    private static final float HEIGHT = 16f;
    private static final float MIN_WIDTH = 60f;
    private static final float PADDING = 6f;
    private static final float FONT_SIZE = 6f;
    private static final long DISPLAY_DURATION = 2000L;

    public enum NotifType {
        SUCCESS(
                new Color(72, 201, 124),
                new Color(25, 138, 79),
                "cataclysm:textures/ui/elements/success.png"
        ),
        ERROR(
                new Color(255, 105, 105),
                new Color(190, 45, 45),
                "cataclysm:textures/ui/elements/error.png"
        ),
        WARNING(
                new Color(255, 205, 87),
                new Color(234, 179, 8),
                "cataclysm:textures/ui/elements/info.png"
        );

        private final Color color1;
        private final Color color2;
        private final String iconPath;

        NotifType(Color color1, Color color2, String iconPath) {
            this.color1 = color1;
            this.color2 = color2;
            this.iconPath = iconPath;
        }

        public Color color1() { return color1; }
        public Color color2() { return color2; }
        public String getIconPath() { return iconPath; }
    }

    private record Notif(String text, NotifType type) {}

    private static volatile Notif active = null;
    private static volatile long activeEndTime = 0L;

    private final Set<String> warnedExpiringEffects = new HashSet<>();
    private final Set<String> activeEffectsCache = new HashSet<>();
    private final BooleanSetting effectWarnings;
    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private float animatedWidth = MIN_WIDTH;

    public NotificationIsland() {
        super("notification_island", "NotificationIsland", 300.0F, 10.0F, MIN_WIDTH, HEIGHT);
        effectWarnings = new BooleanSetting("Предупр. о зельях", "warn_effects", true);
    }

    public static void addNotification(String text, NotifType type) {
        if (text == null || text.isEmpty()) return;
        active = new Notif(text, type == null ? NotifType.WARNING : type);
        activeEndTime = System.currentTimeMillis() + DISPLAY_DURATION;
    }

    public static void notifyModuleToggle(String name, boolean enabled) {
        if (name == null || name.isEmpty()) return;
        String compact = name.replace(" ", "");
        String text = compact + (enabled ? " включен" : " выключен");
        addNotification(text, enabled ? NotifType.SUCCESS : NotifType.ERROR);
    }
    @Override
    public void render() {
        if (mc.player == null) return;

        if (effectWarnings.getValue()) {
            tickExpiringPotionWarnings();
        }

        boolean hasActiveNotif = active != null && System.currentTimeMillis() < activeEndTime;
        boolean isChatOpen = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;

        if (!hasActiveNotif && isChatOpen) {
            active = new Notif("Aura включен", NotifType.SUCCESS);
            hasActiveNotif = true;
        }

        if (!hasActiveNotif && !editPreview()) {
            active = null;
            contentVisible(false);
            return;
        }

        panelAnimation.update();
        panelAnimation.run(hasActiveNotif ? 1.0 : 0.0, 0.24F, hasActiveNotif ? Easings.EXPO_OUT : Easings.EXPO_IN, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        logics();

        Notif renderNotif = active != null ? active : new Notif("Пример уведомления", NotifType.WARNING);
        float textWidth = Render2D.textWidth(TEXT_FONT, renderNotif.text(), FONT_SIZE);

        // 6f отступ + 10f капсула + 5f зазор + ширина текста + 6f отступ
        float targetWidth = PADDING + 10f + 5f + textWidth + PADDING;
        targetWidth = Math.max(targetWidth, MIN_WIDTH);

        animatedWidth = animatedWidth + (targetWidth - animatedWidth) * 0.2f;

        float x = drag.x();
        float y = drag.y();
        size(animatedWidth, HEIGHT);

        int bgAlpha = (int) (235 * alpha);
        drawPanel(x, y, animatedWidth, HEIGHT, bgAlpha, CORNER_MEDIUM);

        float textY = y + (HEIGHT - FONT_SIZE) / 2f - 1f;
        float capsuleSize = 10f;
        float capsuleY = y + (HEIGHT - capsuleSize) / 2f;
        float capsuleX = x + PADDING;

        Color c1 = renderNotif.type().color1();
        Color c2 = renderNotif.type().color2();
        int c1Alpha = withAlpha(c1.getRGB(), (int) (255 * alpha));
        int c2Alpha = withAlpha(c2.getRGB(), (int) (255 * alpha));

        // Рендерим полноценную капсулу с градиентом, как в Dynamic Island
        Render2D.rect(capsuleX, capsuleY, capsuleSize, capsuleSize, capsuleSize / 2f - 1f, c2Alpha, c2Alpha, c2Alpha, c1Alpha);

        // Отрисовка внутренней иконки
        try {
            int iconColor = withAlpha(0xFFFFFFFF, (int) (255 * alpha));
            Render2D.image(renderNotif.type().getIconPath(), capsuleX + 2f, capsuleY + 2f, 6f, 6f, 0f, iconColor);
        } catch (Throwable ignored) {}

        int textColor = withAlpha(TEXT_COLOR, (int) (255 * alpha));
        Render2D.text(TEXT_FONT, renderNotif.text(), capsuleX + capsuleSize + 5f, textY, FONT_SIZE, textColor);
    }
    private void logics() {
        size(animatedWidth, HEIGHT);
    }

    private void tickExpiringPotionWarnings() {
        if (mc.player == null) return;
        activeEffectsCache.clear();

        for (var effect : mc.player.getActiveEffects()) {
            String effectName = effect.getEffect().value().getDisplayName().getString();
            int amp = effect.getAmplifier() + 1;
            String key = effectName + ":" + amp;
            activeEffectsCache.add(key);

            int secondsLeft = effect.getDuration() / 20;

            if (secondsLeft > 0 && secondsLeft <= 10 && !warnedExpiringEffects.contains(key)) {
                addNotification(effectName + " закончится через " + secondsLeft + " секунд", NotifType.WARNING);
                SoundManager.playSoundDirect(SoundManager.LOW, 1.0f, 1.0f);
                warnedExpiringEffects.add(key);
            }
        }

        for (String key : warnedExpiringEffects) {
            if (!activeEffectsCache.contains(key)) {
                String effectName = key.contains(":") ? key.substring(0, key.indexOf(":")) : key;
                addNotification(effectName + " закончился", NotifType.ERROR);
                SoundManager.playSoundDirect(SoundManager.LOW, 1.0f, 1.0f);
            }
        }

        warnedExpiringEffects.removeIf(key -> !activeEffectsCache.contains(key));
    }

    @Override
    protected boolean editPreview() {
        return false;
    }
}
