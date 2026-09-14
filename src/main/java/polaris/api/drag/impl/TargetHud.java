package polaris.api.drag.impl;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.api.module.impl.combat.AuraModule;
import polaris.utils.render.ScissorUtil;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.item.RenderItem;
import polaris.utils.render.item.RenderItemOptions;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.Render2DCoordinateSpace;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TargetHud extends HudPanel {
    private LivingEntity lastTarget;
    private long lastUpdateTime;
    private float displayedHealth;
    private float displayedAbsorption;
    private float healthBarProgress;
    private float absorptionBarProgress;
    private float animatedHeight = HEIGHT;

    private final List<Particle> particles = new ArrayList<>();
    private LivingEntity particleTarget;
    private int prevHurtTime;
    private float prevParticleHealth;

    private static final int SRC_APPLE = 0;
    private static final int SRC_ENCH_APPLE = 1;
    private static final int SRC_TOTEM = 2;
    private int absorptionSource = SRC_APPLE;
    private float prevAbsorptionCheck = 0f;
    private LivingEntity lastAbsTarget;

    private static final float WIDTH = 118;
    private static final float HEIGHT = 36;
    private static final float COMPACT_HEIGHT = 26;

    public TargetHud() {
        super("targethud", "TargetHud", 140.0F, 130.0F, WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        if (mc.player == null || mc.level == null) {
            contentVisible(false);
            return;
        }

        LivingEntity target = target();
        boolean preview = target == null && editPreview() && mc.player != null;
        boolean hasTarget = target != null || preview;

        float alpha = contentAlpha(hasTarget);
        if (alpha <= 0.01F) return;

        if (preview) {
            target = mc.player;
        } else if (target != null) {
            lastTarget = target;
        } else if (lastTarget != null && lastTarget.isAlive()) {
            target = lastTarget;
        } else if (mc.player != null) {
            target = mc.player;
        } else {
            return;
        }

        long now = System.currentTimeMillis();
        float delta = Math.min(0.1F, (now - lastUpdateTime) / 1000.0F);
        lastUpdateTime = now;

        float x = drag.x();
        float y = drag.y();

        boolean fullLayout = hasItems(target) || target.getAbsorptionAmount() > 0.1F;
        float targetHeight = fullLayout ? HEIGHT : COMPACT_HEIGHT;
        animatedHeight += (targetHeight - animatedHeight) * delta * 10F;
        if (Math.abs(animatedHeight - targetHeight) < 0.1F) animatedHeight = targetHeight;

        size(WIDTH, animatedHeight);

        int bgAlpha = (int) (230 * alpha);
        drawPanel(x, y, WIDTH, animatedHeight, bgAlpha, 6);

        // Определяем источник золотых сердец (яблоко / тотем)
        updateAbsorptionSource(target);

        drawFace(x, y, alpha, fullLayout, target);
        updateParticles(target, x, y, fullLayout, delta, preview);
        drawParticles(alpha);
        drawContent(x, y, alpha, delta, fullLayout, target);

        if (fullLayout && target instanceof Player) {
            drawArmor(x, y, alpha, target);
        }
    }

    private void updateAbsorptionSource(LivingEntity target) {
        float absorpNow = Math.max(0, target.getAbsorptionAmount());

        if (target != lastAbsTarget) {
            lastAbsTarget = target;
            prevAbsorptionCheck = absorpNow;
            if (absorpNow > 0.1F) {
                absorptionSource = detectAbsorptionSource(target);
            }
            return;
        }

        if (absorpNow > prevAbsorptionCheck + 0.1F) {
            // Сердца только что появились — определяем источник по эффекту
            absorptionSource = detectAbsorptionSource(target);
        } else if (absorpNow <= 0.05F) {
            absorptionSource = SRC_APPLE;
        }
        prevAbsorptionCheck = absorpNow;
    }

    private int detectAbsorptionSource(LivingEntity target) {
        try {
            if (target.hasEffect(MobEffects.ABSORPTION)) {
                MobEffectInstance inst = target.getEffect(MobEffects.ABSORPTION);
                if (inst != null) {
                    int amp = inst.getAmplifier();
                    int duration = inst.getDuration();
                    // Тотем: Absorption II (amp 1) на ~5 секунд (100 тиков)
                    if (amp == 1 && duration <= 200) {
                        return SRC_TOTEM;
                    }
                    // Зачарованное яблоко: Absorption IV (amp 3)
                    if (amp >= 3) {
                        return SRC_ENCH_APPLE;
                    }
                    // Обычное яблоко: Absorption I на 2 минуты
                    return SRC_APPLE;
                }
            }
        } catch (Throwable ignored) {
        }

        // Фолбэк по количеству сердец, если эффект недоступен
        float abs = target.getAbsorptionAmount();
        if (Math.abs(abs - 8F) < 0.5F) return SRC_TOTEM;
        if (abs > 8F) return SRC_ENCH_APPLE;
        return SRC_APPLE;
    }

    private ItemStack absorptionIcon() {
        return switch (absorptionSource) {
            case SRC_TOTEM -> new ItemStack(Items.TOTEM_OF_UNDYING);
            case SRC_ENCH_APPLE -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            default -> new ItemStack(Items.GOLDEN_APPLE);
        };
    }

    private void drawFace(float x, float y, float alpha, boolean fullLayout, LivingEntity target) {
        float faceSize = fullLayout ? 26 : 18;
        float faceX = x + 5;
        float faceY = y + (animatedHeight - faceSize) / 2F;

        if (target instanceof AbstractClientPlayer player) {
            String texture = player.getSkin().body().texturePath().toString();
            int imageAlpha = Math.round(255 * alpha);

            float hurtPct = Math.min(1.0F, target.hurtTime / 10.0F);
            int r = 255;
            int g = (int) (255 * (1.0F - hurtPct));
            int b = (int) (255 * (1.0F - hurtPct));
            int color = new Color(r, g, b, imageAlpha).getRGB();

            Render2D.imageUvNearest(texture, faceX, faceY, faceSize, faceSize, 4.0F, 1.0F,
                    8F / 64F, 8F / 64F, 16F / 64F, 16F / 64F, color);
            Render2D.imageUvNearest(texture, faceX, faceY, faceSize, faceSize, 4.0F, 1.0F,
                    40F / 64F, 8F / 64F, 48F / 64F, 16F / 64F, color);
        } else {
            String name = target.getName().getString();
            String letter = target instanceof Player && !name.isEmpty() ? name.substring(0, 1).toUpperCase() : "?";
            float tw = Render2D.textWidth(TITLE_FONT, letter, 10);
            Render2D.rect(faceX, faceY, faceSize, faceSize, 4, withAlpha(ColorUtil.rgba(128, 128, 128, 24), (int) (255 * alpha)));
            Render2D.text(TITLE_FONT, letter, faceX + (faceSize - tw) * 0.5F + 0.5F, faceY + 4.5F, 10, withAlpha(TEXT_COLOR, (int) (255 * alpha)));
        }
    }

    private void drawArmor(float x, float y, float alpha, LivingEntity target) {
        if (!(target instanceof Player player)) return;

        float armorX = x + 36;
        float armorY = y + 6;
        float iconSize = 8;
        float spacing = 2;

        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        };
        for (EquipmentSlot slot : slots) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            drawItemWithInfo(stack, armorX, armorY, iconSize, alpha);
            armorX += iconSize + spacing;
        }
    }

    private void drawItemWithInfo(ItemStack stack, float ix, float iy, float iconSize, float alpha) {
        RenderItem.item(stack, ix, iy, iconSize, RenderItemOptions.noDecorations(alpha));

        int a255 = (int) (255 * alpha);

        // Количество предметов
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            float fs = 5.5f;
            float cw = Render2D.textWidth(TEXT_FONT, count, fs);
            Render2D.text(TEXT_FONT, count, ix + iconSize - cw + 0.5f, iy + iconSize - fs + 1f, fs,
                    withAlpha(TEXT_COLOR, a255));
        }

        // Полоска прочности
        int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            int damage = stack.getDamageValue();
            float frac = Math.max(0f, Math.min(1f, 1f - (float) damage / (float) maxDamage));
            float barY = iy + iconSize + 1f;
            float barH = 1.2f;

            Render2D.rect(ix, barY, iconSize, barH, 0.6f, withAlpha(ColorUtil.rgba(0, 0, 0, 140), a255));
            if (frac > 0.01f) {
                int col = Color.HSBtoRGB(frac / 3.0f, 1.0f, 1.0f);
                Render2D.rect(ix, barY, iconSize * frac, barH, 0.6f, withAlpha(col, a255));
            }
        }
    }

    private void drawContent(float x, float y, float alpha, float delta, boolean fullLayout, LivingEntity target) {
        float contentX = x + (fullLayout ? 36 : 28);
        float nameY = y + (fullLayout ? 17 : 8);

        float hp = Math.max(0, target.getHealth());
        float maxHp = Math.max(1, target.getMaxHealth());
        if (maxHp < hp) maxHp = hp;
        float absorp = Math.max(0, target.getAbsorptionAmount());

        displayedHealth += (hp - displayedHealth) * delta * 5F;
        displayedAbsorption = absorp;

        String rawName = target.getName().getString();
        if (rawName == null || rawName.trim().isEmpty()) {
            rawName = target.getDisplayName().getString();
        }
        if (rawName == null || rawName.trim().isEmpty()) {
            rawName = "Target";
        }
        String name = rawName.replaceAll("(?i)В§[0-9A-FK-OR]", "");

        int accentColor = withAlpha(accentColor(), (int) (255 * alpha));
        int whiteColor = withAlpha(TEXT_COLOR, (int) (255 * alpha));
        int absColor = withAlpha(ColorUtil.rgba(255, 200, 50, 255), (int) (255 * alpha));

        float hpXBase = x + WIDTH - 6;

        if (absorp > 0.1F || displayedAbsorption > 0.1F) {
            String hpStr = String.format("%.1f", displayedHealth);
            float hpW = Render2D.textWidth(TEXT_FONT, hpStr, 6);
            float hpXv = hpXBase - hpW;
            float hpYv = y + 6;

            Render2D.text(TEXT_FONT, hpStr, hpXv, hpYv + 0.5F, 6, accentColor);

            String absStr = String.format("%.1f", displayedAbsorption);
            float absW = Render2D.textWidth(TEXT_FONT, absStr, 6);
            float absXv = hpXBase - absW;
            float absYv = nameY + (fullLayout ? 0.5F : 0);

            // Иконка зависит от источника сердец: яблоко / зачарованное яблоко / тотем
            ItemStack absIcon = absorptionIcon();
            float appleIconSize = 7;
            RenderItem.item(absIcon, absXv - 8.5F, absYv, appleIconSize, RenderItemOptions.noDecorations(alpha));
            Render2D.text(TEXT_FONT, absStr, absXv, absYv + 0.5F, 6, absColor);
        } else {
            String hpStr = String.format("%.1f", displayedHealth);
            float hpW = Render2D.textWidth(TEXT_FONT, hpStr, 6);
            float hpXv = hpXBase - hpW;
            float hpYv = nameY + (fullLayout ? 0.5F : 0);

            Render2D.text(TEXT_FONT, hpStr, hpXv, hpYv + 0.5F, 6, accentColor);
        }

        float sampleW = Render2D.textWidth(TEXT_FONT, "20.0", 6);
        float nameMaxW = hpXBase - sampleW - contentX - 10;
        float nameW = Render2D.textWidth(TEXT_FONT, name, 7);

        if (nameW > nameMaxW) {
            ScissorUtil.push(
                    Render2DCoordinateSpace.toGuiInt(contentX),
                    Render2DCoordinateSpace.toGuiInt(nameY - 2),
                    Render2DCoordinateSpace.toGuiInt(contentX + nameMaxW),
                    Render2DCoordinateSpace.toGuiInt(nameY + 12)
            );
            Render2D.text(TEXT_FONT, name, contentX, nameY, 7, whiteColor);
            ScissorUtil.pop();
        } else {
            Render2D.text(TEXT_FONT, name, contentX, nameY, 7, whiteColor);
        }

        float barX = contentX;
        float barY = y + (fullLayout ? 29 : 18);
        float barWidth = WIDTH - (fullLayout ? 36 : 28) - 6;
        float barHeight = 1.5F;

        Render2D.rect(barX, barY, barWidth, barHeight, 1, withAlpha(BAR_BG, (int) (180 * alpha)));

        float hpFrac = Math.min(1f, hp / maxHp);
        float absFrac = Math.min(1f, absorp / maxHp);
        healthBarProgress += (hpFrac - healthBarProgress) * delta * 4F;
        absorptionBarProgress += (absFrac - absorptionBarProgress) * delta * 4F;

        if (healthBarProgress > 0.01F) {
            Render2D.rect(barX, barY, barWidth * Math.min(1, healthBarProgress), barHeight, 1, accentColor);
        }

        if (absorptionBarProgress > 0.01F) {
            Render2D.rect(barX, barY, barWidth * Math.min(1, absorptionBarProgress), barHeight, 1, absColor);
        }
    }

    private void updateParticles(LivingEntity target, float x, float y, boolean fullLayout, float delta, boolean preview) {
        float faceSize = fullLayout ? 26 : 18;
        float faceX = x + 5;
        float faceY = y + (animatedHeight - faceSize) / 2F;
        float cx = faceX + faceSize / 2F;
        float cy = faceY + faceSize / 2F;


        if (target != particleTarget) {
            particleTarget = target;
            prevHurtTime = target.hurtTime;
            prevParticleHealth = particleHealth(target);
        } else {
            float currentHealth = particleHealth(target);
            float damage = Math.max(0.0F, prevParticleHealth - currentHealth);
            if (!preview && target.hurtTime > prevHurtTime && damage > 0.01F) {
                spawnParticles(cx, cy, damage);
            }
            prevHurtTime = target.hurtTime;
            prevParticleHealth = currentHealth;
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= delta;
            if (p.life <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.vy += 70f * delta;
            p.vx *= 0.92f;
        }
    }

    private static float particleHealth(LivingEntity target) {
        return Math.max(0.0F, target.getHealth()) + Math.max(0.0F, target.getAbsorptionAmount());
    }

    private void spawnParticles(float cx, float cy, float damage) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = Math.max(1, Math.min(24, Math.round(damage)));
        int accent = accentColor();
        float baseAngle = rng.nextFloat() * (float) (Math.PI * 2.0);
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();

            double angle = baseAngle + i * (Math.PI * 2.0 / count) + rng.nextDouble(-0.25, 0.25);
            double speed = 24.0 + Math.min(22.0, damage * 1.5F) + rng.nextDouble(34.0);
            p.x = cx;
            p.y = cy;
            p.vx = (float) (Math.cos(angle) * speed);
            p.vy = (float) (Math.sin(angle) * speed) - 10f;
            p.maxLife = 0.45f + rng.nextFloat() * 0.4f;
            p.life = p.maxLife;
            p.size = (1.0f + rng.nextFloat() * 1.6f + Math.min(0.7F, damage * 0.06F)) * 5f;
            p.rgb = (rng.nextInt(3) == 0) ? 0xFFFFFF : (accent & 0xFFFFFF);
            particles.add(p);
        }

        while (particles.size() > 80) {
            particles.remove(0);
        }
    }

    private void drawParticles(float alpha) {
        if (particles.isEmpty()) {
            return;
        }
        for (Particle p : particles) {
            float lifeFrac = Math.max(0f, p.life / p.maxLife);
            int pAlpha = (int) (255 * lifeFrac * alpha);
            if (pAlpha <= 2) continue;
            float s = p.size * (0.5f + 0.5f * lifeFrac);


            int halo = withAlpha(p.rgb, (int) (pAlpha * 0.20f));
            int mid = withAlpha(p.rgb, (int) (pAlpha * 0.45f));
            int core = withAlpha(0xFFFFFF, (int) (pAlpha * 0.95f));
            int coreTint = withAlpha(p.rgb, pAlpha);

            drawGlowCircle(p.x, p.y, s, halo);
            drawGlowCircle(p.x, p.y, s * 0.62f, mid);
            drawGlowCircle(p.x, p.y, s * 0.34f, coreTint);
            drawGlowCircle(p.x, p.y, s * 0.16f, core);
        }
    }

    private static void drawGlowCircle(float cx, float cy, float size, int color) {
        Render2D.rect(cx - size / 2f, cy - size / 2f, size, size, size / 2f, color);
    }

    private static final class Particle {
        float x, y, vx, vy, life, maxLife, size;
        int rgb;
    }

    private LivingEntity target() {
        if (AuraModule.target != null && AuraModule.target.isAlive()) {
            return AuraModule.target;
        }
        if (mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    private boolean hasItems(LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                if (!player.getItemBySlot(slot).isEmpty()) return true;
            }
        }
        return false;
    }
}