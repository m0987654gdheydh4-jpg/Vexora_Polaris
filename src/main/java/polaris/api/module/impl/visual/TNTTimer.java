package polaris.api.module.impl.visual;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.visual.esp.EspGeometry;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;


public final class TNTTimer extends Module {
    public TNTTimer() {
        super("TNT Timer", "Shows TNT fuse time and estimated damage.", ModuleCategory.VISUAL);
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }
        if (event.getLayer() != DrawEvent.Layer.GAME && event.getLayer() != DrawEvent.Layer.CHAT_OVERLAY) {
            return;
        }

        EspGeometry.ProjectionContext ctx = EspGeometry.createProjectionContext(mc, event.getPartialTicks());
        if (ctx == null) {
            return;
        }

        
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof PrimedTnt tnt)) {
                continue;
            }

            float fuseSec = tnt.getFuse() / 20.0f;
            String name = String.format(java.util.Locale.US, "%.1f сек", fuseSec);

            Vec3 world = tnt.getPosition(event.getPartialTicks()).add(0, tnt.getBbHeight() + 0.45, 0);
            EspGeometry.ScreenPoint sp = EspGeometry.projectWorldToScreenSpace(world, ctx);
            if (sp == null) {
                continue;
            }

            float sx = (float) sp.x();
            float sy = (float) sp.y();

            double damage = estimateDamage(tnt);
            boolean deadly = damage > 0
                    && mc.player.getHealth() + mc.player.getAbsorptionAmount() < damage;

            float size = 8.0f;
            float nameW = Render2D.textWidth(FontType.SEMIBOLD, name, size);
            float pad = 4f;
            float boxH = 11f;
            float yOff = damage > 0 ? 12f : 0f;

            Render2D.rect(sx - nameW * 0.5f - pad, sy - yOff - 1.5f, nameW + pad * 2f, boxH, 3f,
                    ColorUtil.rgba(14, 17, 21, 200));
            Render2D.text(FontType.SEMIBOLD, name, sx - nameW * 0.5f, sy - yOff, size,
                    ColorUtil.rgba(240, 242, 248, 255));

            if (damage > 0) {
                String text = deadly ? "Смертельно!" : ((int) damage + " урона");
                float tw = Render2D.textWidth(FontType.SEMIBOLD, text, size);
                int bg = deadly ? ColorUtil.rgba(180, 40, 40, 210) : ColorUtil.rgba(14, 17, 21, 200);
                int fg = deadly ? ColorUtil.rgba(255, 255, 255, 255) : ColorUtil.rgba(235, 237, 242, 255);
                Render2D.rect(sx - tw * 0.5f - pad, sy + 2.5f, tw + pad * 2f, boxH, 3f, bg);
                Render2D.text(FontType.SEMIBOLD, text, sx - tw * 0.5f, sy + 4f, size, fg);
            }
        }
    }

    private double estimateDamage(PrimedTnt ent) {
        float power = 4f;
        Vec3 center = ent.position();
        double dist = Math.sqrt(mc.player.distanceToSqr(center));
        if (dist >= power * 2.0) {
            return 0;
        }
        boolean blocked = mc.level.clip(new ClipContext(
                center,
                mc.player.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        )).getType() != HitResult.Type.MISS;

        double density = blocked ? 0.35 : 1.0;
        double impact = (1.0 - dist / (power * 2.0)) * density;
        if (impact <= 0) {
            return 0;
        }
        return (int) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0);
    }
}
