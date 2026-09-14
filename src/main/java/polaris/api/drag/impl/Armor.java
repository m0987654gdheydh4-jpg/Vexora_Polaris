package polaris.api.drag.impl;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.api.settings.impl.BooleanSetting;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.item.RenderItemOptions;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.timer.StopWatch;


public final class Armor extends HudPanel {
    private static final float ITEM = 16.0F;
    private static final float STEP = 20.0F;
    private static final float PERCENT_SIZE = 7.0F;
    private static final float EDGE_THRESHOLD = 25.0F;
    private static final float RIGHT_EDGE_PAD = 80.0F;

    
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final Item[][] DEMO_PIECES = {
            {Items.LEATHER_HELMET, Items.IRON_HELMET, Items.GOLDEN_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
            {Items.LEATHER_CHESTPLATE, Items.IRON_CHESTPLATE, Items.GOLDEN_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
            {Items.LEATHER_LEGGINGS, Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
            {Items.LEATHER_BOOTS, Items.IRON_BOOTS, Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}
    };

    private final BooleanSetting percents;
    private final SmoothAnimation showAnim = new SmoothAnimation();
    private final SmoothAnimation percentAnim = new SmoothAnimation();
    private final StopWatch demoTimer = new StopWatch();
    private final ItemStack[] demoArmor = new ItemStack[4];

    public Armor() {
        super("armor", "Armor", 60.0F, 40.0F, 80.0F, 20.0F);
        percents = new BooleanSetting("Прочность в процентах", "Show armor durability as percent above each piece.", true);
        showAnim.set(0.0);
        percentAnim.set(0.0);
        refreshDemoArmor();
    }

    @Override
    public void render() {
        if (!selected() || mc.player == null || mc.getWindow() == null) {
            contentVisible(false);
            return;
        }

        boolean hasArmor = false;
        for (EquipmentSlot slot : SLOTS) {
            if (!mc.player.getItemBySlot(slot).isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        boolean showDemo = !hasArmor && (mc.screen instanceof ChatScreen || editPreview());
        boolean show = hasArmor || showDemo;

        showAnim.update();
        showAnim.run(show ? 1.0 : 0.0, 0.28F, show ? Easings.EXPO_OUT : Easings.EXPO_IN, true);
        float alpha = showAnim.get();
        contentVisible(show || alpha > 0.01F);
        if (alpha <= 0.01F) {
            return;
        }

        if (showDemo && demoTimer.finished(2000)) {
            refreshDemoArmor();
            demoTimer.reset();
        }

        float screenW = Render2D.getFixedScaledWidth();
        float screenH = Render2D.getFixedScaledHeight();
        float dragX = drag.x();
        float dragY = drag.y();

        
        boolean vertical = dragX <= EDGE_THRESHOLD || dragX >= screenW - RIGHT_EDGE_PAD;

        percentAnim.update();
        percentAnim.run(percents.getValue() ? 1.0 : 0.0, 0.25F, Easings.SINE_OUT, true);
        float percentA = percentAnim.get();

        
        float[] edge = nearestEdge(dragX, dragY, screenW, screenH, vertical);
        float x = lerp(edge[0], dragX, alpha);
        float y = lerp(edge[1], dragY, alpha);

        float cursorX = x;
        float cursorY = y;
        float maxTextW = 0f;
        int itemCount = 0;

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = showDemo ? demoArmor[i] : mc.player.getItemBySlot(SLOTS[i]);
            if (stack == null || stack.isEmpty()) {
                
                if (showDemo) {
                    if (vertical) {
                        cursorY += STEP;
                    } else {
                        cursorX += STEP;
                    }
                }
                continue;
            }

            
            RenderItem.item(stack, cursorX, cursorY, ITEM, RenderItemOptions.decorated(alpha));

            
            if (percentA > 0.02f && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                float pct = left / (float) stack.getMaxDamage() * 100f;
                if (Float.isNaN(pct)) {
                    pct = 100f;
                }
                String text = String.format("%.0f%%", pct);
                float tw = Render2D.textWidth(FontType.SEMIBOLD, text, PERCENT_SIZE);
                maxTextW = Math.max(maxTextW, tw);

                float tx = cursorX + ITEM * 0.5f - tw * 0.5f + 0.5f;
                float ty = vertical ? cursorY - 6.5f : cursorY - 8.5f;
                int color = ColorUtil.rgba(255, 255, 255, Math.round(255f * alpha * percentA));
                Render2D.text(FontType.SEMIBOLD, text, tx, ty, PERCENT_SIZE, color);
            }

            if (vertical) {
                cursorY += STEP;
            } else {
                cursorX += STEP;
            }
            itemCount++;
        }

        if (itemCount <= 0 && !showDemo) {
            
            size(STEP, STEP);
            return;
        }

        
        if (vertical) {
            float w = Math.max(ITEM, maxTextW + 4f);
            float h = Math.max(STEP, itemCount * STEP);
            
            size(Math.max(18f, w), h);
        } else {
            float w = Math.max(STEP, itemCount * STEP);
            float h = STEP + (percentA > 0.5f ? 8f : 0f);
            size(w, Math.max(18f, h));
        }
    }

    private void refreshDemoArmor() {
        for (int i = 0; i < 4; i++) {
            Item[] pieces = DEMO_PIECES[i];
            int idx = (int) (Math.random() * pieces.length);
            ItemStack stack = pieces[idx].getDefaultInstance();
            if (Math.random() < 0.7 && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int dmg = (int) (Math.random() * max * 0.8);
                stack.setDamageValue(dmg);
            }
            demoArmor[i] = stack;
        }
    }

    
    private static float[] nearestEdge(float x, float y, float sw, float sh, boolean vertical) {
        
        float[][] candidates = vertical
                ? new float[][]{
                {-12f, y},
                {sw + 12f, y},
                {x, -12f},
                {x, sh + 12f}
        }
                : new float[][]{
                {x, -12f},
                {x, sh + 12f},
                {-12f, y},
                {sw + 12f, y}
        };

        float best = Float.MAX_VALUE;
        float bx = x;
        float by = y;
        for (float[] c : candidates) {
            float dx = c[0] - x;
            float dy = c[1] - y;
            float d = dx * dx + dy * dy;
            if (d < best) {
                best = d;
                bx = c[0];
                by = c[1];
            }
        }
        return new float[]{bx, by};
    }

    private static float lerp(float a, float b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        return a + (b - a) * t;
    }
}
