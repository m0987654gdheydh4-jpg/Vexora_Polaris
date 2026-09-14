package polaris.api.drag.impl;

import net.minecraft.client.multiplayer.ServerData;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;

import java.awt.Color;

public final class Watermark extends HudPanel {
    private static final float HEIGHT = 16f;
    private static final float MIN_WIDTH = 120f;
    private static final float PADDING = 6f;
    private static final float FONT_SIZE = 6f;

    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private float animatedWidth = MIN_WIDTH;

    private float wVexora = -1;

    public Watermark() {
        super("watermark", "Watermark", 5.0F, 5.0F, MIN_WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        if (mc.player == null || mc.level == null) {
            contentVisible(false);
            return;
        }

        boolean hasContent = true;
        panelAnimation.update();
        panelAnimation.run(hasContent ? 1.0 : 0.0, 0.24F, Easings.EXPO_OUT, true);
        float alpha = panelAnimation.get();
        contentVisible(alpha > 0.01F);
        if (alpha <= 0.01F) return;

        logics();

        // Считываем ник игрока
        String nameStr = mc.player.getGameProfile().name();
        float wName = Render2D.textWidth(TEXT_FONT, nameStr, FONT_SIZE);

        // Считываем IP сервера
        ServerData serverData = mc.getConnection().getServerData();
        String ipStr = (serverData != null && serverData.ip != null) ? serverData.ip : "Одиночная игра";
        float wIp = Render2D.textWidth(TEXT_FONT, ipStr, FONT_SIZE);

        // Считываем текущий FPS
        String fpsStr = mc.getFps() + " FPS";
        float wFps = Render2D.textWidth(TEXT_FONT, fpsStr, FONT_SIZE);

        // Считываем текущий пинг
        // Точечная замена получения пинга под ReallyWorld
        int pingValue = 0;
        try {
            if (mc.getConnection() != null && mc.player != null) {
                // Извлекаем по ID профиля, а не по UUID сущности — этот способ не сбрасывается в 0
                var entry = mc.getConnection().getPlayerInfo(mc.player.getGameProfile().id());
                if (entry != null) {
                    pingValue = entry.getLatency();
                }
            }
        } catch (Throwable ignored) {}

        String pingStr = pingValue + "ms";
        float wPing = Render2D.textWidth(TEXT_FONT, pingStr, FONT_SIZE);

        // Кешируем статические названия строк
        if (wVexora < 0) {
            wVexora = Render2D.textWidth(TEXT_FONT, "Vexora", FONT_SIZE);
        }

        // Динамический расчёт ширины плашки под размеры контента (без иконок)
        float totalWidth = PADDING
                + wVexora + 8f + 1f + 8f // Логотип + Разделитель
                + wName + 8f + 1f + 8f   // Ник + Разделитель
                + wIp + 8f + 1f + 8f     // IP + Разделитель
                + wFps + 8f + 1f + 8f    // FPS + Разделитель
                + wPing                  // Пинг
                + PADDING;

        animatedWidth = animatedWidth + (totalWidth - animatedWidth) * 0.2f;

        float x = drag.x();
        float y = drag.y();
        size(animatedWidth, HEIGHT);

        int bgAlpha = (int) (235 * alpha);
        drawPanel(x, y, animatedWidth, HEIGHT, bgAlpha, CORNER_MEDIUM);
        float textY = y + (HEIGHT - FONT_SIZE) / 2f - 1f;
        float currentX = x + PADDING;

        int accentRgb = withAlpha(accentColor(), (int) (255 * alpha));
        int textRgb = withAlpha(TEXT_COLOR, (int) (255 * alpha));

        // 1. Отрисовка названия Vexora
        Render2D.text(TEXT_FONT, "Vexora", currentX, textY, FONT_SIZE, accentRgb);
        currentX += wVexora + 8f;
        currentX = drawInlineSeparator(currentX, y, alpha);

        // 2. Отрисовка ника игрока
        Render2D.text(TEXT_FONT, nameStr, currentX, textY, FONT_SIZE, textRgb);
        currentX += wName + 8f;
        currentX = drawInlineSeparator(currentX, y, alpha);

        // 3. Отрисовка IP
        Render2D.text(TEXT_FONT, ipStr, currentX, textY, FONT_SIZE, textRgb);
        currentX += wIp + 8f;
        currentX = drawInlineSeparator(currentX, y, alpha);

        // 4. Отрисовка FPS
        Render2D.text(TEXT_FONT, fpsStr, currentX, textY, FONT_SIZE, textRgb);
        currentX += wFps + 8f;
        currentX = drawInlineSeparator(currentX, y, alpha);

        // 5. Отрисовка адаптивного текста пинга
        int pingColor;
        if (pingValue > 300) {
            pingColor = ColorUtil.rgba(255, 50, 50, (int) (255 * alpha)); // Красный
        } else if (pingValue > 150) {
            pingColor = ColorUtil.rgba(255, 200, 50, (int) (255 * alpha)); // Жёлтый
        } else {
            pingColor = ColorUtil.rgba(50, 255, 50, (int) (255 * alpha)); // Зелёный
        }

        Render2D.text(TEXT_FONT, pingStr, currentX, textY, FONT_SIZE, pingColor);
    }

    private void logics() {
        size(animatedWidth, HEIGHT);
    }

    private float drawInlineSeparator(float x, float y, float alpha) {
        float sepY = y + HEIGHT / 2f - 3f;
        Render2D.rect(x, sepY, 1f, 6f, 0.5f, withAlpha(BORDER_COLOR, (int) (150 * alpha)));
        return x + 9f;
    }
}
