package polaris.utils.render.animation;

import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.HashMap;
import java.util.Map;


public final class TimerTextAnimator {

    private static final long ANIM_MS = 170L;
    private static final float SLIDE_PX = 5.0f;
    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private TimerTextAnimator() {
    }

    public static void draw(FontType font, String key, String text, float x, float y, float size, int color) {
        if (text == null) {
            text = "";
        }
        final String initialText = text;
        long now = System.currentTimeMillis();
        Entry entry = ENTRIES.computeIfAbsent(key, k -> new Entry(initialText));

        if (!text.equals(entry.current)) {
            entry.previous = entry.current;
            entry.current = text;
            entry.changedAt = now;
        }

        entry.lastUse = now;
        cleanup(now);

        float progress = Math.min(1.0f, (now - entry.changedAt) / (float) ANIM_MS);
        int baseAlpha = (color >>> 24) & 0xFF;
        if (baseAlpha == 0) {
            baseAlpha = 255;
        }

        String previous = entry.previous;
        String current = entry.current;
        int maxLen = Math.max(previous.length(), current.length());

        float cursor = x;
        for (int i = 0; i < maxLen; i++) {
            char oldChar = i < previous.length() ? previous.charAt(i) : '\0';
            char newChar = i < current.length() ? current.charAt(i) : '\0';

            float oldW = oldChar == '\0' ? 0.0f : Render2D.textWidth(font, String.valueOf(oldChar), size);
            float newW = newChar == '\0' ? 0.0f : Render2D.textWidth(font, String.valueOf(newChar), size);
            float charW = Math.max(oldW, newW);

            boolean animate = Character.isDigit(oldChar) && Character.isDigit(newChar)
                    && oldChar != newChar && progress < 1.0f;

            if (animate) {
                int oldAlpha = (int) (baseAlpha * (1.0f - progress));
                int newAlpha = (int) (baseAlpha * progress);

                Render2D.text(font, String.valueOf(oldChar), cursor, y - progress * SLIDE_PX, size,
                        replaceAlpha(color, oldAlpha));
                Render2D.text(font, String.valueOf(newChar), cursor, y + (1.0f - progress) * SLIDE_PX, size,
                        replaceAlpha(color, newAlpha));
            } else if (newChar != '\0') {
                Render2D.text(font, String.valueOf(newChar), cursor, y, size, color);
            }

            cursor += charW;
        }
    }

    private static void cleanup(long now) {
        if (ENTRIES.size() < 512) {
            return;
        }
        ENTRIES.entrySet().removeIf(e -> now - e.getValue().lastUse > 10_000L);
    }

    private static int replaceAlpha(int color, int newAlpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, newAlpha)) << 24);
    }

    private static final class Entry {
        private String previous;
        private String current;
        private long changedAt;
        private long lastUse;

        private Entry(String text) {
            this.previous = text;
            this.current = text;
            this.changedAt = System.currentTimeMillis();
            this.lastUse = this.changedAt;
        }
    }
}

