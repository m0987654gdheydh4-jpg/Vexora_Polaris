package polaris.screens.ailab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import polaris.api.module.impl.combat.AuraModule;
import polaris.api.module.impl.combat.aura.ai.AiRotationTelemetry;
import polaris.api.module.impl.combat.aura.ai.AiRotationTrainer;
import polaris.screens.lab.ScaledLabPanel;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.string.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class AiLabScreen extends Screen {
    private static final float DW = 600F;
    private static final float DH = 360F;
    private static final String[] TABS = {"Аналитика", "Обучение", "Сравнение"};

    private final ScaledLabPanel lab = new ScaledLabPanel();
    private final List<BoundsAction> buttons = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final Bounds[] tabBtns = {Bounds.ZERO, Bounds.ZERO, Bounds.ZERO};
    private Bounds closeBtn = Bounds.ZERO;
    private int tab;
    private AiRotationTelemetry telemetry;
    private long lastRefresh;
    private Slider dragging;
    private boolean closing;
    private float openAnim;

    public AiLabScreen() {
        super(Component.literal("AI Lab"));
        refresh();
    }

    private void refresh() {
        telemetry = AiRotationTrainer.resolve7();
        lastRefresh = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float delta) {
        if (!AiRotationTrainer.isFlag() && System.currentTimeMillis() - lastRefresh > 2000L) {
            refresh();
        }
        openAnim = Mth.clamp(openAnim + (closing ? -0.12F : 0.16F), 0F, 1F);
        if (closing && openAnim <= 0.02F) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        Render2D.beginFrame(graphics);
        lab.begin(DW, DH, mx, my);
        lab.drawShell(openAnim);
        buttons.clear();
        sliders.clear();

        float s = lab.s;
        int textCol = lab.text(Math.round(246 * openAnim));
        int muted = rgba(150, 156, 168, Math.round(210 * openAnim));
        int accent = lab.accent(Math.round(220 * openAnim));

        text("AI Lab", lab.px(14), lab.py(16), 13 * s, textCol);
        text("Профиль " + AiRotationTrainer.getDefaultValue() + "  ·  " + AiRotationTrainer.resolve12(),
                lab.px(14), lab.py(30), 8 * s, muted);

        closeBtn = new Bounds(lab.px(DW - 28), lab.py(10), lab.u(18), lab.u(18));
        rect(closeBtn, closeBtn.contains(lab.mouseX, lab.mouseY) ? rgba(230, 70, 80, 230) : rgba(255, 255, 255, 18), 5 * s);
        center("x", closeBtn, 9 * s, textCol);

        float tabY = lab.py(50);
        float tw = (lab.w - lab.u(36)) / 3F;
        for (int i = 0; i < TABS.length; i++) {
            Bounds b = new Bounds(lab.px(14) + i * (tw + lab.u(4)), tabY, tw, lab.u(17));
            tabBtns[i] = b;
            boolean on = tab == i;
            rect(b, on ? accent : rgba(255, 255, 255, b.contains(lab.mouseX, lab.mouseY) ? 26 : 12), 5 * s);
            center(TABS[i], b, 8 * s, on ? rgba(255, 255, 255, 246) : rgba(180, 188, 204, 220));
        }

        float cx = lab.px(14);
        float cy = lab.py(74);
        float cw = lab.w - lab.u(28);
        float ch = lab.h - lab.u(74) - lab.u(12);
        switch (tab) {
            case 1 -> drawTrain(cx, cy, cw, ch, s);
            case 2 -> drawCompare(cx, cy, cw, ch, s);
            default -> drawAnalytics(cx, cy, cw, ch, s);
        }

        Render2D.flush();
        super.render(graphics, mx, my, delta);
    }

    private void drawAnalytics(float f, float g, float h, float i, float s) {
        AiRotationTelemetry t = telemetry;
        if (t == null || !t.flag) {
            empty(f, g, h, i, "Нет записи. Вкладка Обучение → Запись.");
            return;
        }
        text("Кадров " + t.intValue + "  Удары " + t.intValue2 + "  Промахи " + Math.round(t.floatValue * 100F)
                        + "%  Дист " + fmt1(t.floatValue3) + "-" + fmt1(t.floatValue4) + "м",
                f, g + lab.u(2), 8 * s, rgba(200, 208, 222, 230));
        float halfW = (h - lab.u(10)) * 0.5F;
        float chartY = g + lab.u(18);
        float chartH = (i - lab.u(24)) * 0.5F - lab.u(6);
        card(f, chartY, halfW, chartH, "Дистанция");
        drawDistBars(f + lab.u(10), chartY + lab.u(22), halfW - lab.u(20), chartH - lab.u(30), t, s);
        card(f + halfW + lab.u(10), chartY, halfW, chartH, "Скорость");
        drawSpeedBars(f + halfW + lab.u(20), chartY + lab.u(22), halfW - lab.u(28), chartH - lab.u(34), t, s);
        float botY = chartY + chartH + lab.u(8);
        card(f, botY, halfW, chartH, "Yaw");
        drawHist(f + lab.u(10), botY + lab.u(22), halfW - lab.u(20), chartH - lab.u(30), t.ints2, t.intValue6, rgba(110, 200, 255, 255));
        card(f + halfW + lab.u(10), botY, halfW, chartH, "Pitch");
        drawHist(f + halfW + lab.u(20), botY + lab.u(22), halfW - lab.u(28), chartH - lab.u(30), t.ints3, t.intValue7, rgba(255, 156, 86, 255));
    }

    private void drawDistBars(float x, float y, float w, float h, AiRotationTelemetry t, float s) {
        int total = Math.max(1, t.ints[0] + t.ints[1] + t.ints[2]);
        String[] labels = {"Близко", "Средне", "Далеко"};
        int[] cols = {rgba(92, 235, 182, 255), rgba(110, 200, 255, 255), rgba(255, 156, 86, 255)};
        float bw = w / 3F - lab.u(8);
        for (int i = 0; i < 3; i++) {
            float p = (float) t.ints[i] / total;
            float bh = Math.max(lab.u(3), p * (h - lab.u(18)));
            float bx = x + i * (w / 3F) + lab.u(4);
            Render2D.rect(bx, y + h - lab.u(16) - bh, bw, bh, 4 * s, cols[i]);
            text(Math.round(p * 100F) + "%", bx, y + h - lab.u(18) - bh - lab.u(9), 7 * s, rgba(235, 240, 250, 235));
            text(labels[i], bx, y + h - lab.u(10), 7 * s, rgba(170, 178, 194, 220));
        }
    }

    private void drawSpeedBars(float x, float y, float w, float h, AiRotationTelemetry t, float s) {
        float max = Math.max(1F, t.floatValue7);
        int n = t.floats == null ? 0 : t.floats.length;
        if (n == 0) return;
        float step = w / n;
        for (int i = 0; i < n; i++) {
            float bh = Math.max(1F, (t.floats[i] / max) * (h - lab.u(4)));
            Render2D.rect(x + i * step, y + h - bh, Math.max(1F, step * 0.85F), bh, 0F, lab.accent(230));
        }
    }

    private void drawHist(float x, float y, float w, float h, int[] bins, int peak, int color) {
        if (bins == null) return;
        float step = w / bins.length;
        float max = Math.max(1F, (float) peak);
        for (int i = 0; i < bins.length; i++) {
            float bh = Math.max(1F, bins[i] / max * (h - 2F));
            Render2D.rect(x + i * step, y + h - bh, Math.max(1F, step * 0.8F), bh, 0F, color);
        }
        Render2D.rect(x + w * 0.5F - 0.5F, y, 1F, h, 0F, rgba(255, 255, 255, 40));
    }

    private void drawTrain(float f, float g, float h, float i, float s) {
        List<String> profiles = AiRotationTrainer.resolve27();
        String active = AiRotationTrainer.getDefaultValue();
        card(f, g, h, lab.u(78), "Профиль");
        Bounds prev = new Bounds(f + lab.u(10), g + lab.u(28), lab.u(28), lab.u(22));
        Bounds mid = new Bounds(f + lab.u(44), g + lab.u(28), lab.u(130), lab.u(22));
        Bounds next = new Bounds(mid.x + mid.w + lab.u(6), g + lab.u(28), lab.u(28), lab.u(22));
        button(prev, "<", false, false, () -> cycleProfile(profiles, -1));
        rect(mid, rgba(255, 255, 255, 16), 5 * s);
        center(active, mid, 9 * s, rgba(235, 240, 250, 235));
        button(next, ">", false, false, () -> cycleProfile(profiles, 1));
        Bounds neu = new Bounds(next.x + next.w + lab.u(8), g + lab.u(28), lab.u(70), lab.u(22));
        Bounds del = new Bounds(neu.x + neu.w + lab.u(6), g + lab.u(28), lab.u(70), lab.u(22));
        button(neu, "+ Новый", false, false, () -> {
            String msg = AiRotationTrainer.createNextProfile();
            if (msg != null) ChatMessage.brandmessage(msg);
            refresh();
        });
        button(del, "Удалить", true, false, () -> {
            String msg = AiRotationTrainer.deleteProfile(AiRotationTrainer.getDefaultValue());
            if (msg != null) ChatMessage.brandmessage(msg);
            refresh();
        });
        text("Запись сохраняется в текущий профиль. + Новый — создать ещё один.",
                f + lab.u(10), g + lab.u(56), 7 * s, rgba(150, 158, 176, 210));

        float by = g + lab.u(88);
        float bw = (h - lab.u(18)) / 4F - lab.u(6);
        boolean rec = AiRotationTrainer.isFlag();
        boolean run = AiRotationTrainer.isFlag2();
        boolean train = AiRotationTrainer.isFlag6();
        button(new Bounds(f, by, bw, lab.u(26)), rec ? "Запись..." : "Запись", false, rec, AiRotationTrainer::resolve);
        button(new Bounds(f + (bw + lab.u(8)), by, bw, lab.u(26)), "Стоп", true, false, () -> { AiRotationTrainer.resolve2(); refresh(); });
        button(new Bounds(f + (bw + lab.u(8)) * 2F, by, bw, lab.u(26)), train ? "Обучение..." : "Обучить", false, train, () -> {
            String msg = AiRotationTrainer.resolve4();
            if (msg != null) ChatMessage.brandmessage(msg);
        });
        button(new Bounds(f + (bw + lab.u(8)) * 3F, by, bw, lab.u(26)), run ? "Идёт" : "Запуск", false, run, this::startRun);

        float py = by + lab.u(36);
        card(f, py, h, lab.u(90), "Параметры");
        AuraModule aura = AuraModule.getInstance();
        if (aura != null) {
            slider(new Bounds(f + lab.u(10), py + lab.u(32), h - lab.u(20), lab.u(22)), "AI Jitter", 0F, 2F,
                    () -> aura.getAiJitter().getFloat(), v -> aura.getAiJitter().setValue((double) v));
            Bounds logB = new Bounds(f + lab.u(10), py + lab.u(60), lab.u(140), lab.u(20));
            boolean logOn = aura.getAiDebugLog().getValue();
            button(logB, logOn ? "Логи: ВКЛ" : "Логи: ВЫКЛ", false, logOn,
                    () -> aura.getAiDebugLog().setValue(!aura.getAiDebugLog().getValue()));
            Bounds missB = new Bounds(logB.x + logB.w + lab.u(8), py + lab.u(60), lab.u(140), lab.u(20));
            boolean missOn = aura.getAiHumanMisses().getValue();
            button(missB, missOn ? "Промахи: ВКЛ" : "Промахи: ВЫКЛ", false, missOn,
                    () -> aura.getAiHumanMisses().setValue(!aura.getAiHumanMisses().getValue()));
        }
        float sy = py + lab.u(98);
        if (sy + lab.u(20) < g + i) {
            String frames = telemetry != null && telemetry.flag ? String.valueOf(telemetry.intValue) : "-";
            String loss = AiRotationTrainer.getFloatValue17() < 0F ? "-" : String.format(Locale.ROOT, "%.4f", AiRotationTrainer.getFloatValue17());
            text("Кадров " + frames + "   Loss " + loss, f, sy, 8 * s, rgba(195, 204, 220, 230));
            text("Пиши на разных дистанциях, веди плавно.", f, sy + lab.u(12), 7 * s, rgba(150, 158, 176, 205));
        }
    }

    private void drawCompare(float f, float g, float h, float i, float s) {
        AiRotationTelemetry t = telemetry;
        if (t == null || !t.flag) {
            empty(f, g, h, i, "Нет данных. Сначала запись и обучение.");
            return;
        }
        float half = (i - lab.u(10)) * 0.5F - lab.u(4);
        drawSeries(f, g, h, half, "Yaw: ты vs нейросеть", t.floats2, t.flag2 ? t.floats4 : null, s);
        drawSeries(f, g + half + lab.u(8), h, half, "Pitch: ты vs нейросеть", t.floats3, t.flag2 ? t.floats5 : null, s);
        if (!t.flag2) {
            text("Модель не обучена — жми Обучить.", f + lab.u(10), g + lab.u(22), 8 * s, rgba(255, 180, 110, 230));
        }
    }

    private void drawSeries(float f, float g, float h, float i, String title, float[] you, float[] nn, float s) {
        card(f, g, h, i, title);
        float x = f + lab.u(10);
        float y = g + lab.u(24);
        float w = h - lab.u(20);
        float hh = i - lab.u(40);
        float mid = y + hh * 0.5F;
        Render2D.rect(x, mid - 0.5F, w, 1F, 0F, rgba(255, 255, 255, 36));
        float scale = 6F;
        if (you != null) for (float v : you) scale = Math.max(scale, Math.abs(v));
        if (nn != null) for (float v : nn) scale = Math.max(scale, Math.abs(v));
        scale = Math.min(scale, 35F);
        plot(x, mid, w, hh * 0.5F - 2F, you, scale, rgba(120, 210, 255, 235));
        plot(x, mid, w, hh * 0.5F - 2F, nn, scale, rgba(255, 150, 90, 235));
        text("ты", x, y + hh + lab.u(8), 7 * s, rgba(120, 210, 255, 220));
        text("нейросеть", x + lab.u(36), y + hh + lab.u(8), 7 * s, rgba(255, 150, 90, 220));
    }

    private void plot(float x, float mid, float w, float amp, float[] fs, float scale, int color) {
        if (fs == null || fs.length == 0) return;
        float step = w / fs.length;
        float k = amp / scale;
        for (int i = 0; i < fs.length; i++) {
            float v = Mth.clamp(fs[i] * k, -amp, amp);
            if (v >= 0) Render2D.rect(x + i * step, mid - v, Math.max(1F, step * 0.8F), v, 0F, color);
            else Render2D.rect(x + i * step, mid, Math.max(1F, step * 0.8F), -v, 0F, color);
        }
    }

    private void startRun() {
        String msg = AiRotationTrainer.resolve3();
        AuraModule aura = AuraModule.getInstance();
        if (aura != null && AiRotationTrainer.isFlag2()) {
            aura.mode.setValue("AI");
            if (!aura.isEnabled()) aura.setEnabled(true);
        }
        if (msg != null && !msg.isEmpty()) ChatMessage.brandmessage(msg);
        refresh();
    }

    private void cycleProfile(List<String> list, int dir) {
        if (list == null || list.isEmpty()) return;
        int idx = list.indexOf(AiRotationTrainer.getDefaultValue());
        idx = Math.floorMod((idx < 0 ? 0 : idx) + dir, list.size());
        AiRotationTrainer.resolve26(list.get(idx));
        refresh();
    }

    private void card(float x, float y, float w, float h, String title) {
        rect(new Bounds(x, y, w, h), rgba(255, 255, 255, 10), 8 * lab.s);
        Render2D.outline(x, y, w, h, 8 * lab.s, Math.max(1F, lab.s), rgba(255, 255, 255, 16));
        text(title, x + lab.u(10), y + lab.u(8), 9 * lab.s, rgba(210, 218, 232, 235));
    }

    private void empty(float x, float y, float w, float h, String msg) {
        text(msg, x + (w - textW(msg, 10 * lab.s)) * 0.5F, y + h * 0.5F, 10 * lab.s, rgba(170, 178, 196, 220));
    }

    private void button(Bounds b, String label, boolean danger, boolean active, Runnable action) {
        boolean hov = b.contains(lab.mouseX, lab.mouseY);
        int c = active ? lab.accent(220) : danger ? (hov ? rgba(230, 78, 90, 230) : rgba(150, 52, 60, 170))
                : (hov ? lab.accent(180) : rgba(255, 255, 255, 16));
        rect(b, c, 5 * lab.s);
        center(label, b, 8 * lab.s, rgba(238, 242, 250, 240));
        buttons.add(new BoundsAction(b, action));
    }

    private void slider(Bounds b, String label, float min, float max, FloatSupplier get, FloatConsumer set) {
        float val = get.get();
        text(label + "  " + String.format(Locale.ROOT, "%.2f", val), b.x, b.y - lab.u(2), 8 * lab.s, rgba(190, 198, 214, 225));
        float ty = b.y + lab.u(12);
        float th = Math.max(3F, lab.u(4));
        Render2D.rect(b.x, ty, b.w, th, th * 0.5F, rgba(255, 255, 255, 30));
        float t = measure((val - min) / (max - min), 0F, 1F);
        Render2D.rect(b.x, ty, b.w * t, th, th * 0.5F, lab.accent(235));
        Render2D.rect(b.x + b.w * t - lab.u(3), ty - lab.u(2), lab.u(6), lab.u(8), 3 * lab.s, rgba(235, 242, 255, 245));
        sliders.add(new Slider(min, max, get, set, new Bounds(b.x, ty - lab.u(8), b.w, lab.u(20))));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float x = (float) event.x(), y = (float) event.y();
        if (closeBtn.contains(x, y)) { onClose(); return true; }
        for (int i = 0; i < tabBtns.length; i++) {
            if (tabBtns[i].contains(x, y)) { tab = i; return true; }
        }
        for (Slider s : sliders) {
            if (s.bounds.contains(x, y)) { dragging = s; s.apply(x); return true; }
        }
        for (BoundsAction b : buttons) {
            if (b.bounds.contains(x, y)) { b.action.run(); return true; }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = null; return super.mouseReleased(event); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) { dragging.apply((float) event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void onClose() { closing = true; }
    @Override public boolean isPauseScreen() { return false; }

    private void rect(Bounds b, int c, float r) { Render2D.rect(b.x, b.y, b.w, b.h, r, c); }
    private static void text(String s, float x, float y, float size, int color) { Render2D.text(FontType.BOLD, s, x, y, size, color); }
    private static void center(String s, Bounds b, float size, int color) {
        text(s, b.x + (b.w - textW(s, size)) * 0.5F, b.y + b.h * 0.5F - size * 0.35F, size, color);
    }
    private static float textW(String s, float size) { return Render2D.textWidth(FontType.BOLD, s, size); }
    private static String fmt1(float f) { return String.format(Locale.ROOT, "%.1f", f); }
    private static float measure(float f, float g, float h) { return ScaledLabPanel.clamp(f, g, h); }
    private static int rgba(int r, int g, int b, int a) { return ColorUtil.rgba(r, g, b, a); }

    private static final class Bounds {
        static final Bounds ZERO = new Bounds(0, 0, 0, 0);
        final float x, y, w, h;
        Bounds(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(float mx, float my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }
    private record BoundsAction(Bounds bounds, Runnable action) {}
    @FunctionalInterface private interface FloatSupplier { float get(); }
    @FunctionalInterface private interface FloatConsumer { void set(float v); }
    private static final class Slider {
        final float min, max; final FloatSupplier get; final FloatConsumer set; final Bounds bounds;
        Slider(float min, float max, FloatSupplier get, FloatConsumer set, Bounds bounds) {
            this.min = min; this.max = max; this.get = get; this.set = set; this.bounds = bounds;
        }
        void apply(float mx) {
            float t = measure((mx - bounds.x) / Math.max(1F, bounds.w), 0F, 1F);
            set.set(measure(Math.round((min + t * (max - min)) * 100F) / 100F, min, max));
        }
    }
}
