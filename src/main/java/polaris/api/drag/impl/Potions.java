package polaris.api.drag.impl;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.animation.TimerTextAnimator;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ScissorUtil;
import polaris.utils.render.ui.Render2DCoordinateSpace;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Potions extends HudPanel {
    private static final float ROW_STEP = 14.0F;
    private static final float HEADER_HEIGHT = 20f;
    private static final float SUBHEADER_HEIGHT = 11f;
    private static final float ICON_SIZE = 9f;
    private static final float PANEL_ANIM = 0.30F;
    private static final int LOW_DURATION_TICKS = 300;
    private static final List<Holder<MobEffect>> PREVIEW_EFFECTS = List.of(MobEffects.SPEED);
    private static final Comparator<MobEffectInstance> EFFECT_NAME_COMPARATOR = Comparator.comparing(effect -> effect.getEffect().value().getDisplayName().getString());
    private static final Comparator<RowState> ROW_STATE_COMPARATOR = Comparator.comparingDouble(RowState::offset);

    private final List<RowEntry> rowEntries = new ArrayList<>();
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final Map<String, Integer> maxDurations = new HashMap<>();
    private final Set<String> activeEffectIds = new HashSet<>();
    private final List<RowState> rowStates = new ArrayList<>();
    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private final SmoothAnimation iconAlphaAnimation = new SmoothAnimation();
    private boolean iconAlphaForward = true;
    private long previewIconSecond = -1L;
    private Holder<MobEffect> previewEffect = MobEffects.SPEED;

    public Potions() {
        super("potions", "Potions", 300.0F, 100.0F, 110.0F, 23.0F);
    }

    @Override
    public void render() {
        if (!selected()) {
            contentVisible(false);
            return;
        }
        PotionsState state = logics();
        if (state == null) return;
        renderPotions(state);
    }

    private PotionsState logics() {
        effects.clear();
        activeEffectIds.clear();
        if (mc.player != null) {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                if (effect.showIcon()) {
                    effects.add(effect);
                }
            }
        }
        if (effects.size() > 1) effects.sort(EFFECT_NAME_COMPARATOR);

        for (MobEffectInstance effect : effects) {
            String id = getEffectId(effect);
            activeEffectIds.add(id);
            int currentDuration = effect.getDuration();
            if (!maxDurations.containsKey(id) || currentDuration > maxDurations.get(id)) {
                maxDurations.put(id, currentDuration);
            }
        }

        boolean preview = effects.isEmpty() && editPreview();
        boolean targetVisible = !effects.isEmpty() || preview;

        panelAnimation.update();
        panelAnimation.run(targetVisible ? 1.0 : 0.0, PANEL_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        iconAlphaAnimation.update();
        if (!iconAlphaAnimation.isAlive()) {
            iconAlphaAnimation.run(iconAlphaForward ? 1.0 : 0.0, 1.55, Easings.EXPO_IN_OUT);
            iconAlphaForward = !iconAlphaForward;
        }

        for (RowEntry entry : rowEntries) {
            entry.active = false;
            entry.alpha.update();
            entry.y.update();
            entry.slide.update();
        }

        int targetRows = 0;
        if (preview) {
            updatePreviewEffects();
            RowEntry entry = row("__preview", "Пример эффекта 10", "0:00", MobEffects.SPEED, 0.5f, 0.0F);
            markRowActive(entry, 0.0F);
            targetRows = 1;
        } else {
            for (MobEffectInstance effect : effects) {
                String id = getEffectId(effect);
                float targetY = targetRows * ROW_STEP;
                int duration = effect.getDuration();
                float progress = 1.0f;
                if (duration != -1 && maxDurations.containsKey(id)) {
                    int maxDur = maxDurations.get(id);
                    if (maxDur > 0) progress = (float) duration / maxDur;
                }
                RowEntry entry = row(id, getDisplayName(effect), formatDuration(duration), effect.getEffect(), progress, targetY);
                markRowActive(entry, targetY);
                targetRows++;
            }
        }

        for (RowEntry entry : rowEntries) {
            if (!entry.active) {
                entry.alpha.run(0.0, ROW_LEAVE, Easings.EXPO_IN, true);
                entry.slide.run(ROW_SLIDE_OUT, ROW_LEAVE, Easings.EXPO_IN, true);
            }
        }
        rowEntries.removeIf(entry -> !entry.active && entry.alpha.get() <= 0.01F && !entry.alpha.isAlive());

        
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, RowEntry> mapEntry : entryMap.entrySet()) {
            if (!activeEffectIds.contains(mapEntry.getKey()) && mapEntry.getValue().alpha.get() <= 0.01F) {
                toRemove.add(mapEntry.getKey());
                maxDurations.remove(mapEntry.getKey());
            }
        }
        for (String id : toRemove) entryMap.remove(id);

        float panelAlpha = panelAnimation.get();
        boolean visible = targetVisible || panelAlpha > 0.01F || !rowEntries.isEmpty();
        contentVisible(visible);
        if (!visible) return null;

        float width = 110.0F;
        float contentRows = 0;
        for (RowEntry entry : rowEntries) {
            if (entry.alpha.get() > 0.01F || entry.active) {
                contentRows += entry.alpha.get();
                float nameWidth = Render2D.textWidth(TEXT_FONT, entry.name, 6);
                float timerWidth = Render2D.textWidth(TEXT_FONT, entry.time, 6);
                width = Math.max(width, nameWidth + timerWidth + 40);
            }
        }

        float targetHeight = HEADER_HEIGHT + contentRows * ROW_STEP + 4;
        size(width, targetHeight);

        rowStates.clear();
        for (RowEntry entry : rowEntries) {
            float alpha = entry.alpha.get();
            if (alpha > 0.01F || entry.active) {
                rowStates.add(new RowState(entry.key, entry.name, entry.time, entry.effect, entry.progress,
                        entry.y.get(), entry.slide.get(), alpha));
            }
        }
        if (rowStates.size() > 1) rowStates.sort(ROW_STATE_COMPARATOR);

        int negative = 0;
        int positive = 0;
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                negative++;
            } else {
                positive++;
            }
        }
        String groupLabel;
        int groupCount;
        if (preview) {
            groupLabel = "Положительные";
            groupCount = 1;
        } else if (negative > positive) {
            groupLabel = "Отрицательные";
            groupCount = effects.size();
        } else {
            groupLabel = "Положительные";
            groupCount = effects.size();
        }

        return new PotionsState(rowStates, iconAlphaAnimation.get(), panelAlpha, drag.x(), drag.y(), drag.width(), drag.height(), groupLabel, groupCount);
    }

    private void renderPotions(PotionsState state) {
        float x = state.x;
        float y = state.y;
        float w = state.width;
        float h = state.height;
        int bgAlpha = (int) (255 * state.alpha);

        drawPanel(x, y, w, h, bgAlpha, CORNER_MEDIUM);
        drawIosIndicator(x, y, w, h, bgAlpha);

        ScissorUtil.push(
                Render2DCoordinateSpace.toGuiInt(x),
                Render2DCoordinateSpace.toGuiInt(y),
                Render2DCoordinateSpace.toGuiInt(x + w),
                Render2DCoordinateSpace.toGuiInt(y + h)
        );

        
        drawPanelHeader(x, y, w, "Эффекты", "q", bgAlpha);

        float rowY = y + HEADER_HEIGHT;

        for (RowState row : state.rows) {
            float rowAlpha = state.alpha * row.alpha;
            int rowAlphaInt = (int) (255 * rowAlpha);
            float currentY = rowY + row.offset + rowAppearLift(row.alpha);
            float currentX = x + row.slide;

            
            float timerTextWidth = Render2D.textWidth(TEXT_FONT, row.time, 6);
            float arcSize = 5.0f;
            float spacing = 2.5f;
            float boxWidth = arcSize + spacing + timerTextWidth + 8;
            float boxHeight = 11;
            float boxX = currentX + w - boxWidth - 6;
            float boxY = currentY + (ROW_STEP - boxHeight) / 2f;

            int boxAlpha = (int) (rowAlphaInt * 0.85f);
            drawBackground(boxX, boxY, boxWidth, boxHeight, boxAlpha, CORNER_SMALL);
            drawOutline(boxX, boxY, boxWidth, boxHeight, OUTLINE_THICKNESS, boxAlpha, CORNER_SMALL);

            int trackColor = withAlpha(BORDER_COLOR, rowAlphaInt);
            int timerColor = getTimerColor(row.effect, row.progress, rowAlphaInt);

            
            float textHeight = 5f;
            TimerTextAnimator.draw(TEXT_FONT, "potion_" + row.key, row.time, boxX + 4f, boxY + (boxHeight - textHeight) / 2f - 1.0f, 6, timerColor);

            
            float arcX = boxX + boxWidth - arcSize - 4f;
            float arcY = boxY + (boxHeight - arcSize) / 2f;
            float degree = row.progress * 360f;
            float rotation = -90f + (degree / 2f);
            Render2D.arc(arcX, arcY, arcSize, 1.2f, 360f, 0, trackColor);
            Render2D.arc(arcX, arcY, arcSize, 1.2f, degree, rotation, timerColor);

            
            float iconX = currentX + 8;
            float iconY = currentY + (ROW_STEP - ICON_SIZE) / 2f;
            Render2D.effectIcon(row.effect, iconX, iconY, ICON_SIZE, withAlpha(ColorUtil.rgba(255, 255, 255, 255), rowAlphaInt));

            
            Render2D.text(TEXT_FONT, row.name, currentX + 20, currentY + (ROW_STEP - 6) / 2f - 0.5f, 6, withAlpha(TEXT_COLOR, rowAlphaInt));
        }

        ScissorUtil.pop();
    }

    private int getTimerColor(Holder<MobEffect> effect, float progress, int rowAlpha) {
        boolean isNegative = effect.value().getCategory() == MobEffectCategory.HARMFUL;
        if (isNegative) {
            return withAlpha(ColorUtil.rgba(255, 75, 75, 255), rowAlpha);
        }
        if (progress < 0.2f) {
            return withAlpha(ColorUtil.rgba(255, 170, 0, 255), rowAlpha);
        }
        return withAlpha(accentColor(), rowAlpha);
    }

    private final Map<String, RowEntry> entryMap = new LinkedHashMap<>();

    private void markRowActive(RowEntry entry, float targetY) {
        entry.active = true;
        entry.alpha.run(1.0, ROW_APPEAR, Easings.EXPO_OUT, true);
        entry.y.run(targetY, ROW_MOVE, Easings.EXPO_OUT, true);
        entry.slide.run(0.0, ROW_APPEAR, Easings.EXPO_OUT, true);
    }

    private RowEntry row(String key, String name, String time, Holder<MobEffect> effect, float progress, float targetY) {
        for (RowEntry entry : rowEntries) {
            if (entry.key.equals(key)) {
                entry.name = name;
                entry.time = time;
                entry.effect = effect;
                entry.progress = progress;
                return entry;
            }
        }
        RowEntry entry = new RowEntry(key, name, time, effect, progress);
        entry.alpha.set(0.0);
        entry.y.set(targetY + ROW_SPAWN_Y);
        entry.slide.set(-ROW_SLIDE_IN);
        rowEntries.add(entry);
        entryMap.put(key, entry);
        return entry;
    }

    private String getEffectId(MobEffectInstance effect) {
        try {
            var key = effect.getEffect().unwrapKey();
            if (key.isPresent()) {
                return key.get().toString();
            }
        } catch (Exception ignored) {
        }
        return "unknown_" + effect.hashCode();
    }

    private String getDisplayName(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int amplifier = effect.getAmplifier();
        if (amplifier > 0) return name + " " + (amplifier + 1);
        return name;
    }

    private String formatDuration(int ticks) {
        if (ticks == -1) return "в€ћ";
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    private void updatePreviewEffects() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != previewIconSecond) {
            previewIconSecond = second;
            previewEffect = PREVIEW_EFFECTS.get(ThreadLocalRandom.current().nextInt(PREVIEW_EFFECTS.size()));
        }
    }

    private static final class RowEntry {
        private final String key;
        private final SmoothAnimation alpha = new SmoothAnimation();
        private final SmoothAnimation y = new SmoothAnimation();
        private final SmoothAnimation slide = new SmoothAnimation();
        private String name;
        private String time;
        private Holder<MobEffect> effect;
        private float progress;
        private boolean active;

        private RowEntry(String key, String name, String time, Holder<MobEffect> effect, float progress) {
            this.key = key;
            this.name = name;
            this.time = time;
            this.effect = effect;
            this.progress = progress;
        }
    }

    private record RowState(String key, String name, String time, Holder<MobEffect> effect, float progress, float offset, float slide, float alpha) {
    }

    private record PotionsState(List<RowState> rows, float iconAlpha, float alpha, float x, float y, float width, float height, String groupLabel, int groupCount) {
    }
}

