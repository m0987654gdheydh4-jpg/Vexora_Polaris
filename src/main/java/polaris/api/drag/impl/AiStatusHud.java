package polaris.api.drag.impl;

import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.ai.AiRotationStatus;
import polaris.api.module.impl.combat.aura.ai.AiRotationTrainer;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.Locale;


public final class AiStatusHud extends HudPanel {
    private final SmoothAnimation showAnim = new SmoothAnimation();
    private final SmoothAnimation pulseAnim = new SmoothAnimation();

    public AiStatusHud() {
        super("aistatus", "AI Status", 140F, 52F, 220F, 48F);
    }

    @Override
    public void render() {
        if (mc.player == null || mc.level == null) {
            contentVisible(false);
            return;
        }
        AiRotationStatus st = AiRotationTrainer.resolve11();
        AuraModule aura = AuraModule.getInstance();
        boolean want = (aura != null && aura.isEnabled() && aura.mode.is("AI"))
                || AiRotationTrainer.isFlag()
                || st.training()
                || System.currentTimeMillis() - st.updatedAtMs() < 2000L;
        float a = contentAlpha(want);
        if (a <= 0.01F) return;

        String title = "AI Aura";
        String status = st.text();
        String meta = "Frames " + st.queuedRecords() + "  Saved " + st.writtenRecords();
        float tw = Math.max(160F, Math.max(
                Render2D.textWidth(FontType.BOLD, title, 11F) + Render2D.textWidth(FontType.BOLD, status, 9F) + 56F,
                Render2D.textWidth(FontType.BOLD, meta, 8F) + 48F));
        size(tw, 48F);
        float x = drag.x();
        float y = drag.y();
        int accent = statusColor(st, a);
        drawPanel(x, y, tw, 48F, (int) (230 * a), 8F);

        boolean busy = st.training()
                || status.toLowerCase(Locale.ROOT).contains("recording")
                || status.toLowerCase(Locale.ROOT).contains("replay");
        pulseAnim.update();
        pulseAnim.run(busy ? 1.0 : 0.0, 0.2F, Easings.EXPO_OUT, true);
        float p = pulseAnim.get() * (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 180.0));
        float cx = x + 18F;
        float cy = y + 24F;
        int ar = (accent >> 16) & 255;
        int ag = (accent >> 8) & 255;
        int ab = accent & 255;
        Render2D.rect(cx - 6F - p * 3F, cy - 6F - p * 3F, 12F + p * 6F, 12F + p * 6F, 6F + p * 3F,
                ColorUtil.rgba(ar, ag, ab, (int) (42 * a * (1F - p * 0.5F))));
        Render2D.rect(cx - 3F, cy - 3F, 6F, 6F, 3F, accent);
        Render2D.text(FontType.BOLD, title, x + 32F, y + 12F, 11F, ColorUtil.rgba(245, 248, 255, (int) (246 * a)));
        float sw = Render2D.textWidth(FontType.BOLD, status, 9F);
        Render2D.text(FontType.BOLD, status, x + tw - 12F - sw, y + 13F, 9F, ColorUtil.rgba(200, 208, 220, (int) (220 * a)));
        Render2D.text(FontType.BOLD, meta, x + 32F, y + 30F, 8F, ColorUtil.rgba(155, 165, 180, (int) (165 * a)));
    }

    private int statusColor(AiRotationStatus st, float a) {
        String t = st.text().toLowerCase(Locale.ROOT);
        int alpha = (int) (255 * a);
        if (t.contains("failed") || t.contains("error") || t.contains("missing")) return ColorUtil.rgba(255, 96, 112, alpha);
        if (st.training()) return ColorUtil.rgba(255, 198, 92, alpha);
        if (st.loadingModel()) return ColorUtil.rgba(120, 176, 255, alpha);
        if (t.contains("recording")) return ColorUtil.rgba(92, 235, 182, alpha);
        if (t.contains("replay") || t.contains("ready") || t.contains("brain")) return ColorUtil.rgba(128, 226, 255, alpha);
        return ColorUtil.rgba(120, 180, 255, alpha);
    }
}
