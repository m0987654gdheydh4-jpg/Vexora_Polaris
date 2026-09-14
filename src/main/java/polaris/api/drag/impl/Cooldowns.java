package polaris.api.drag.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.animation.TimerTextAnimator;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.item.RenderItemOptions;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ScissorUtil;
import polaris.utils.render.ui.Render2DCoordinateSpace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Cooldowns extends HudPanel {
    private static final float ROW_STEP = 14.0F;
    private static final float HEADER_HEIGHT = 20f;
    private static final float ICON_SIZE = 9f;
    private static final float PANEL_ANIM = 0.30F;
    private static final Comparator<RowState> ROW_STATE_COMPARATOR = Comparator.comparingDouble(RowState::offset);
    
    private static final Comparator<CooldownInfo> ACTIVE_COMPARATOR =
            Comparator.<CooldownInfo>comparingDouble(info -> info.progress)
                    .thenComparing(info -> info.key.rowKey());

    private final Map<CooldownKey, CooldownInfo> infoByItem = new LinkedHashMap<>();
    private final List<RowEntry> rowEntries = new ArrayList<>();
    private final List<CooldownInfo> activeCooldowns = new ArrayList<>();
    private final List<RowState> rowStates = new ArrayList<>();
    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private final SmoothAnimation iconAlphaAnimation = new SmoothAnimation();
    private boolean iconAlphaForward = true;

    private static final ItemStack[] PREVIEW_STACKS = {
            Items.ENDER_EYE.getDefaultInstance(),
            Items.SUGAR.getDefaultInstance(),
            Items.GOLDEN_APPLE.getDefaultInstance()
    };
    private int currentItemIndex = 0;
    private long lastItemChange = 0;

    public Cooldowns() {
        super("cooldowns", "Cooldowns", 10.0F, 40.0F, 110.0F, 23.0F);
    }

    @Override
    public void render() {
        CooldownsState state = logics();
        if (state == null) return;
        renderCooldowns(state);
    }

    private CooldownsState logics() {
        List<CooldownInfo> active = collectActive();
        boolean preview = active.isEmpty() && editPreview();
        boolean targetVisible = !active.isEmpty() || preview;

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
            long now = System.currentTimeMillis();
            if (now - lastItemChange >= 1000) {
                currentItemIndex = (currentItemIndex + 1) % PREVIEW_STACKS.length;
                lastItemChange = now;
            }
            RowEntry entry = row("__preview", PREVIEW_STACKS[currentItemIndex], "Пример задержки", "0:00", 0.0F, 0.5f);
            markRowActive(entry, 0.0F);
            targetRows = 1;
        } else {
            for (CooldownInfo info : active) {
                float targetY = targetRows * ROW_STEP;
                RowEntry entry = row(info.key.rowKey(), info.stack, displayName(info.stack), formatDuration(info.remainingSeconds), targetY, info.progress);
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

        float panelAlpha = panelAnimation.get();
        boolean visible = targetVisible || panelAlpha > 0.01F || !rowEntries.isEmpty();
        contentVisible(visible);
        if (!visible) return null;

        float width = 110.0F;
        float contentRows = 0;
        float lowestRowBottom = 0.0F;
        for (RowEntry entry : rowEntries) {
            if (entry.alpha.get() > 0.01F || entry.active) {
                contentRows += entry.alpha.get();
                
                
                lowestRowBottom = Math.max(lowestRowBottom, entry.y.get() + ROW_STEP);
                float nameWidth = Render2D.textWidth(TEXT_FONT, entry.name, 6);
                float timerWidth = Render2D.textWidth(TEXT_FONT, entry.time, 6);
                width = Math.max(width, nameWidth + timerWidth + 40);
            }
        }

        float targetHeight = HEADER_HEIGHT + Math.max(contentRows * ROW_STEP, lowestRowBottom) + 4;
        size(width, targetHeight);

        rowStates.clear();
        for (RowEntry entry : rowEntries) {
            float alpha = entry.alpha.get();
            if (alpha > 0.01F || entry.active) {
                rowStates.add(new RowState(entry.key, entry.stack, entry.name, entry.time, entry.progress,
                        entry.y.get(), entry.slide.get(), alpha));
            }
        }
        if (rowStates.size() > 1) rowStates.sort(ROW_STATE_COMPARATOR);

        return new CooldownsState(rowStates, iconAlphaAnimation.get(), panelAlpha, drag.x(), drag.y(), drag.width(), drag.height());
    }

    private void markRowActive(RowEntry entry, float targetY) {
        entry.active = true;
        entry.alpha.run(1.0, ROW_APPEAR, Easings.EXPO_OUT, true);
        entry.y.run(targetY, ROW_MOVE, Easings.EXPO_OUT, true);
        entry.slide.run(0.0, ROW_APPEAR, Easings.EXPO_OUT, true);
    }

    private void renderCooldowns(CooldownsState state) {
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

        
        drawPanelHeader(x, y, w, "Задержки", "l", bgAlpha);

        float rowY = y + HEADER_HEIGHT;

        for (RowState row : state.rows) {
            float rowAlpha = state.alpha * row.alpha;
            int rowAlphaInt = (int) (255 * rowAlpha);
            float currentY = rowY + row.offset + rowAppearLift(row.alpha);
            float currentX = x + row.slide;

            
            
            if (currentY + ROW_STEP > y + h + 0.5F || currentY < y + HEADER_HEIGHT - ROW_STEP) {
                continue;
            }

            
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

            
            float arcX = boxX + 4;
            float arcY = boxY + (boxHeight - arcSize) / 2f;
            float degree = row.progress * 360f;
            float rotation = -90f + (degree / 2f);
            int trackColor = withAlpha(BORDER_COLOR, rowAlphaInt);
            int timerColor = getTimerColor(row.progress, rowAlphaInt);
            Render2D.arc(arcX, arcY, arcSize, 1.2f, 360f, 0, trackColor);
            Render2D.arc(arcX, arcY, arcSize, 1.2f, degree, rotation, timerColor);

            
            float textHeight = 5f;
            TimerTextAnimator.draw(TEXT_FONT, "cooldown_" + row.key, row.time, arcX + arcSize + spacing, boxY + (boxHeight - textHeight) / 2f - 1.0f, 6, timerColor);

            
            Render2D.text(TEXT_FONT, row.name, currentX + 20, currentY + (ROW_STEP - 6) / 2f - 0.5f, 6, withAlpha(TEXT_COLOR, rowAlphaInt));

            
            float iconX = currentX + 8;
            float iconY = currentY + (ROW_STEP - ICON_SIZE) / 2f;
            RenderItem.item(row.stack, iconX, iconY, ICON_SIZE, RenderItemOptions.noDecorations(rowAlpha));
        }

        ScissorUtil.pop();
    }

    private int getTimerColor(float progress, int rowAlpha) {
        
        return withAlpha(accentColor(), rowAlpha);
    }

    private RowEntry row(String key, ItemStack stack, String name, String time, float targetY, float progress) {
        for (RowEntry entry : rowEntries) {
            if (entry.key.equals(key)) {
                entry.stack = stack == null ? ItemStack.EMPTY : stack.copy();
                entry.name = name;
                entry.time = time;
                entry.progress = progress;
                return entry;
            }
        }
        RowEntry entry = new RowEntry(key, stack, name, time, progress);
        entry.alpha.set(0.0);
        entry.y.set(targetY + ROW_SPAWN_Y);
        entry.slide.set(-ROW_SLIDE_IN);
        rowEntries.add(entry);
        return entry;
    }

    private List<CooldownInfo> collectActive() {
        activeCooldowns.clear();
        if (mc.player == null) { infoByItem.clear(); return activeCooldowns; }

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !mc.player.getCooldowns().isOnCooldown(stack)) continue;
            CooldownKey key = CooldownKey.of(stack);
            float progress = mc.player.getCooldowns().getCooldownPercent(stack, 0.0F);
            CooldownInfo info = infoByItem.computeIfAbsent(key, ignored -> new CooldownInfo());
            
            
            if (containsActiveKey(key)) continue;
            info.key = key;
            info.stack = stack.copy();
            info.update(progress, mc.player.tickCount);
            activeCooldowns.add(info);
        }
        
        activeCooldowns.sort(ACTIVE_COMPARATOR);
        infoByItem.entrySet().removeIf(entry -> !containsActiveKey(entry.getKey()));
        return activeCooldowns;
    }

    private boolean containsActiveKey(CooldownKey key) {
        for (CooldownInfo info : activeCooldowns) {
            if (info.key.equals(key)) return true;
        }
        return false;
    }

    private String displayName(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return switch (id) {
            case "minecraft:ender_eye" -> "Disorientation";
            case "minecraft:sugar" -> "Sugar";
            case "minecraft:netherite_scrap" -> "Trap";
            case "minecraft:dried_kelp" -> "Plast";
            case "minecraft:trident" -> "Trident";
            case "minecraft:mace" -> "Mace";
            case "minecraft:wind_charge" -> "Wind Charge";
            case "minecraft:enchanted_golden_apple" -> "Ench. Gap";
            case "minecraft:golden_apple" -> "Golden Apple";
            default -> stack.getHoverName().getString();
        };
    }

    private String formatDuration(int seconds) {
        if (seconds < 0) return "...";
        if (seconds == 0) return "0:00";
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return minutes + ":" + String.format("%02d", secs);
    }

    private static final class RowEntry {
        private final String key;
        private final SmoothAnimation alpha = new SmoothAnimation();
        private final SmoothAnimation y = new SmoothAnimation();
        private final SmoothAnimation slide = new SmoothAnimation();
        private ItemStack stack;
        private String name;
        private String time;
        private float progress;
        private boolean active;

        private RowEntry(String key, ItemStack stack, String name, String time, float progress) {
            this.key = key;
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
            this.name = name;
            this.time = time;
            this.progress = progress;
        }
    }

    private final class CooldownInfo {
        private CooldownKey key;
        private ItemStack stack = ItemStack.EMPTY;
        private float progress;
        private float lastProgress = -1.0F;
        private long lastTick = -1L;
        private int totalTicks = -1;
        private int remainingTicks;
        private int remainingSeconds;

        private void update(float nextProgress, long tick) {
            progress = clamp(nextProgress, 0.0F, 1.0F);
            if (lastTick >= 0L && tick > lastTick && lastProgress > progress) {
                float diff = lastProgress - progress;
                if (diff > 0.00001F) {
                    int estimate = Math.round((tick - lastTick) / diff);
                    if (estimate > 0 && estimate < 12000) {
                        totalTicks = totalTicks <= 0 ? estimate : Math.round(totalTicks * 0.75F + estimate * 0.25F);
                    }
                }
            }
            lastProgress = progress;
            lastTick = tick;
            remainingTicks = totalTicks <= 0 ? Math.max(1, Math.round(progress * 20.0F)) : Math.max(1, Math.round(progress * totalTicks));
            remainingSeconds = Math.max(0, remainingTicks / 20);
        }
    }

    private record RowState(String key, ItemStack stack, String name, String time, float progress, float offset, float slide, float alpha) {
    }

    private record CooldownsState(List<RowState> rows, float iconAlpha, float alpha, float x, float y, float width, float height) {
    }

    private record CooldownKey(Item item, DataComponentMap components, String name) {
        private static CooldownKey of(ItemStack stack) {
            return new CooldownKey(stack.getItem(), stack.immutableComponents(), stack.getHoverName().getString());
        }
        private String rowKey() {
            return BuiltInRegistries.ITEM.getKey(item) + "|" + name + "|" + components.hashCode();
        }
    }
}

