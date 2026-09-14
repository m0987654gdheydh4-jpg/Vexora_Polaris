package polaris.api.drag.impl;

import polaris.api.settings.impl.BooleanSetting;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

public final class Info extends HudPanel {
    private static final float HEIGHT = 16f;
    private static final float ICON_SIZE = 9f;
    private static final float PADDING = 6f;
    private static final float FONT_SIZE = 6f;
    private static final float FONT_SMALL = 4.5f;

    private double lastX = 0;
    private double lastZ = 0;
    private double currentBps = 0;
    private double displayBps = 0;
    private double targetBps = 0;
    private long lastBpsTime = 0;


    private static final long CACHE_UPDATE_INTERVAL = 100;

    private String cachedXValue = "0";
    private String cachedYValue = "0";
    private String cachedZValue = "0";
    private String cachedBpsValue = "0.00";
    private long lastCacheUpdate = 0;

    private float cachedXValueWidth = 0;
    private float cachedYValueWidth = 0;
    private float cachedZValueWidth = 0;
    private float cachedBpsValueWidth = 0;

    private float wxText = -1, wyText = -1, wzText = -1;
    private float wbpsText = -1;

    private final BooleanSetting showCoords;
    private final BooleanSetting showBps;
    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    public Info() {
        super("info", "Info", 5.0F, 180.0F, 200.0F, 16.0F);
        showCoords = new BooleanSetting("Показывать координаты", "show_coords", true);
        showBps = new BooleanSetting("Показывать BPS", "show_bps", true);
    }

    @Override
    public void render() {
        if (mc.player == null) return;

        tickBps();

        long currentTime = System.currentTimeMillis();

        boolean hasContent = true;
        panelAnimation.update();
        panelAnimation.run(hasContent ? 1.0 : 0.0, 0.24F, hasContent ? Easings.EXPO_OUT : Easings.EXPO_IN, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        
        if (currentTime - lastCacheUpdate > CACHE_UPDATE_INTERVAL) {
            int playerX = (int) mc.player.getX();
            int playerY = (int) mc.player.getY();
            int playerZ = (int) mc.player.getZ();

            String newX = Integer.toString(playerX);
            String newY = Integer.toString(playerY);
            String newZ = Integer.toString(playerZ);
            if (!newX.equals(cachedXValue)) { cachedXValue = newX; cachedXValueWidth = Render2D.textWidth(TEXT_FONT, newX, FONT_SIZE); }
            if (!newY.equals(cachedYValue)) { cachedYValue = newY; cachedYValueWidth = Render2D.textWidth(TEXT_FONT, newY, FONT_SIZE); }
            if (!newZ.equals(cachedZValue)) { cachedZValue = newZ; cachedZValueWidth = Render2D.textWidth(TEXT_FONT, newZ, FONT_SIZE); }
            String newBps = fastFormat2(roundToStep(displayBps, 0.50));
            if (!newBps.equals(cachedBpsValue)) { cachedBpsValue = newBps; cachedBpsValueWidth = Render2D.textWidth(TEXT_FONT, newBps, FONT_SIZE); }
            lastCacheUpdate = currentTime;
        }

        if (wxText < 0) {
            wxText = Render2D.textWidth(TEXT_FONT, "x", FONT_SIZE);
            wyText = Render2D.textWidth(TEXT_FONT, "y", FONT_SIZE);
            wzText = Render2D.textWidth(TEXT_FONT, "z", FONT_SIZE);
            wbpsText = Render2D.textWidth(TEXT_FONT, "b/s", FONT_SIZE);
        }

        boolean showC = showCoords.getValue();
        boolean showB = showBps.getValue();

        float totalWidth = PADDING + ICON_SIZE + 4;
        if (showC) {
            totalWidth += wxText + 2 + cachedXValueWidth + 12
                    + wyText + 2 + cachedYValueWidth + 12
                    + wzText + 2 + cachedZValueWidth;
        }
        if (showB) {
            if (showC) totalWidth += 12;
            totalWidth += ICON_SIZE + 4 + cachedBpsValueWidth + 2 + wbpsText;
        }
        totalWidth += PADDING;

        float x = drag.x();
        float y = drag.y();

        size(totalWidth, HEIGHT);

        int bgAlpha = (int) (235 * alpha);
        drawPanel(x, y, totalWidth, HEIGHT, bgAlpha, CORNER_MEDIUM);

        float textY = y + (HEIGHT - FONT_SIZE) / 2f - 1f;
        float iconY = y + (HEIGHT - ICON_SIZE) / 2;
        float textX = x + PADDING;

        int accentRgb = withAlpha(accentColor(), (int)(255 * alpha));
        int textRgb = withAlpha(TEXT_COLOR, (int)(255 * alpha));
        int dimRgb = withAlpha(TEXT_SECONDARY, (int)(200 * alpha));

        
        Render2D.text(FontType.MAINMENUSCREEN, "x", textX, iconY, ICON_SIZE, accentRgb);

        float offsetX = textX + ICON_SIZE + 4;

        if (showC) {
            offsetX = drawLabeledValue("x", cachedXValue, offsetX, textY, dimRgb, textRgb);
            offsetX = drawSeparator(offsetX, y, alpha);
            offsetX = drawLabeledValue("y", cachedYValue, offsetX, textY, dimRgb, textRgb);
            offsetX = drawSeparator(offsetX, y, alpha);
            offsetX = drawLabeledValue("z", cachedZValue, offsetX, textY, dimRgb, textRgb);
        }

        if (showB) {
            if (showC) {
                offsetX += 6;
                Render2D.rect(offsetX, y + 4, 1, HEIGHT - 8, 0.5F, withAlpha(BORDER_COLOR, (int)(200 * alpha)));
                offsetX += 6;
            }

            Render2D.text(FontType.MAINMENUSCREEN, "j", offsetX, iconY, ICON_SIZE, accentRgb);
            offsetX += ICON_SIZE + 4;

            offsetX = drawLabeledValue("", cachedBpsValue, offsetX, textY, 0, textRgb);
            offsetX += 2;
            Render2D.text(TEXT_FONT, "b/s", offsetX, textY + 0.7f, FONT_SMALL, withAlpha(dimRgb, (int)(150 * alpha)));
        }
    }

    private void tickBps() {
        long now = System.currentTimeMillis();
        long deltaMs = now - lastBpsTime;

        if (lastBpsTime > 0 && deltaMs > 0 && deltaMs < 1000) {
            double dx = mc.player.getX() - lastX;
            double dz = mc.player.getZ() - lastZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double instantBps = distance / (deltaMs / 1000.0);

            // Мгновенно присваиваем значения без сглаживания
            currentBps = instantBps;
            targetBps = roundToStep(currentBps, 0.50);
            displayBps = targetBps;
        }

        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        lastBpsTime = now;
    }
    private float drawLabeledValue(String label, String value, float x, float y, int labelColor, int valueColor) {
        if (!label.isEmpty()) {
            Render2D.text(TEXT_FONT, label, x, y, FONT_SIZE, labelColor);
            float labelW = Render2D.textWidth(TEXT_FONT, label, FONT_SIZE);
            x += labelW + 2;
        }
        Render2D.text(TEXT_FONT, value, x, y, FONT_SIZE, valueColor);
        float valW = Render2D.textWidth(TEXT_FONT, value, FONT_SIZE);
        return x + valW;
    }

    private float drawSeparator(float x, float y, float alpha) {
        float sepY = y + HEIGHT / 2f - 3f;
        Render2D.rect(x + 4, sepY, 1, 6, 1, withAlpha(BORDER_COLOR, (int)(180 * alpha)));
        return x + 12;
    }

    private double roundToStep(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static String fastFormat2(double v) {
        boolean neg = v < 0;
        if (neg) v = -v;
        long whole = (long) v;
        long frac = (long) ((v - whole) * 100 + 0.5);
        if (frac >= 100) { whole += 1; frac -= 100; }
        StringBuilder sb = new StringBuilder(8);
        if (neg) sb.append('-');
        sb.append(whole).append('.');
        if (frac < 10) sb.append('0');
        sb.append(frac);
        return sb.toString();
    }
}

