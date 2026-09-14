package polaris.screens.rotationbuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import polaris.api.module.impl.combat.aura.custom.RotationPresetEditorState;
import polaris.api.module.impl.combat.aura.custom.RotationPresetManager;
import polaris.api.module.impl.combat.aura.custom.RotationPresetStore;
import polaris.screens.lab.ScaledLabPanel;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.string.chat.ChatMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class RotationBuilderScreen extends Screen {
    private static final float DW = 620F;
    private static final float DH = 380F;
    private static final String[] ENGINES = RotationPresetManager.FUNTIME;

    private final ScaledLabPanel lab = new ScaledLabPanel();
    private final RotationPresetManager pm = RotationPresetManager.resolve();
    private final RotationPresetStore store = RotationPresetStore.getINSTANCE();
    private final List<Slider> sliders = new ArrayList<>();
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Cycle> cycles = new ArrayList<>();

    private Bounds closeBtn = Bounds.ZERO;
    private Bounds preview = Bounds.ZERO;
    private float modelOx, modelOy, modelW, modelH;
    private Slider dragging;
    private int dragPoint = -1;
    private int selectedPoint = -1;
    private boolean closing;
    private float openAnim;
    private int page;
    private long lastPointCycle;
    private int previewIdx;
    private float crossX, crossY;
    private float contentScroll;

    public RotationBuilderScreen() {
        super(Component.literal("Rotation Builder"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        openAnim = Mth.clamp(openAnim + (closing ? -0.12F : 0.16F), 0F, 1F);
        if (closing && openAnim <= 0.02F) {
            RotationPresetManager.invoke10();
            Minecraft.getInstance().setScreen(null);
            return;
        }
        Render2D.beginFrame(g);
        lab.begin(DW, DH, mouseX, mouseY);
        lab.drawShell(openAnim);

        sliders.clear();
        buttons.clear();
        cycles.clear();

        float s = lab.s;
        int text = lab.text(Math.round(246 * openAnim));
        int muted = rgba(150, 156, 168, Math.round(210 * openAnim));
        int accent = lab.accent(Math.round(220 * openAnim));

        text("Rotation Builder", lab.px(14), lab.py(16), 13 * s, text);
        text("Engine " + pm.funtime2 + "  ·  " + pm.items.size() + "/12", lab.px(14), lab.py(30), 8 * s, muted);

        closeBtn = new Bounds(lab.px(DW - 28), lab.py(10), lab.u(18), lab.u(18));
        rect(closeBtn, closeBtn.contains(lab.mouseX, lab.mouseY) ? rgba(230, 70, 80, 230) : rgba(255, 255, 255, 18), 5 * s);
        center("x", closeBtn, 9 * s, text);

        
        float chipX = lab.px(14);
        float chipY = lab.py(48);
        float chipH = lab.u(15);
        float maxX = lab.x + lab.w - lab.u(14);
        for (String eng : ENGINES) {
            float cw = textW(eng, 8 * s) + lab.u(10);
            if (chipX + cw > maxX) break;
            Bounds b = new Bounds(chipX, chipY, cw, chipH);
            boolean on = eng.equals(pm.funtime2) || eng.equals(pm.funtime3);
            rect(b, on ? accent : rgba(255, 255, 255, b.contains(lab.mouseX, lab.mouseY) ? 28 : 12), 4 * s);
            center(eng, b, 8 * s, text);
            buttons.add(new Btn(b, () -> { pm.invoke5(eng); RotationPresetManager.invoke10(); }));
            chipX += cw + lab.u(4);
        }

        
        float bodyTop = lab.py(70);
        float bodyH = lab.h - lab.u(70) - lab.u(36);
        float leftW = lab.u(170);
        preview = new Bounds(lab.px(12), bodyTop, leftW, bodyH);
        rect(preview, rgba(255, 255, 255, 10), 8 * s);
        outline(preview, rgba(255, 255, 255, 18), 8 * s);

        text("ЛКМ точка · ПКМ удалить", preview.x + lab.u(8), preview.y + lab.u(8), 7 * s, muted);
        modelH = bodyH * 0.70F;
        modelW = modelH * 0.42F;
        modelOx = preview.x + preview.w * 0.5F;
        modelOy = preview.y + preview.h - lab.u(20);
        RotationPresetEditorState.drawModel(modelOx, modelOy, modelW, modelH, openAnim);
        updateCross(delta);
        drawPoints(accent);
        rect(new Bounds(crossX - 5, crossY - 0.6F, 10, 1.2F), rgba(255, 90, 110, 220), 0);
        rect(new Bounds(crossX - 0.6F, crossY - 5, 1.2F, 10), rgba(255, 90, 110, 220), 0);
        action(new Bounds(preview.x + lab.u(8), preview.y + preview.h - lab.u(20), lab.u(66), lab.u(14)), "Очистить", true, pm::invoke3);

        float rx = preview.x + preview.w + lab.u(10);
        float rw = lab.x + lab.w - lab.u(12) - rx;
        String[] tabs = {"Скорости", "Смещения", "Пресеты"};
        float tw = (rw - lab.u(8)) / 3F;
        for (int i = 0; i < 3; i++) {
            Bounds b = new Bounds(rx + i * (tw + lab.u(4)), bodyTop, tw, lab.u(17));
            boolean on = page == i;
            rect(b, on ? accent : rgba(255, 255, 255, b.contains(lab.mouseX, lab.mouseY) ? 26 : 12), 5 * s);
            center(tabs[i], b, 8 * s, text);
            int idx = i;
            buttons.add(new Btn(b, () -> { page = idx; contentScroll = 0; }));
        }

        float sy = bodyTop + lab.u(22);
        float sh = bodyH - lab.u(22);
        
        if (page == 0) drawSpeed(rx, sy, rw, sh, s);
        else if (page == 1) drawOffset(rx, sy, rw, sh, s);
        else drawPresets(rx, sy, rw, sh, s);

        float by = lab.y + lab.h - lab.u(26);
        action(new Bounds(lab.px(12), by, lab.u(70), lab.u(16)), "Сброс", true, pm::invoke7);
        action(new Bounds(lab.px(88), by, lab.u(78), lab.u(16)), "Сохранить", false, () -> {
            RotationPresetManager.invoke10();
            ChatMessage.brandmessage("Custom rotation сохранён.");
        });
        action(new Bounds(lab.px(172), by, lab.u(84), lab.u(16)), "Копировать", false, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(pm.resolve4());
            ChatMessage.brandmessage("Код скопирован.");
        });
        action(new Bounds(lab.px(262), by, lab.u(78), lab.u(16)), "Вставить", false, () -> {
            boolean ok = RotationPresetManager.check3(Minecraft.getInstance().keyboardHandler.getClipboard());
            ChatMessage.brandmessage(ok ? "Пресет импортирован." : "Неверный код.");
        });

        Render2D.flush();
        super.render(g, mouseX, mouseY, delta);
    }

    private void drawSpeed(float x, float y, float w, float h, float s) {
        float col = (w - lab.u(8)) * 0.5F;
        float gap = lab.u(26);
        float sy = y;
        slider(x, sy, col, "Yaw мин", 0, 180, true, () -> pm.floatValue, v -> pm.floatValue = v); sy += gap;
        slider(x, sy, col, "Yaw макс", 0, 180, true, () -> pm.floatValue2, v -> pm.floatValue2 = v); sy += gap;
        slider(x, sy, col, "Pitch мин", 0, 120, true, () -> pm.floatValue3, v -> pm.floatValue3 = v); sy += gap;
        slider(x, sy, col, "Pitch макс", 0, 120, true, () -> pm.floatValue4, v -> pm.floatValue4 = v); sy += gap;
        slider(x, sy, col, "Удар Yaw", 0, 240, true, () -> pm.floatValue5, v -> pm.floatValue5 = v); sy += gap;
        slider(x, sy, col, "Удар Pitch", 0, 240, true, () -> pm.floatValue6, v -> pm.floatValue6 = v); sy += gap;
        if (sy < y + h - gap) { slider(x, sy, col, "Рандом Y", 0, 20, false, () -> pm.floatValue7, v -> pm.floatValue7 = v); sy += gap; }
        if (sy < y + h - gap) slider(x, sy, col, "Рандом P", 0, 20, false, () -> pm.floatValue8, v -> pm.floatValue8 = v);

        float rx = x + col + lab.u(8);
        float ry = y;
        slider(rx, ry, col, "Осц. X", 0, 1, false, () -> pm.floatValue9, v -> pm.floatValue9 = v); ry += gap;
        slider(rx, ry, col, "Осц. Y", 0, 1, false, () -> pm.floatValue10, v -> pm.floatValue10 = v); ry += gap;
        slider(rx, ry, col, "Частота", 0.2F, 3, false, () -> pm.floatValue11, v -> pm.floatValue11 = v); ry += gap;
        slider(rx, ry, col, "Бок. точка", 0, 0.6F, false, () -> pm.floatValue12, v -> pm.floatValue12 = v); ry += gap;
        slider(rx, ry, col, "Возврат", 5, 120, true, () -> pm.floatValue13, v -> pm.floatValue13 = v); ry += gap;
        slider(rx, ry, col, "Смена тчк", 0.1F, 3, false, () -> pm.floatValue14, v -> pm.floatValue14 = v); ry += gap;
        if (ry < y + h - lab.u(20)) { cycle(rx, ry, col, "Цикл", RotationPresetManager.CYCLE, () -> pm.cycle, v -> pm.cycle = v); ry += lab.u(20); }
        if (ry < y + h - lab.u(18)) toggle(rx, ry, col, "Голова", () -> pm.flag, v -> pm.flag = v);
    }

    private void drawOffset(float x, float y, float w, float h, float s) {
        float col = (w - lab.u(8)) * 0.5F;
        float gap = lab.u(26);
        float sy = y;
        slider(x, sy, col, "Смещ. Yaw", -30, 30, true, () -> pm.floatValue15, v -> pm.floatValue15 = v); sy += gap;
        slider(x, sy, col, "Смещ. Pitch", -30, 30, true, () -> pm.floatValue16, v -> pm.floatValue16 = v); sy += gap;
        slider(x, sy, col, "Pitch min", -90, 0, true, () -> pm.floatValue17, v -> pm.floatValue17 = v); sy += gap;
        slider(x, sy, col, "Pitch max", 0, 90, true, () -> pm.floatValue18, v -> pm.floatValue18 = v); sy += gap;
        slider(x, sy, col, "Упреждение", 0, 0.6F, false, () -> pm.floatValue19, v -> pm.floatValue19 = v);
        float rx = x + col + lab.u(8);
        float ry = y;
        toggle(rx, ry, col, "Отвод", () -> pm.flag2, v -> pm.flag2 = v); ry += lab.u(22);
        slider(rx, ry, col, "Угол отвода", 0, 90, true, () -> pm.floatValue23, v -> pm.floatValue23 = v); ry += gap;
        slider(rx, ry, col, "Интервал", 1.5F, 15, false, () -> pm.floatValue24, v -> pm.floatValue24 = v); ry += gap;
        cycle(rx, ry, col, "Pitch", RotationPresetManager.SMOOTH, () -> pm.smooth, v -> pm.smooth = v); ry += lab.u(20);
        cycle(rx, ry, col, "Точка", RotationPresetManager.MULTIPOINT, () -> pm.multipoint, v -> pm.multipoint = v);
    }

    private void drawPresets(float x, float y, float w, float h, float s) {
        text("Сохранённые пресеты", x, y, 9 * s, rgba(200, 208, 222, 230));
        action(new Bounds(x, y + lab.u(14), lab.u(96), lab.u(15)), "Сохранить", false, () -> {
            var e = store.resolve2("preset", pm);
            ChatMessage.brandmessage(e != null ? "Сохранено: " + e.name() : "Ошибка.");
        });
        float py = y + lab.u(34);
        float rowH = lab.u(18);
        for (var e : store.resolve()) {
            if (py + rowH > y + h) break;
            Bounds row = new Bounds(x, py, w, rowH);
            rect(row, rgba(255, 255, 255, 12), 4 * s);
            text(e.name(), row.x + lab.u(6), row.y + lab.u(5), 8 * s, rgba(220, 226, 236, 230));
            action(new Bounds(row.x + row.w - lab.u(96), row.y + lab.u(2), lab.u(44), lab.u(14)), "Load", false,
                    () -> ChatMessage.brandmessage(store.check(e.id()) ? "Загружено" : "Ошибка"));
            action(new Bounds(row.x + row.w - lab.u(48), row.y + lab.u(2), lab.u(40), lab.u(14)), "Del", true,
                    () -> store.check2(e.id()));
            py += rowH + lab.u(4);
        }
    }

    private void updateCross(float delta) {
        long now = System.currentTimeMillis();
        float tx, ty;
        if (pm.items != null && !pm.items.isEmpty()) {
            if (now >= lastPointCycle) {
                previewIdx = "Random".equals(pm.cycle)
                        ? (int) (Math.random() * pm.items.size())
                        : (previewIdx + 1) % pm.items.size();
                lastPointCycle = now + (long) (pm.floatValue14 * 1000F / Math.max(0.1F, pm.floatValue22));
            }
            var st = pm.items.get(Mth.clamp(previewIdx, 0, pm.items.size() - 1));
            tx = modelOx + st.floatValue * modelW;
            ty = modelOy - st.floatValue2 * modelH;
        } else {
            float[] p = RotationPresetEditorState.toScreen(modelOx, modelOy, modelW, modelH, 0F, 0.5625F);
            tx = p[0]; ty = p[1];
        }
        float k = Mth.clamp(delta * 12F, 0.05F, 1F);
        if (crossX == 0 && crossY == 0) { crossX = tx; crossY = ty; }
        else { crossX += (tx - crossX) * k; crossY += (ty - crossY) * k; }
    }

    private void drawPoints(int accent) {
        if (pm.items == null) return;
        for (int i = 0; i < pm.items.size(); i++) {
            var st = pm.items.get(i);
            float x = modelOx + st.floatValue * modelW;
            float y = modelOy - st.floatValue2 * modelH;
            float r = (i == selectedPoint ? 5F : 3.5F) * lab.s;
            if (i == selectedPoint) {
                Render2D.rect(x - r - 2, y - r - 2, (r + 2) * 2, (r + 2) * 2, 5, rgba(95, 210, 255, 70));
            }
            Render2D.rect(x - r, y - r, r * 2, r * 2, 5, accent);
        }
    }

    private void slider(float x, float y, float w, String label, float min, float max, boolean whole, FGet get, FSet set) {
        float val = get.get();
        float fs = 8 * lab.s;
        text(label + " " + String.format(Locale.ROOT, whole ? "%.0f" : "%.2f", val), x, y, fs, rgba(190, 198, 214, 220));
        float ty = y + lab.u(11);
        float th = Math.max(3F, lab.u(4));
        Render2D.rect(x, ty, w, th, th * 0.5F, rgba(255, 255, 255, 28));
        float t = clamp((val - min) / (max - min), 0F, 1F);
        Render2D.rect(x, ty, w * t, th, th * 0.5F, lab.accent(230));
        Render2D.rect(x + w * t - lab.u(3), ty - lab.u(2), lab.u(6), lab.u(8), 3 * lab.s, rgba(235, 242, 255, 245));
        sliders.add(new Slider(min, max, whole, get, set, new Bounds(x, ty - lab.u(8), w, lab.u(20))));
    }

    private void cycle(float x, float y, float w, String label, String[] options, SGet get, SSet set) {
        text(label, x, y + lab.u(2), 8 * lab.s, rgba(190, 198, 214, 220));
        Bounds b = new Bounds(x + w - lab.u(90), y, lab.u(90), lab.u(15));
        rect(b, rgba(255, 255, 255, b.contains(lab.mouseX, lab.mouseY) ? 28 : 14), 4 * lab.s);
        center(get.get(), b, 8 * lab.s, rgba(235, 240, 250, 235));
        cycles.add(new Cycle(b, options, get, set));
    }

    private void toggle(float x, float y, float w, String label, BGet get, BSet set) {
        text(label, x, y + lab.u(2), 8 * lab.s, rgba(190, 198, 214, 220));
        Bounds b = new Bounds(x + w - lab.u(52), y, lab.u(52), lab.u(15));
        boolean on = get.get();
        rect(b, on ? lab.accent(210) : rgba(255, 255, 255, 14), 4 * lab.s);
        center(on ? "ON" : "OFF", b, 8 * lab.s, rgba(235, 240, 250, 235));
        buttons.add(new Btn(b, () -> { set.set(!get.get()); RotationPresetManager.invoke10(); }));
    }

    private void action(Bounds b, String label, boolean danger, Runnable action) {
        boolean hov = b.contains(lab.mouseX, lab.mouseY);
        int c = danger ? (hov ? rgba(230, 78, 90, 220) : rgba(140, 48, 56, 160))
                : (hov ? lab.accent(200) : rgba(255, 255, 255, 16));
        rect(b, c, 4 * lab.s);
        center(label, b, 8 * lab.s, rgba(238, 242, 250, 240));
        buttons.add(new Btn(b, () -> { action.run(); RotationPresetManager.invoke10(); }));
    }

    private int hitPoint(float x, float y) {
        if (pm.items == null) return -1;
        for (int i = pm.items.size() - 1; i >= 0; i--) {
            var st = pm.items.get(i);
            float px = modelOx + st.floatValue * modelW;
            float py = modelOy - st.floatValue2 * modelH;
            if (Math.hypot(x - px, y - py) <= 8.0 * lab.s) return i;
        }
        return -1;
    }

    private boolean hitModel(float x, float y) {
        if (modelW <= 1) return false;
        return RotationPresetEditorState.hitModel((x - modelOx) / modelW, (modelOy - y) / modelH);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float x = (float) event.x(), y = (float) event.y();
        int btn = event.button();
        if (closeBtn.contains(x, y)) { onClose(); return true; }
        if (preview.contains(x, y)) {
            int hit = hitPoint(x, y);
            if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (hit >= 0) { pm.invoke4(pm.items.get(hit)); selectedPoint = -1; RotationPresetManager.invoke10(); }
                return true;
            }
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (hit >= 0) { selectedPoint = hit; dragPoint = hit; return true; }
                if (hitModel(x, y) && pm.items.size() < 12) {
                    pm.invoke2(clamp((x - modelOx) / modelW, -0.5F, 0.5F), clamp((modelOy - y) / modelH, 0, 1));
                    selectedPoint = pm.items.size() - 1;
                    RotationPresetManager.invoke10();
                    return true;
                }
            }
        }
        for (Slider s : sliders) if (s.bounds.contains(x, y)) { dragging = s; s.apply(x); return true; }
        for (Cycle c : cycles) if (c.bounds.contains(x, y)) { c.next(); RotationPresetManager.invoke10(); return true; }
        for (Btn b : buttons) if (b.bounds.contains(x, y)) { b.action.run(); return true; }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) { RotationPresetManager.invoke10(); dragging = null; }
        if (dragPoint >= 0) { RotationPresetManager.invoke10(); dragPoint = -1; }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        float x = (float) event.x(), y = (float) event.y();
        if (dragging != null) { dragging.apply(x); return true; }
        if (dragPoint >= 0 && dragPoint < pm.items.size()) {
            var st = pm.items.get(dragPoint);
            st.floatValue = clamp((x - modelOx) / modelW, -0.5F, 0.5F);
            st.floatValue2 = clamp((modelOy - y) / modelH, 0, 1);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override public void onClose() { closing = true; }
    @Override public boolean isPauseScreen() { return false; }

    private void rect(Bounds b, int c, float r) { Render2D.rect(b.x, b.y, b.w, b.h, r, c); }
    private void outline(Bounds b, int c, float r) { Render2D.outline(b.x, b.y, b.w, b.h, r, Math.max(1F, lab.s), c); }
    private static void text(String s, float x, float y, float size, int color) { Render2D.text(FontType.BOLD, s, x, y, size, color); }
    private static void center(String s, Bounds b, float size, int color) {
        text(s, b.x + (b.w - textW(s, size)) * 0.5F, b.y + b.h * 0.5F - size * 0.35F, size, color);
    }
    private static float textW(String s, float size) { return Render2D.textWidth(FontType.BOLD, s, size); }
    private static float clamp(float f, float a, float b) { return ScaledLabPanel.clamp(f, a, b); }
    private static int rgba(int r, int g, int b, int a) { return ColorUtil.rgba(r, g, b, a); }

    private static final class Bounds {
        static final Bounds ZERO = new Bounds(0, 0, 0, 0);
        final float x, y, w, h;
        Bounds(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        boolean contains(float mx, float my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }
    private record Btn(Bounds bounds, Runnable action) {}
    @FunctionalInterface private interface FGet { float get(); }
    @FunctionalInterface private interface FSet { void set(float v); }
    @FunctionalInterface private interface SGet { String get(); }
    @FunctionalInterface private interface SSet { void set(String v); }
    @FunctionalInterface private interface BGet { boolean get(); }
    @FunctionalInterface private interface BSet { void set(boolean v); }
    private static final class Slider {
        final float min, max; final boolean whole; final FGet get; final FSet set; final Bounds bounds;
        Slider(float min, float max, boolean whole, FGet get, FSet set, Bounds bounds) {
            this.min = min; this.max = max; this.whole = whole; this.get = get; this.set = set; this.bounds = bounds;
        }
        void apply(float mx) {
            float t = clamp((mx - bounds.x) / Math.max(1F, bounds.w), 0F, 1F);
            float v = min + t * (max - min);
            if (whole) v = Math.round(v); else v = Math.round(v * 100F) / 100F;
            set.set(clamp(v, min, max));
        }
    }
    private static final class Cycle {
        final Bounds bounds; final String[] options; final SGet get; final SSet set;
        Cycle(Bounds bounds, String[] options, SGet get, SSet set) {
            this.bounds = bounds; this.options = options; this.get = get; this.set = set;
        }
        void next() {
            int i = 0;
            for (int n = 0; n < options.length; n++) if (options[n].equals(get.get())) { i = n; break; }
            set.set(options[(i + 1) % options.length]);
        }
    }
}
