package polaris.api.module.impl.visual;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.visual.esp.EspGeometry;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;


public final class DamageParticles extends Module {
    private static DamageParticles instance;

    private final NumberSetting textSize = register(new NumberSetting("Text Size", "Damage number size.", 9.0, 4.0, 22.0, 0.5));
    private final NumberSetting riseSpeed = register(new NumberSetting("Rise Speed", "How fast numbers rise.", 0.55, 0.1, 2.0, 0.05));
    private final NumberSetting lifetime = register(new NumberSetting("Lifetime", "Number lifetime ms.", 1400, 400, 4000, 50));
    private final NumberSetting spread = register(new NumberSetting("Spread", "Horizontal spawn spread.", 0.35, 0.0, 1.2, 0.05));
    private final BooleanSetting showHeal = register(new BooleanSetting("Show Heal", "Show heal numbers.", true));
    private final BooleanSetting showDamage = register(new BooleanSetting("Show Damage", "Show damage numbers.", true));
    private final BooleanSetting outline = register(new BooleanSetting("Outline", "Draw dark outline behind numbers.", true));
    private final NumberSetting range = register(new NumberSetting("Range", "Track entities within blocks.", 48.0, 8.0, 96.0, 1.0));

    private final List<FloatingText> texts = new ArrayList<>();
    private final Map<UUID, Float> prevHealth = new HashMap<>();
    private final Map<UUID, Float> prevAbsorption = new HashMap<>();
    private long lastFrameNs = System.nanoTime();

    public DamageParticles() {
        super("DamageParticles", "Floating damage/heal numbers.", ModuleCategory.VISUAL);
        instance = this;
    }

    public static DamageParticles getInstance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        texts.clear();
        prevHealth.clear();
        prevAbsorption.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            prevHealth.clear();
            prevAbsorption.clear();
            texts.clear();
            return;
        }

        double r = range.getValue();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(
                LivingEntity.class,
                mc.player.getBoundingBox().inflate(r),
                e -> e != null && e.isAlive())) {

            UUID id = entity.getUUID();
            float health = entity.getHealth();
            float absorption = entity.getAbsorptionAmount();
            float total = health + absorption;

            Float oldTotal = null;
            Float oldH = prevHealth.get(id);
            Float oldA = prevAbsorption.get(id);
            if (oldH != null && oldA != null) {
                oldTotal = oldH + oldA;
            }

            if (oldTotal != null) {
                float diff = total - oldTotal;
                
                if (diff < -0.05f && showDamage.getValue()) {
                    float amount = -diff;
                    if (amount >= 0.25f) {
                        spawnText(entity, formatAmount(amount, true), 0xFFFF4A4A, true);
                    }
                } else if (diff > 0.05f && showHeal.getValue()) {
                    float amount = diff;
                    if (amount >= 0.25f) {
                        spawnText(entity, formatAmount(amount, false), 0xFF4CFF7A, false);
                    }
                }
            }

            prevHealth.put(id, health);
            prevAbsorption.put(id, absorption);
        }

        
        prevHealth.keySet().removeIf(id -> {
            if (mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(r + 4),
                    e -> e.getUUID().equals(id)).isEmpty()) {
                prevAbsorption.remove(id);
                return true;
            }
            return false;
        });
    }

    private static String formatAmount(float amount, boolean damage) {
        String sign = damage ? "-" : "+";
        if (amount >= 10f || Math.abs(amount - Math.round(amount)) < 0.05f) {
            return sign + Math.round(amount);
        }
        return sign + String.format(Locale.US, "%.1f", amount);
    }

    private void spawnText(LivingEntity entity, String text, int color, boolean damage) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double sp = spread.getValue();
        double x = entity.getX() + (rng.nextDouble() - 0.5) * sp * 2.0;
        double y = entity.getY() + entity.getBbHeight() * (0.75 + rng.nextDouble() * 0.35);
        double z = entity.getZ() + (rng.nextDouble() - 0.5) * sp * 2.0;
        long life = lifetime.getValue().longValue() + rng.nextLong(0, 280);
        float vx = (float) ((rng.nextDouble() - 0.5) * 0.25);
        float vz = (float) ((rng.nextDouble() - 0.5) * 0.25);
        float pop = damage ? 1.18f : 1.08f;
        texts.add(new FloatingText(x, y, z, vx, vz, text, color, life, pop));
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (texts.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        if (event.getLayer() != DrawEvent.Layer.GAME && event.getLayer() != DrawEvent.Layer.CHAT_OVERLAY) {
            return;
        }

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;

        float rise = riseSpeed.getFloat() * dt;
        Iterator<FloatingText> it = texts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.ageMs += dt * 1000f;
            ft.y += rise;
            ft.x += ft.vx * dt;
            ft.z += ft.vz * dt;
            if (ft.ageMs >= ft.lifetimeMs) {
                it.remove();
            }
        }

        EspGeometry.ProjectionContext ctx = EspGeometry.createProjectionContext(mc, event.getPartialTicks());
        if (ctx == null) {
            return;
        }

        float size = textSize.getFloat();
        for (FloatingText ft : texts) {
            float progress = Mth.clamp(ft.ageMs / ft.lifetimeMs, 0f, 1f);
            float fadeIn = Mth.clamp(progress / 0.12f, 0f, 1f);
            float fadeOut = 1f - Mth.clamp((progress - 0.55f) / 0.45f, 0f, 1f);
            float alpha = fadeIn * fadeOut;
            if (alpha <= 0.02f) {
                continue;
            }

            EspGeometry.ScreenPoint sp = EspGeometry.projectWorldToScreenSpace(ft.x, ft.y, ft.z, ctx);
            if (sp == null) {
                continue;
            }

            float pop = 1f + (ft.popScale - 1f) * (1f - easeOutCubic(Math.min(1f, progress / 0.2f)));
            float scale = size * pop * (1f - progress * 0.12f);
            float tw = Render2D.textWidth(FontType.SEMIBOLD, ft.text, scale);
            float tx = (float) sp.x() - tw * 0.5f;
            float ty = (float) sp.y() - scale * 0.5f - progress * 18f;

            int a = Math.round(255f * alpha);
            int col = (ft.color & 0x00FFFFFF) | (a << 24);

            if (outline.getValue()) {
                int shadow = (Math.round(a * 0.75f) << 24);
                Render2D.text(FontType.SEMIBOLD, ft.text, tx + 0.8f, ty + 0.8f, scale, shadow);
                Render2D.text(FontType.SEMIBOLD, ft.text, tx - 0.6f, ty, scale, shadow);
                Render2D.text(FontType.SEMIBOLD, ft.text, tx + 0.6f, ty, scale, shadow);
                Render2D.text(FontType.SEMIBOLD, ft.text, tx, ty - 0.6f, scale, shadow);
            }
            Render2D.text(FontType.SEMIBOLD, ft.text, tx, ty, scale, col);
        }
    }

    private static float easeOutCubic(float t) {
        float c = 1f - t;
        return 1f - c * c * c;
    }

    private static final class FloatingText {
        double x, y, z;
        float vx, vz;
        final String text;
        final int color;
        final float lifetimeMs;
        final float popScale;
        float ageMs;

        FloatingText(double x, double y, double z, float vx, float vz, String text, int color, long lifetime, float popScale) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
            this.text = text;
            this.color = color;
            this.lifetimeMs = lifetime;
            this.popScale = popScale;
        }
    }
}
