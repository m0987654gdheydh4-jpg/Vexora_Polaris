package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.AttackEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.Render3D;
import polaris.utils.render.world.glasstorus.GlassTorus3D;

import java.util.ArrayList;
import java.util.List;

public final class HitTorus extends Module {
    private final NumberSetting noise = register(new NumberSetting("Noise", "", 5.0, 1.0, 10.0, 1.0));
    private final NumberSetting reflect = register(new NumberSetting("Reflect", "", 100.0, 50.0, 100.0, 5.0));
    private final NumberSetting blur = register(new NumberSetting("Blur", "", 0.0, 0.0, 10.0, 1.0));
    private final NumberSetting size = register(new NumberSetting("Size", "", 0.5, 0.5, 1.5, 0.1));

    private final List<TorusObj> toruses = new ArrayList<>();

    public HitTorus() {
        super("HitTorus", "", ModuleCategory.VISUAL);
    }

    @Override
    protected void onDisable() {
        toruses.clear();
    }

    @SubscribeEvent
    private void onAttack(AttackEvent e) {
        if (mc.player == null) return;
        Entity target = e.getTarget();
        if (!(target instanceof LivingEntity) || target == mc.player) return;
        Vec3 hitPos = target.getBoundingBox().getCenter();
        toruses.add(new TorusObj(hitPos));
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent e) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        toruses.removeIf(t -> t.shouldRemove(now));
        if (toruses.isEmpty()) return;

        float torusScale = size.getFloat();
        float noiseVal = noise.getFloat();
        float reflectVal = reflect.getFloat();
        float blurVal = blur.getFloat() * 5.0f;

        GlassTorus3D.setMatrices(Render3D.lastProjMat, Render3D.lastModMat);
        GlassTorus3D.begin();
        for (TorusObj t : toruses) {
            float sizing = t.sizing(now);
            float radius = sizing * torusScale;
            float inner = (torusScale - sizing * torusScale) * radius;
            float outer = sizing * radius;
            GlassTorus3D.renderRockstar(t.pos, t.yaw, t.pitch, outer, Math.max(0f, inner), noiseVal, reflectVal, blurVal);
        }
        GlassTorus3D.end();
    }

    private final class TorusObj {
        private static final long LIFE = 1000L;
        final Vec3 pos;
        final float yaw;
        final float pitch;
        final long born;

        TorusObj(Vec3 raw) {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 sub = eye.subtract(raw).scale(0.5);
            this.pos = raw.add(
                    Mth.clamp(sub.x, -0.5, 0.5),
                    Mth.clamp(sub.y, -1.0, 1.0),
                    Mth.clamp(sub.z, -0.5, 0.5)
            );
            double px = pos.x - mc.player.getX();
            double py = pos.y - (mc.player.getY() + mc.player.getEyeHeight());
            double pz = pos.z - mc.player.getZ();
            double h = Math.sqrt(px * px + pz * pz);
            this.yaw = (float) (Math.atan2(pz, px) * (180.0 / Math.PI)) - 90.0f;
            this.pitch = (float) (-(Math.atan2(py, h) * (180.0 / Math.PI)));
            this.born = System.currentTimeMillis();
        }

        float sizing(long now) {
            float t = Mth.clamp((now - born) / (float) LIFE, 0f, 1f);
            float u = t - 1f;
            return (float) Math.sqrt(1.0 - (double) u * u);
        }

        boolean shouldRemove(long now) {
            return now - born >= LIFE;
        }
    }
}
