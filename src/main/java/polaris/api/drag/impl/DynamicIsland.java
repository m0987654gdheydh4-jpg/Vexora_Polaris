package polaris.api.drag.impl;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.lwjgl.opengl.GL11;
import polaris.api.settings.impl.BooleanSetting;
import polaris.mixin.accessor.BossHealthOverlayAccessor;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.animation.TimerTextAnimator;
import polaris.utils.render.ui.Render2D;
import polaris.utils.sounds.SoundManager;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class DynamicIsland extends HudPanel {
    private static final float DEFAULT_PILL_H = 15f;
    
    private static final float NOTIFICATION_PILL_H = 20f;
    private static final float DEFAULT_RADIUS = 7f;
    private static final float NOTIFICATION_RADIUS = 9f;
    private static final float ISLAND_Y = 7f;
    private static final float TEXT_SIZE = 7f;
    private static final float TIMER_TEXT_SIZE = 6f;
    
    private static final float TEXT_Y_NUDGE = -0.6f;

    
    private static final float SIDE_GAP = 4f;
    private static final int BAR_COUNT = 4;
    private static final float BAR_W = 2f;
    private static final float BAR_STEP = 2.7f;
    private static final float BAR_MAX_H = 3f + (BAR_COUNT - 1);
    
    private static final float BAR_GROUP_W = (BAR_COUNT - 1) * BAR_STEP + BAR_W;
    private static final float PLANE_SIZE = 8f;
    
    private static final float TIMER_CAP_PAD = 2.75f;
    
    private static final float TIMER_LABEL_GAP = 3.5f;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_MM_SS = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern TIME_DIGITS = Pattern.compile("\\b\\d{1,3}\\b");
    private static final float MIN_PILL_W = 48f;
    
    private static final String DEFAULT_LABEL = "Vexora";

    
    public enum NotificationType {
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
        ),
        INFO(
                new Color(255, 205, 87),
                new Color(234, 179, 8),
                "cataclysm:textures/ui/elements/info.png"
        ),
        DEFAULT(
                new Color(120, 180, 255),
                new Color(60, 100, 180),
                null
        ) {
            @Override
            public Color color1() {
                return HudTheme.current().accentColor();
            }

            @Override
            public Color color2() {
                return mixColor(HudTheme.current().accentColor(), Color.BLACK, 0.45f);
            }
        };

        private final Color color1;
        private final Color color2;
        private final String icon;

        NotificationType(Color color1, Color color2, String icon) {
            this.color1 = color1;
            this.color2 = color2;
            this.icon = icon;
        }

        public Color color() {
            return color1();
        }

        public Color color1() {
            return color1;
        }

        public Color color2() {
            return color2;
        }

        public String icon() {
            return icon;
        }
    }

    private record Notif(String text, NotificationType type, Color overrideColor, String overrideIcon) {
        Notif(String text, NotificationType type) {
            this(text, type, null, null);
        }

        Color color1() {
            return overrideColor != null ? overrideColor : type.color1();
        }

        Color color2() {
            if (overrideColor != null) {
                return mixColor(overrideColor, Color.BLACK, 0.3f);
            }
            return type.color2();
        }

        String icon() {
            return overrideIcon != null ? overrideIcon : type.icon();
        }
    }

    private static volatile Notif active = null;
    private static volatile long activeEnd = 0L;
    
    private static final long SHOW_MS = 1000L;
    private static final long WARNING_SHOW_MS = 2200L;
    private static volatile long activeRevision = 0L;

    private float pillW = MIN_PILL_W;
    private float pillH = DEFAULT_PILL_H;
    private float pillRadius = DEFAULT_RADIUS;
    private Notif displayedNotif = null;
    private Notif pendingNotif = null;
    private boolean switchingNotif = false;
    private long lastSeenRevision = -1L;

    
    private final BakekAnimation widthAnim = new BakekAnimation(500L, MIN_PILL_W, BakekAnimation.SIZE);
    private final BakekAnimation heightAnim = new BakekAnimation(500L, DEFAULT_PILL_H, BakekAnimation.SIZE);
    private final BakekAnimation radiusAnim = new BakekAnimation(500L, DEFAULT_RADIUS, BakekAnimation.SIZE);
    
    private final BakekAnimation statusAnim = new BakekAnimation(500L, 1f, BakekAnimation.SIZE);
    private final BakekAnimation notifAnim = new BakekAnimation(500L, 0f, BakekAnimation.SIZE);
    
    private final BakekAnimation showPingAnim = new BakekAnimation(500L, 0f, BakekAnimation.BAKEK);
    private final BakekAnimation adaptRAnim = new BakekAnimation(300L, 255f, BakekAnimation.LINEAR);
    private final BakekAnimation adaptGAnim = new BakekAnimation(300L, 255f, BakekAnimation.LINEAR);
    private final BakekAnimation adaptBAnim = new BakekAnimation(300L, 255f, BakekAnimation.LINEAR);

    private boolean pingSoundPlayed = false;
    private boolean useDark = false;
    private long lastAdaptSampleAt = 0L;
    private static final ByteBuffer PIXEL_BUFFER = ByteBuffer.allocateDirect(4);

    private final Set<String> warnedExpiringEffects = new HashSet<>();
    private long nextArmorCheckAt = 0L;

    private final BooleanSetting showPingSetting;
    private final BooleanSetting effectWarnings;
    private final BooleanSetting durabilityWarnings;
    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    private static final String AIRPLANE_ICON = "cataclysm:textures/ui/elements/airplane.png";
    private static Boolean airplaneIconExists = null;

    public DynamicIsland() {
        super("dynamic_island", "DynamicIsland", 0F, ISLAND_Y, MIN_PILL_W, DEFAULT_PILL_H);

        showPingSetting = new BooleanSetting("Показывать пинг", "show_ping", true);
        effectWarnings = new BooleanSetting("Предупр. о зельях", "warn_effects", true);
        durabilityWarnings = new BooleanSetting("Предупр. о брони", "warn_durability", true);
    }

    public static void addNotification(String text, NotificationType type) {
        if (text == null || text.isEmpty()) return;
        Notif notif = new Notif(text, type == null ? NotificationType.INFO : type);
        active = notif;
        activeEnd = System.currentTimeMillis() + (type == NotificationType.WARNING ? WARNING_SHOW_MS : SHOW_MS);
        activeRevision++;
    }

    private static boolean iconExists(String path) {
        try {
            var id = net.minecraft.resources.Identifier.tryParse(path);
            if (id == null) return false;
            var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
            return resource.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasAirplaneIcon() {
        if (airplaneIconExists == null) airplaneIconExists = iconExists(AIRPLANE_ICON);
        return airplaneIconExists;
    }

    
    public static void notifyModuleToggle(String name, boolean enabled) {
        if (name == null || name.isEmpty()) return;
        String compact = name.replace(" ", "");
        String text = compact + (enabled ? " включен" : " выключен");
        NotificationType type = enabled ? NotificationType.SUCCESS : NotificationType.ERROR;
        active = new Notif(text, type);
        activeEnd = System.currentTimeMillis() + SHOW_MS;
        activeRevision++;
    }

    @Override
    public void render() {
        if (mc == null || mc.player == null || mc.getWindow() == null) return;
        if (!selected()) {
            contentVisible(false);
            return;
        }

        panelAnimation.update();
        panelAnimation.run(1.0, 0.24F, Easings.EXPO_OUT, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        if (effectWarnings.getValue()) tickExpiringPotionWarnings();
        if (durabilityWarnings.getValue()) tickArmorDurabilityWarnings();

        if (active != null && System.currentTimeMillis() >= activeEnd) {
            active = null;
            activeEnd = 0L;
        }
        if (lastSeenRevision != activeRevision) {
            handleNotificationRevision();
            lastSeenRevision = activeRevision;
        }

        float scaledWidth = Render2D.getFixedScaledWidth();

        boolean hasNotif = active != null && System.currentTimeMillis() < activeEnd;
        if (hasNotif && displayedNotif == null && !switchingNotif) {
            displayedNotif = active;
        }
        if (switchingNotif && notifAnim.get() <= 0.02f) {
            displayedNotif = pendingNotif;
            pendingNotif = null;
            switchingNotif = false;
        }

        boolean shouldShowNotif = !switchingNotif && hasNotif;
        float nAnim = notifAnim.update(shouldShowNotif);
        Notif renderNotif = nAnim > 0.02f || shouldShowNotif ? displayedNotif : null;
        if (!shouldShowNotif && nAnim <= 0.02f && !switchingNotif) {
            displayedNotif = null;
        }
        boolean showNotif = renderNotif != null && nAnim > 0.02f;

        String teleportRaw = getTeleportBossbarText();
        boolean hasTeleport = teleportRaw != null && !teleportRaw.isEmpty();
        String pvpRaw = getPvpBossbarText();
        boolean hasPvp = pvpRaw != null && !pvpRaw.isEmpty();

        
        float sAnim;
        if (showNotif) {
            sAnim = statusAnim.update(1f);
        } else if (hasTeleport || hasPvp) {
            sAnim = statusAnim.update(1f);
        } else {
            sAnim = statusAnim.update(1f);
        }

        float targetW;
        float targetH = showNotif ? NOTIFICATION_PILL_H : DEFAULT_PILL_H;
        float targetRadius = showNotif ? NOTIFICATION_RADIUS : DEFAULT_RADIUS;
        if (showNotif) {
            
            float tw = Render2D.textWidth(TEXT_FONT, renderNotif.text(), TEXT_SIZE);
            targetW = 6f + 10f + 5f + tw + 7f;
        } else if (hasTeleport) {
            targetW = timerTargetWidth("Телепорт", extractPvpTimer(teleportRaw));
            targetH = DEFAULT_PILL_H;
            targetRadius = DEFAULT_RADIUS;
        } else if (hasPvp) {
            targetW = timerTargetWidth("Вы в PVP режиме", extractPvpTimer(pvpRaw));
            targetH = DEFAULT_PILL_H;
            targetRadius = DEFAULT_RADIUS;
        } else {
            float nameW = Render2D.textWidth(TEXT_FONT, DEFAULT_LABEL, TEXT_SIZE);
            targetW = 20f + nameW;
        }
        targetW = Math.max(targetW, MIN_PILL_W);

        
        pillW = widthAnim.update(targetW);
        pillH = heightAnim.update(targetH);
        pillRadius = radiusAnim.update(targetRadius);

        String time = LocalTime.now().format(TIME_FORMATTER);
        float timeW = Render2D.textWidth(TEXT_FONT, time, TEXT_SIZE);

        
        float pillX = scaledWidth / 2f - pillW / 2f;
        float pillY = ISLAND_Y;
        float textY = textBaselineY(pillY, pillH, TEXT_SIZE);
        float totalW = pillW + timeW + 8f;

        size(totalW, pillH + 2f);
        
        drag.position(pillX - timeW - 4f, pillY);
        drag.locked(true);

        float showPing = showPingAnim.get();
        if (showPingSetting.getValue()) {
            
            
            
            float rightExtra = BAR_GROUP_W + SIDE_GAP + 4f * showPing;
            size(totalW + rightExtra, pillH + 2f);
        }

        int bgAlpha = (int) (255 * alpha);

        updateAdaptiveColor();
        
        int adaptTextColor = adaptiveTextColor(bgAlpha, 1f);

        
        Render2D.text(TEXT_FONT, time, pillX - timeW - 4f, textY, TEXT_SIZE, adaptTextColor);

        
        
        
        
        int mainBgAlpha = clamp255((int) (bgAlpha * HudTheme.current().hudOpacity));
        int washA = clamp255((int) (bgAlpha * 0.10f));
        int washRgb = adaptiveWashColor(washA);

        drawGlow(pillX, pillY, pillW, pillH, bgAlpha, pillRadius);
        Render2D.rect(pillX - 1f, pillY - 1f, pillW + 2f, pillH + 2f,
                pillRadius + 1f, washRgb);
        HudRenderCompat.background(pillX, pillY, pillW, pillH, pillRadius,
                Math.max(8f, HudTheme.BLUR_RADIUS * 0.85f),
                2f,
                HudTheme.current().backgroundWithAlpha(mainBgAlpha));
        drawShine(pillX, pillY, pillW, pillH, pillRadius, bgAlpha / 255f);

        if (showNotif) {
            drawNotificationContent(pillX, pillY, renderNotif, bgAlpha, nAnim);
        } else if (hasTeleport) {
            String t = extractPvpTimer(teleportRaw);
            drawTimerStatus(pillX, pillY, new Color(0x1B6FE4), "Телепорт", t, bgAlpha, sAnim);
        } else if (hasPvp) {
            String t = extractPvpTimer(pvpRaw);
            drawTimerStatus(pillX, pillY, new Color(185, 28, 28), "Вы в PVP режиме", t, bgAlpha, sAnim);
        } else {
            drawDefaultContent(pillX, pillY, bgAlpha, sAnim);
        }

        if (showPingSetting.getValue()) {
            drawPing(pillX, pillW, pillY, adaptTextColor, bgAlpha);
        }

        
        lastBottomFixed = pillY + pillH;
        lastRenderMs = System.currentTimeMillis();
    }

    
    private static float textBaselineY(float pillY, float pillH, float fontSize) {
        return centeredTextY(pillY, pillH, fontSize);
    }

    
    private static float centeredTextY(float boxY, float boxH, float fontSize) {
        return boxY + (boxH - fontSize) * 0.5f + TEXT_Y_NUDGE * (fontSize / TEXT_SIZE);
    }

    
    private static String timerSample(String timer) {
        return timer == null || timer.isEmpty() ? "0:00" : timer;
    }

    private float timerTargetWidth(String label, String timer) {
        float labelW = Render2D.textWidth(TEXT_FONT, label, TEXT_SIZE);
        float timerW = Render2D.textWidth(TEXT_FONT, timerSample(timer), TIMER_TEXT_SIZE);
        
        return SIDE_GAP * 2f + TIMER_CAP_PAD * 2f + TIMER_LABEL_GAP + labelW + timerW;
    }

    
    private void drawDefaultContent(float pillX, float pillY, int bgAlpha, float anim) {
        Color accent = HudTheme.current().accentColor();
        Color dark = mixColor(accent, Color.BLACK, 0.55f);
        int darkC = withAlpha(dark.getRGB(), bgAlpha);
        int accentC = withAlpha(accent.getRGB(), bgAlpha);

        float orbSize = 7f;
        float orbX = pillX - 6f + 10f * anim;
        
        float orbY = pillY + (pillH - orbSize) * 0.5f;
        Render2D.rect(orbX, orbY, orbSize, orbSize, 3f, darkC, darkC, darkC, accentC);

        
        Render2D.text(TEXT_FONT, DEFAULT_LABEL,
                pillX + 25f - 10f * anim,
                textBaselineY(pillY, pillH, TEXT_SIZE),
                TEXT_SIZE,
                HudTheme.current().textWithAlpha(bgAlpha));
    }

    
    private void drawNotificationContent(float pillX, float pillY, Notif notif, int bgAlpha, float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        float slide = 6f * (1f - progress);
        int contentAlpha = clamp255((int) (bgAlpha * progress));

        float iconSize = 10f;
        float leftPad = 6f;
        float iconGap = 5f;

        float capsuleX = pillX + leftPad + slide;
        float capsuleY = pillY + (pillH - iconSize) * 0.5f;
        float capsuleR = iconSize / 2f - 1f;

        Color c1 = notif.color1();
        Color c2 = notif.color2();
        int c1Alpha = withAlpha(c1.getRGB(), contentAlpha);
        int c2Alpha = withAlpha(c2.getRGB(), contentAlpha);

        Render2D.rect(capsuleX, capsuleY, iconSize, iconSize, capsuleR, c2Alpha, c2Alpha, c2Alpha, c1Alpha);

        String icon = notif.icon();
        if (icon != null && iconExists(icon)) {
            float ix = capsuleX + 2f;
            float iy = capsuleY + 2f;
            int iconColor = multAlpha(0xFFFFFFFF, contentAlpha / 255f);
            Render2D.image(icon, ix, iy, 6f, 6f, 0f, iconColor);
        }

        Render2D.text(TEXT_FONT, notif.text(),
                capsuleX + iconSize + iconGap,
                textBaselineY(pillY, pillH, TEXT_SIZE),
                TEXT_SIZE,
                HudTheme.current().textWithAlpha(contentAlpha));
    }

    
    private void drawTimerStatus(float pillX, float pillY,
                                 Color capsuleColor, String label, String timer,
                                 int bgAlpha, float anim) {
        String t = timer == null ? "" : timer;
        
        
        float timerW = Render2D.textWidth(TEXT_FONT, timerSample(t), TIMER_TEXT_SIZE);

        float capW = TIMER_CAP_PAD * 2f + timerW;
        float capH = 8f;
        float capX = pillX - 16f + (SIDE_GAP + 16f) * anim;
        float capY = pillY + (pillH - capH) * 0.5f;

        int capAlpha = clamp255((int) (bgAlpha * anim));
        Color cap = new Color(capsuleColor.getRed(), capsuleColor.getGreen(), capsuleColor.getBlue(), capAlpha);
        Render2D.rect(capX, capY, capW, capH, 3f, cap.getRGB());

        int whiteRgb = (clamp255(capAlpha) << 24) | 0xFFFFFF;
        if (!t.isEmpty()) {
            float actualW = Render2D.textWidth(TEXT_FONT, t, TIMER_TEXT_SIZE);
            float tx = capX + (capW - actualW) * 0.5f;
            
            
            float ty = centeredTextY(capY, capH, TIMER_TEXT_SIZE);
            TimerTextAnimator.draw(TEXT_FONT, "island_timer_" + label, t, tx, ty, TIMER_TEXT_SIZE, whiteRgb);
        }

        
        
        float labelX = capX + capW + TIMER_LABEL_GAP;
        Render2D.text(TEXT_FONT, label,
                labelX,
                textBaselineY(pillY, pillH, TEXT_SIZE),
                TEXT_SIZE,
                HudTheme.current().textWithAlpha(clamp255((int) (bgAlpha * anim))));
    }

    
    private void drawPing(float pillX, float pillW, float pillY, int adaptTextColor, int bgAlpha) {
        float planeY = pillY + (pillH - PLANE_SIZE) * 0.5f;
        
        
        float planeX = pillX + pillW + SIDE_GAP + (BAR_GROUP_W - PLANE_SIZE) * 0.5f;
        if (mc.isSingleplayer() || mc.getConnection() == null) {
            drawAirplane(planeX, planeY, adaptTextColor);
            return;
        }

        int ping = 0;
        try {
            var connection = mc.getConnection();
            if (connection != null && mc.player != null) {
                for (var entry : connection.getOnlinePlayers()) {
                    if (entry.getProfile().id().equals(mc.player.getUUID())) {
                        ping = entry.getLatency();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            drawAirplane(planeX, planeY, adaptTextColor);
            return;
        }

        if (ping > 200) {
            if (!pingSoundPlayed) {
                pingSoundPlayed = true;
                SoundManager.playSoundDirect(SoundManager.PLAYERPING, 1.0f, 1.0f);
            }
        } else {
            pingSoundPlayed = false;
        }

        
        float midY = pillY + pillH * 0.5f;
        float textY = textBaselineY(pillY, pillH, TEXT_SIZE);
        
        float barBaseline = midY + BAR_MAX_H * 0.5f;

        
        
        float previousShow = showPingAnim.get();
        float hoverX = pillX + pillW + SIDE_GAP - 2f + 4f * previousShow;
        float hoverY = pillY + 2f;
        float hoverW = BAR_GROUP_W + 4f;
        float hoverH = pillH - 2f;

        double guiScaleX = (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double guiScaleY = (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        float mouseX = (float) (mc.mouseHandler.xpos() * guiScaleX);
        float mouseY = (float) (mc.mouseHandler.ypos() * guiScaleY);
        boolean hovered = mouseX >= hoverX && mouseX <= hoverX + hoverW
                && mouseY >= hoverY && mouseY <= hoverY + hoverH;

        float showPing = showPingAnim.update(hovered);
        float barX = pillX + pillW + SIDE_GAP + 4f * showPing;

        
        if (showPing > 0.01f) {
            Render2D.text(TEXT_FONT, ping + " ms",
                    barX,
                    textY,
                    TEXT_SIZE,
                    multAlpha(adaptTextColor, showPing));
        }

        int[] thresholds = {450, 300, 150, 75};

        
        for (int i = 0; i < BAR_COUNT; i++) {
            float bx = barX + i * BAR_STEP;
            float bh = 3f + i;
            int dim = multAlpha(adaptTextColor, 0.2f * (1f - showPing));
            Render2D.rect(bx, barBaseline - bh, BAR_W, bh, 0.1f, dim);
        }

        
        for (int i = 0; i < BAR_COUNT; i++) {
            if (ping < thresholds[i]) {
                float bx = barX + i * BAR_STEP;
                float bh = 3f + i;
                int lit = multAlpha(adaptTextColor, 1f - showPing);
                Render2D.rect(bx, barBaseline - bh, BAR_W, bh, 0.1f, lit);
            }
        }
    }

    private void drawAirplane(float x, float y, int color) {
        if (!hasAirplaneIcon()) return;
        
        float ay = y; 
        Render2D.image(AIRPLANE_ICON, x, ay, 8f, 8f, 0f, color);
    }

    
    private void updateAdaptiveColor() {
        long now = System.currentTimeMillis();
        if (now - lastAdaptSampleAt >= 500L && mc.screen == null) {
            lastAdaptSampleAt = now;
            try {
                float pixelX = mc.getWindow().getWidth() / 2f;
                
                float pixelY = mc.getWindow().getHeight() - (ISLAND_Y + 5f);
                Color sample = samplePixel(pixelX, pixelY);
                if (sample != null) {
                    float avg = (sample.getRed() + sample.getGreen() + sample.getBlue()) / 3f;
                    useDark = avg > 70f;
                }
            } catch (Throwable ignored) {
            }
        }
        float target = useDark ? 0f : 255f;
        adaptRAnim.update(target);
        adaptGAnim.update(target);
        adaptBAnim.update(target);
    }

    private int adaptiveTextColor(int bgAlpha, float alphaMul) {
        int a = clamp255((int) (bgAlpha * alphaMul));
        int r = clamp255(Math.round(adaptRAnim.get()));
        int g = clamp255(Math.round(adaptGAnim.get()));
        int b = clamp255(Math.round(adaptBAnim.get()));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int adaptiveWashColor(int alpha) {
        
        int c = clamp255(Math.round(adaptRAnim.get()));
        return (alpha << 24) | (c << 16) | (c << 8) | c;
    }

    private static Color samplePixel(float pixelX, float pixelY) {
        try {
            PIXEL_BUFFER.clear();
            GL11.glReadPixels((int) pixelX, (int) pixelY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL_BUFFER);
            int red = PIXEL_BUFFER.get(0) & 0xFF;
            int green = PIXEL_BUFFER.get(1) & 0xFF;
            int blue = PIXEL_BUFFER.get(2) & 0xFF;
            return new Color(red, green, blue);
        } catch (Throwable t) {
            return null;
        }
    }

    private void tickArmorDurabilityWarnings() {
        long now = System.currentTimeMillis();
        if (now < nextArmorCheckAt) return;
        nextArmorCheckAt = now + 1200L;
        strengthNotification();
    }

    private void strengthNotification() {
        if (mc == null || mc.player == null) return;
        var slots = new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET
        };
        String[] names = {"Шлем", "Нагрудник", "Поножи", "Ботинки"};
        for (int i = 0; i < slots.length; i++) {
            var stack = mc.player.getItemBySlot(slots[i]);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            float pct = (float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (pct <= 0.1f && pct > 0f) {
                addNotification(names[i] + " скоро сломается!", NotificationType.WARNING);
                SoundManager.playSoundDirect(SoundManager.LOW, 1.0f, 1.0f);
            }
        }
    }

    private void tickExpiringPotionWarnings() {
        if (mc.player == null) return;
        Set<String> activeKeys = new HashSet<>();
        for (var effect : mc.player.getActiveEffects()) {
            String effectName = effect.getEffect().value().getDisplayName().getString();
            int amp = effect.getAmplifier() + 1;
            String key = effectName + ":" + amp;
            activeKeys.add(key);
            int seconds = effect.getDuration() / 20;
            if (seconds > 0 && seconds <= 30 && !warnedExpiringEffects.contains(key)) {
                addNotification("Зелье " + effectName + " " + amp + " заканчивается (" + seconds + "s)",
                        NotificationType.WARNING);
                warnedExpiringEffects.add(key);
            }
        }
        Iterator<String> it = warnedExpiringEffects.iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (!activeKeys.contains(key)) it.remove();
        }
    }

    
    
    
    

    private static volatile long lastRenderMs;
    private static volatile float lastBottomFixed;

    
    public static boolean isLive() {
        return System.currentTimeMillis() - lastRenderMs < 250L;
    }

    
    public static float bossbarPushDown() {
        if (!isLive()) {
            return 0f;
        }
        float k = Render2D.guiToFixed(1f);
        float bottomGui = Math.abs(k) <= 0.0001f ? lastBottomFixed : lastBottomFixed / k;
        
        return Math.max(0f, bottomGui + 4f - 12f);
    }

    
    public static boolean isIntegratedBossbar(LerpingBossEvent bar) {
        if (!isLive() || bar == null || bar.getName() == null) {
            return false;
        }
        String raw = net.minecraft.ChatFormatting.stripFormatting(bar.getName().getString());
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        String low = raw.toLowerCase(Locale.ROOT);
        return isPvpText(low) || isTeleportText(low);
    }

    private static boolean isPvpText(String low) {
        return low.contains("pvp") || low.contains("пвп") || low.contains("режим боя")
                || low.contains("в бою") || low.contains("бой:") || low.contains("combat");
    }

    private static boolean isTeleportText(String low) {
        return low.contains("телепортац") || low.contains("телепорт")
                || low.contains("перемещ") || low.contains("teleport") || low.contains("tp ");
    }

    private String getPvpBossbarText() {
        if (mc == null || mc.gui == null) return null;
        try {
            var bossOverlay = mc.gui.getBossOverlay();
            if (bossOverlay == null) return null;
            Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) bossOverlay).cataclysm$getEvents();
            if (events == null) return null;
            for (var entry : events.entrySet()) {
                var bar = entry.getValue();
                if (bar == null || bar.getName() == null) continue;
                String raw = net.minecraft.ChatFormatting.stripFormatting(bar.getName().getString());
                if (raw == null || raw.isEmpty()) continue;
                String low = raw.toLowerCase(Locale.ROOT);
                if (isPvpText(low)) {
                    return raw;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getTeleportBossbarText() {
        if (mc == null || mc.gui == null) return null;
        try {
            var bossOverlay = mc.gui.getBossOverlay();
            if (bossOverlay == null) return null;
            Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) bossOverlay).cataclysm$getEvents();
            if (events == null) return null;
            for (var entry : events.entrySet()) {
                var bar = entry.getValue();
                if (bar == null || bar.getName() == null) continue;
                String raw = net.minecraft.ChatFormatting.stripFormatting(bar.getName().getString());
                if (raw == null || raw.isEmpty()) continue;
                String low = raw.toLowerCase(Locale.ROOT);
                if (isTeleportText(low)) {
                    return raw;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractPvpTimer(String text) {
        if (text == null) return null;
        Matcher mmss = TIME_MM_SS.matcher(text);
        if (mmss.find()) return mmss.group();
        Matcher digits = TIME_DIGITS.matcher(text);
        if (digits.find()) return digits.group() + "s";
        return null;
    }

    private void handleNotificationRevision() {
        Notif next = active;
        if (next == null) {
            pendingNotif = null;
            switchingNotif = false;
            return;
        }
        if (displayedNotif == null || notifAnim.get() <= 0.02f) {
            displayedNotif = next;
            pendingNotif = null;
            switchingNotif = false;
            return;
        }
        if (sameNotif(displayedNotif, next)) {
            pendingNotif = null;
            switchingNotif = false;
            return;
        }
        pendingNotif = next;
        switchingNotif = true;
    }

    private static boolean sameNotif(Notif first, Notif second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return Objects.equals(first.text(), second.text())
                && Objects.equals(first.icon(), second.icon())
                && first.type() == second.type();
    }

    private static Color mixColor(Color c1, Color c2, float ratio) {
        int r = (int) (c1.getRed() * (1f - ratio) + c2.getRed() * ratio);
        int g = (int) (c1.getGreen() * (1f - ratio) + c2.getGreen() * ratio);
        int b = (int) (c1.getBlue() * (1f - ratio) + c2.getBlue() * ratio);
        return new Color(clamp255(r), clamp255(g), clamp255(b));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int multAlpha(int color, float alphaMult) {
        int a = (color >>> 24) & 0xFF;
        int newAlpha = clamp255((int) (a * alphaMult));
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }
}
