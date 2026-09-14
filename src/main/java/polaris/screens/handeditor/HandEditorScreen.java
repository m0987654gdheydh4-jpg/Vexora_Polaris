package polaris.screens.handeditor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import polaris.api.drag.impl.HudTheme;
import polaris.api.module.Module;
import polaris.api.module.ModuleManager;
import polaris.api.module.impl.visual.HandTweaker;
import polaris.api.settings.Setting;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ButtonSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.manager.Manager;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class HandEditorScreen extends Screen {
    
    private static final float COL_W = 148f;
    private static final float MARGIN = 12f;
    private static final float PAD = 8f;
    private static final float ROUND = 6f;
    private static final float GAP = 6f;
    private static final float TITLE_H = 16f;
    private static final float BUTTON_H = 18f;
    private static final float SCROLLBAR_W = 3f;
    private static final float SCROLLBAR_GAP = 3f;

    
    private static final String[] HAND_LABELS = {"X", "Y", "Z", "Scale", "Pitch", "Yaw", "Roll"};
    private static final float HAND_ROW_H = 18f;
    private static final float TRACK_H = 3f;

    
    
    private static final String[] LEFT_MODULES = {"Hands", "ShaderHand"};
    private static final String[] RIGHT_MODULES = {"Swing Animation"};
    private static final float MODULE_ROW_H = 18f;
    private static final float SETTING_ROW_H = 16f;
    private static final float SETTING_SLIDER_H = 23f;
    private static final float SETTING_INDENT = 10f;
    
    private static final float RAIL_X = 4f;
    private static final float RAIL_W = 1.5f;
    private static final float MIN_MODULE_PANEL_H = 58f;
    private static final float LABEL_SIZE = 7f;
    private static final float VALUE_SIZE = 6.5f;
    
    private static final float CHIP_PAD_X = 4f;
    private static final float CHIP_H = 10f;

    
    
    private static final float GRAB_HALF_X = 0.38f;
    private static final float GRAB_HALF_Y = 0.34f;
    private static final float GRAB_MIN = 24f;
    private static final float FALLBACK_PER_UNIT = 320f;

    private final Screen parent;

    private int handDragRow = -1;
    private boolean handDragRight;
    private boolean grabbing;
    private boolean grabbingRight;
    private double grabOffsetX;
    private double grabOffsetY;
    private float perUnitX = FALLBACK_PER_UNIT;
    private float perUnitY = -FALLBACK_PER_UNIT;

    private String expandedModule;
    private final float[] moduleScroll = new float[2];
    private Row settingDragRow;
    private float settingDragPanelX;

    public HandEditorScreen(Screen parent) {
        super(Component.literal("Hand Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HandTweaker tweaker = HandTweaker.getInstance();
        if (tweaker == null) {
            onClose();
            return;
        }

        Render2D.beginFrame(graphics);
        int accent = HudTheme.current().accentColor;

        renderModulePanel(graphics, false, mouseX, mouseY, accent);
        renderModulePanel(graphics, true, mouseX, mouseY, accent);

        renderHandPanel(tweaker, false, mouseX, mouseY, accent);
        renderHandPanel(tweaker, true, mouseX, mouseY, accent);

        renderHint(accent);
        renderButton(resetBothRect(), "Reset both", inside(mouseX, mouseY, resetBothRect()), accent);

        Render2D.flush();
    }

    

    private float columnX(boolean right) {
        return right ? width - MARGIN - COL_W : MARGIN;
    }

    private float handPanelH() {
        return TITLE_H + HAND_LABELS.length * HAND_ROW_H + PAD;
    }

    private float handPanelY() {
        return Math.max(MARGIN + MIN_MODULE_PANEL_H + GAP, height * 0.5f - handPanelH() * 0.5f);
    }

    private float modulePanelY() {
        return MARGIN + TITLE_H + 4f;
    }

    
    private float modulePanelH() {
        return Math.max(MIN_MODULE_PANEL_H, handPanelY() - GAP - MARGIN);
    }

    private float moduleViewH() {
        return modulePanelH() - TITLE_H - 4f - PAD * 0.5f;
    }

    private float[] resetRect(boolean right) {
        return new float[]{columnX(right), handPanelY() + handPanelH() + GAP, COL_W, BUTTON_H};
    }

    private float[] resetBothRect() {
        float w = 140f;
        return new float[]{width * 0.5f - w * 0.5f, height - MARGIN - BUTTON_H, w, BUTTON_H};
    }

    private float handRowY(int row) {
        return handPanelY() + TITLE_H + row * HAND_ROW_H;
    }

    private static boolean inside(double mx, double my, float[] rect) {
        return mx >= rect[0] && mx <= rect[0] + rect[2] && my >= rect[1] && my <= rect[1] + rect[3];
    }

    
    private float[] track(float panelX, float rowY, float rowH, float indent, float reserveRight) {
        float x = panelX + PAD + indent;
        float w = COL_W - PAD * 2f - indent - reserveRight;
        return new float[]{x, rowY + rowH - TRACK_H - 3.5f, Math.max(4f, w)};
    }

    

    private void renderHandPanel(HandTweaker tweaker, boolean right, int mouseX, int mouseY, int accent) {
        float x = columnX(right);
        float y = handPanelY();
        float h = handPanelH();

        surface(x, y, COL_W, h);
        header(x, y, right ? "Right hand" : "Left hand");

        NumberSetting[] settings = tweaker.all(right);
        for (int i = 0; i < settings.length; i++) {
            float rowY = handRowY(i);
            NumberSetting setting = settings[i];
            label(x + PAD, rowY, HAND_LABELS[i], ColorUtil.rgba(168, 171, 181, 255));
            value(x, rowY, format(setting.getValue()), ColorUtil.rgba(230, 233, 240, 255));

            float[] t = track(x, rowY, HAND_ROW_H, 0f, 0f);
            slider(t, (float) progressOf(setting), accent, true);
        }

        float[] reset = resetRect(right);
        renderButton(reset, right ? "Reset right" : "Reset left", inside(mouseX, mouseY, reset), accent);
    }

    

    private enum Kind { MODULE, BOOL, NUMBER, MODE, COLOR, BUTTON, READONLY }

    private record Row(Kind kind, float y, float h, Module module, Setting<?> setting, String label) {
    }

    private static String[] columnModules(boolean right) {
        return right ? RIGHT_MODULES : LEFT_MODULES;
    }

    private static Module lookup(String name) {
        ModuleManager manager = Manager.getModules();
        return manager == null ? null : manager.getByName(name).orElse(null);
    }

    private static Kind kindOf(Setting<?> setting) {
        return switch (setting.getType()) {
            case BOOLEAN -> Kind.BOOL;
            case NUMBER -> Kind.NUMBER;
            case MODE -> Kind.MODE;
            case COLOR -> Kind.COLOR;
            case BUTTON -> Kind.BUTTON;
            default -> Kind.READONLY;
        };
    }

    
    private List<Row> moduleRows(boolean right) {
        List<Row> rows = new ArrayList<>();
        float y = 0f;
        for (String name : columnModules(right)) {
            Module module = lookup(name);
            rows.add(new Row(Kind.MODULE, y, MODULE_ROW_H, module, null, name));
            y += MODULE_ROW_H;
            if (module == null || !name.equals(expandedModule)) {
                continue;
            }
            for (Setting<?> setting : module.getSettings()) {
                if (!setting.isVisible()) {
                    continue;
                }
                Kind kind = kindOf(setting);
                float h = kind == Kind.NUMBER || kind == Kind.COLOR ? SETTING_SLIDER_H : SETTING_ROW_H;
                rows.add(new Row(kind, y, h, module, setting, setting.getName()));
                y += h;
            }
            y += 2f;
        }
        return rows;
    }

    private float moduleContentH(boolean right) {
        List<Row> rows = moduleRows(right);
        if (rows.isEmpty()) {
            return 0f;
        }
        Row last = rows.get(rows.size() - 1);
        return last.y + last.h;
    }

    private float moduleMaxScroll(boolean right) {
        return Math.max(0f, moduleContentH(right) - moduleViewH());
    }

    private float scroll(boolean right) {
        int index = right ? 1 : 0;
        moduleScroll[index] = Math.clamp(moduleScroll[index], 0f, moduleMaxScroll(right));
        return moduleScroll[index];
    }

    private void renderModulePanel(GuiGraphics graphics, boolean right, int mouseX, int mouseY, int accent) {
        float x = columnX(right);
        float y = MARGIN;
        float h = modulePanelH();
        float viewY = modulePanelY();
        float viewH = moduleViewH();

        surface(x, y, COL_W, h);
        header(x, y, right ? "Hand FX" : "Hand modules");

        float offset = scroll(right);
        float maxScroll = moduleMaxScroll(right);
        float contentW = COL_W - PAD * 2f - (maxScroll > 0f ? SCROLLBAR_W + SCROLLBAR_GAP : 0f);

        Render2D.pushScissor(graphics, x, viewY, COL_W, viewH);
        for (Row row : moduleRows(right)) {
            float rowY = viewY + row.y - offset;
            if (rowY + row.h < viewY || rowY > viewY + viewH) {
                continue;
            }
            boolean hovered = mouseX >= x && mouseX <= x + COL_W && mouseY >= rowY && mouseY < rowY + row.h
                    && mouseY >= viewY && mouseY < viewY + viewH;
            if (row.kind == Kind.MODULE) {
                renderModuleRow(row, x, rowY, contentW, hovered, accent);
            } else {
                renderSettingRow(row, x, rowY, contentW, hovered, accent);
            }
        }
        Render2D.popScissor(graphics);

        if (maxScroll > 0f) {
            float trackX = x + COL_W - PAD - SCROLLBAR_W;
            float thumbH = Math.max(14f, viewH * (viewH / (viewH + maxScroll)));
            float thumbY = viewY + (offset / maxScroll) * (viewH - thumbH);
            Render2D.rect(trackX, viewY, SCROLLBAR_W, viewH, SCROLLBAR_W * 0.5f, ColorUtil.rgba(255, 255, 255, 16));
            Render2D.rect(trackX, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W * 0.5f, ColorUtil.rgba(255, 255, 255, 62));
        }
    }

    private void renderModuleRow(Row row, float x, float rowY, float contentW, boolean hovered, int accent) {
        boolean missing = row.module == null;
        boolean on = !missing && row.module.isEnabled();
        boolean open = !missing && row.label.equals(expandedModule);

        if (hovered && !missing) {
            Render2D.rect(x + 3f, rowY + 1f, contentW + PAD * 2f - 6f, row.h - 2f, 3f,
                    ColorUtil.rgba(255, 255, 255, 16));
        }
        if (open) {
            Render2D.rect(x + 3f, rowY + 3f, 2f, row.h - 6f, 1f, accent);
        }

        int color = missing
                ? ColorUtil.rgba(108, 110, 118, 255)
                : (hovered || open ? ColorUtil.rgba(240, 242, 248, 255) : ColorUtil.rgba(178, 181, 191, 255));
        Render2D.text(FontType.INTER_SEMI, row.label, x + PAD + 4f, rowY + (row.h - 7f) * 0.5f + 0.5f, 7f, color);
        Render2D.text(FontType.INTER_MEDIUM, open ? "-" : "+", x + PAD - 1f,
                rowY + (row.h - 7f) * 0.5f + 0.5f, 7f, color);

        if (missing) {
            return;
        }
        pill(x + PAD + contentW - 14f, rowY + (row.h - 7f) * 0.5f, 14f, 7f, on, accent);
    }

    private void renderSettingRow(Row row, float x, float rowY, float contentW, boolean hovered, int accent) {
        float labelX = x + PAD + SETTING_INDENT;
        float rightEdge = x + PAD + contentW;
        boolean sliderRow = row.kind == Kind.NUMBER || row.kind == Kind.COLOR;
        
        float labelY = sliderRow ? rowY + 3f : rowY + (row.h - LABEL_SIZE) * 0.5f + 0.5f;
        int labelColor = hovered ? ColorUtil.rgba(226, 229, 237, 255) : ColorUtil.rgba(160, 163, 173, 255);

        if (hovered) {
            Render2D.rect(x + RAIL_X + RAIL_W + 2f, rowY, rightEdge - x - RAIL_X - RAIL_W - 2f, row.h, 3f,
                    ColorUtil.rgba(255, 255, 255, 12));
        }
        
        Render2D.rect(x + RAIL_X, rowY, RAIL_W, row.h, ColorUtil.withAlpha(accent, hovered ? 120 : 58));

        Render2D.text(FontType.INTER_MEDIUM, row.label, labelX, labelY, LABEL_SIZE, labelColor);

        switch (row.kind) {
            case BOOL -> {
                boolean on = Boolean.TRUE.equals(((BooleanSetting) row.setting).getValue());
                pill(rightEdge - 14f, rowY + (row.h - 7.5f) * 0.5f, 14f, 7.5f, on, accent);
            }
            case MODE -> chip(rightEdge, rowY, row.h, String.valueOf(row.setting.getValue()),
                    hovered ? ColorUtil.withAlpha(accent, 46) : ColorUtil.rgba(255, 255, 255, 16),
                    hovered ? ColorUtil.withAlpha(accent, 255) : ColorUtil.rgba(228, 231, 238, 255));
            case NUMBER -> {
                NumberSetting number = (NumberSetting) row.setting;
                chip(rightEdge, rowY + 3f - (row.h - CHIP_H) * 0.5f, row.h, format(number.getValue()),
                        ColorUtil.rgba(255, 255, 255, 16), ColorUtil.rgba(230, 233, 240, 255));
                slider(settingTrack(row, x, rowY, contentW), (float) progressOf(number), accent, true);
            }
            case COLOR -> {
                Color current = ((ColorSetting) row.setting).getValue();
                float sw = 9f;
                Render2D.rect(rightEdge - sw, rowY + 2.5f, sw, sw, 2.5f, ColorUtil.withAlpha(current.getRGB(), 255));
                Render2D.outline(rightEdge - sw, rowY + 2.5f, sw, sw, 2.5f, 0.7f, ColorUtil.rgba(255, 255, 255, 40));
                float[] t = settingTrack(row, x, rowY, contentW);
                int steps = 16;
                float stepW = t[2] / steps;
                for (int i = 0; i < steps; i++) {
                    Render2D.rect(t[0] + i * stepW, t[1], stepW + 0.6f, TRACK_H,
                            ColorUtil.withAlpha(Color.HSBtoRGB(i / (float) steps, 0.85f, 1.0f), 255));
                }
                float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
                float knob = 4.5f;
                Render2D.rect(t[0] + t[2] * hsb[0] - knob * 0.5f, t[1] + TRACK_H * 0.5f - knob * 0.5f,
                        knob, knob, knob * 0.5f, ColorUtil.rgba(245, 246, 250, 255));
            }
            case BUTTON -> chip(rightEdge, rowY, row.h, "run",
                    hovered ? ColorUtil.withAlpha(accent, 60) : ColorUtil.rgba(255, 255, 255, 16),
                    hovered ? ColorUtil.withAlpha(accent, 255) : ColorUtil.rgba(196, 199, 208, 255));
            default -> {
                String text = readonlyText(row.setting);
                float w = Render2D.textWidth(FontType.INTER_MEDIUM, text, VALUE_SIZE);
                Render2D.text(FontType.INTER_MEDIUM, text, rightEdge - w,
                        rowY + (row.h - VALUE_SIZE) * 0.5f + 0.5f, VALUE_SIZE,
                        ColorUtil.rgba(124, 127, 136, 255));
            }
        }
    }

    
    private void chip(float rightEdge, float rowY, float rowH, String text, int fill, int textColor) {
        float w = Render2D.textWidth(FontType.INTER_SEMI, text, VALUE_SIZE) + CHIP_PAD_X * 2f;
        float x = rightEdge - w;
        float y = rowY + (rowH - CHIP_H) * 0.5f;
        Render2D.rect(x, y, w, CHIP_H, 3f, fill);
        Render2D.text(FontType.INTER_SEMI, text, x + CHIP_PAD_X, y + (CHIP_H - VALUE_SIZE) * 0.5f + 0.5f,
                VALUE_SIZE, textColor);
    }

    private static String readonlyText(Setting<?> setting) {
        if (setting instanceof MultiModeSetting multi) {
            return multi.selectedCount() + "/" + multi.getModes().size();
        }
        String text = String.valueOf(setting.getValue());
        return text.length() > 12 ? text.substring(0, 11) + ".." : text;
    }

    private float[] settingTrack(Row row, float panelX, float rowY, float contentW) {
        float x = panelX + PAD + SETTING_INDENT;
        float w = contentW - SETTING_INDENT;
        return new float[]{x, rowY + row.h - TRACK_H - 5f, Math.max(4f, w)};
    }

    

    
    private float[] grabBox(float[] projected) {
        float halfW = Math.max(GRAB_MIN, Math.abs(projected[2]) * GRAB_HALF_X);
        float halfH = Math.max(GRAB_MIN, Math.abs(projected[3]) * GRAB_HALF_Y);
        halfW = Math.min(halfW, width * 0.35f);
        halfH = Math.min(halfH, height * 0.4f);
        return new float[]{projected[0] - halfW, projected[1] - halfH, halfW * 2f, halfH * 2f};
    }

    private boolean hitsHand(double mx, double my, float[] projected) {
        float[] box = grabBox(projected);
        return mx >= box[0] && mx <= box[0] + box[2] && my >= box[1] && my <= box[1] + box[3];
    }

    
    private static double anchorDistSq(double mx, double my, float[] projected) {
        double dx = mx - projected[0];
        double dy = my - projected[1];
        return dx * dx + dy * dy;
    }

    

    private void surface(float x, float y, float w, float h) {
        Render2D.rect(x, y, w, h, ROUND, ColorUtil.rgba(10, 10, 13, 228));
        Render2D.outline(x, y, w, h, ROUND, 0.8f, ColorUtil.rgba(66, 66, 74, 234));
    }

    private void header(float x, float y, String title) {
        Render2D.text(FontType.INTER_SEMI, title, x + PAD, y + (TITLE_H - 7.5f) * 0.5f + 0.5f, 7.5f,
                ColorUtil.rgba(238, 240, 246, 255));
        Render2D.rect(x + PAD, y + TITLE_H - 0.6f, COL_W - PAD * 2f, 0.6f, ColorUtil.rgba(64, 64, 72, 215));
    }

    private void label(float x, float rowY, String text, int color) {
        Render2D.text(FontType.INTER_MEDIUM, text, x, rowY + 1.5f, LABEL_SIZE + 0.5f, color);
    }

    private void value(float panelX, float rowY, String text, int color) {
        float w = Render2D.textWidth(FontType.INTER_SEMI, text, LABEL_SIZE + 0.5f);
        Render2D.text(FontType.INTER_SEMI, text, panelX + COL_W - PAD - w, rowY + 1.5f, LABEL_SIZE + 0.5f, color);
    }

    private void slider(float[] t, float progress, int accent, boolean knob) {
        Render2D.rect(t[0], t[1], t[2], TRACK_H, TRACK_H * 0.5f, ColorUtil.rgba(255, 255, 255, 20));
        Render2D.rect(t[0], t[1], Math.max(TRACK_H, t[2] * progress), TRACK_H, TRACK_H * 0.5f, accent);
        if (knob) {
            float k = 4.5f;
            Render2D.rect(t[0] + t[2] * progress - k * 0.5f, t[1] + TRACK_H * 0.5f - k * 0.5f, k, k, k * 0.5f,
                    ColorUtil.rgba(245, 246, 250, 255));
        }
    }

    private void pill(float x, float y, float w, float h, boolean on, int accent) {
        Render2D.rect(x, y, w, h, h * 0.5f,
                on ? ColorUtil.withAlpha(accent, 222) : ColorUtil.rgba(255, 255, 255, 22));
        float knob = h - 2.6f;
        float knobX = on ? x + w - knob - 1.3f : x + 1.3f;
        Render2D.rect(knobX, y + 1.3f, knob, knob, knob * 0.5f,
                on ? ColorUtil.rgba(250, 251, 255, 255) : ColorUtil.rgba(150, 152, 162, 255));
    }

    private void renderButton(float[] rect, String text, boolean hovered, int accent) {
        Render2D.rect(rect[0], rect[1], rect[2], rect[3], 4f,
                hovered ? ColorUtil.withAlpha(accent, 60) : ColorUtil.rgba(255, 255, 255, 16));
        Render2D.outline(rect[0], rect[1], rect[2], rect[3], 4f, 0.8f,
                hovered ? ColorUtil.withAlpha(accent, 190) : ColorUtil.rgba(255, 255, 255, 28));
        float size = 7.5f;
        float w = Render2D.textWidth(FontType.INTER_SEMI, text, size);
        Render2D.text(FontType.INTER_SEMI, text, rect[0] + (rect[2] - w) * 0.5f,
                rect[1] + (rect[3] - size) * 0.5f + 0.5f, size, ColorUtil.rgba(238, 240, 246, 255));
    }

    private void renderHint(int accent) {
        String hint = "Drag the item itself  •  wheel = depth  •  click a module to unfold";
        float size = 7.5f;
        float textW = Render2D.textWidth(FontType.INTER_MEDIUM, hint, size);
        float boxW = textW + PAD * 2f;
        float boxH = 16f;
        float x = width * 0.5f - boxW * 0.5f;
        Render2D.rect(x, MARGIN, boxW, boxH, 4f, ColorUtil.rgba(10, 10, 13, 200));
        Render2D.outline(x, MARGIN, boxW, boxH, 4f, 0.7f, ColorUtil.withAlpha(accent, 90));
        Render2D.text(FontType.INTER_MEDIUM, hint, x + PAD, MARGIN + (boxH - size) * 0.5f + 0.5f, size,
                ColorUtil.rgba(205, 208, 216, 255));
    }

    

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        
        if (handleModulePanelClick(mx, my, button)) {
            return true;
        }

        HandTweaker tweaker = HandTweaker.getInstance();
        if (tweaker == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubled);
        }

        if (inside(mx, my, resetBothRect())) {
            tweaker.resetBoth();
            return true;
        }

        
        float[] picked = null;
        boolean pickedRight = false;
        double pickedDist = Double.MAX_VALUE;
        for (boolean right : new boolean[]{true, false}) {
            float[] p = tweaker.projectHand(right, width, height);
            if (p == null || !hitsHand(mx, my, p)) {
                continue;
            }
            double dist = anchorDistSq(mx, my, p);
            if (dist < pickedDist) {
                pickedDist = dist;
                picked = p;
                pickedRight = right;
            }
        }
        if (picked != null) {
            grabbing = true;
            grabbingRight = pickedRight;
            grabOffsetX = mx - picked[0];
            grabOffsetY = my - picked[1];
            perUnitX = picked[2];
            perUnitY = picked[3];
            return true;
        }

        for (boolean right : new boolean[]{false, true}) {
            if (inside(mx, my, resetRect(right))) {
                tweaker.reset(right);
                return true;
            }

            float x = columnX(right);
            if (mx < x || mx > x + COL_W) {
                continue;
            }
            NumberSetting[] settings = tweaker.all(right);
            for (int i = 0; i < settings.length; i++) {
                float rowY = handRowY(i);
                if (my >= rowY && my <= rowY + HAND_ROW_H) {
                    handDragRow = i;
                    handDragRight = right;
                    applyHandSlider(settings[i], mx, x);
                    return true;
                }
            }
            if (my >= handPanelY() && my <= handPanelY() + handPanelH()) {
                return true;
            }
        }

        return true;
    }

    private boolean handleModulePanelClick(double mx, double my, int button) {
        for (boolean right : new boolean[]{false, true}) {
            float x = columnX(right);
            if (mx < x || mx > x + COL_W || my < MARGIN || my > MARGIN + modulePanelH()) {
                continue;
            }
            float viewY = modulePanelY();
            float viewH = moduleViewH();
            if (my < viewY || my > viewY + viewH) {
                return true;
            }

            float offset = scroll(right);
            float contentW = COL_W - PAD * 2f - (moduleMaxScroll(right) > 0f ? SCROLLBAR_W + SCROLLBAR_GAP : 0f);
            for (Row row : moduleRows(right)) {
                float rowY = viewY + row.y - offset;
                if (my < rowY || my >= rowY + row.h) {
                    continue;
                }
                if (row.kind == Kind.MODULE) {
                    if (row.module == null) {
                        return true;
                    }
                    if (mx >= x + PAD + contentW - 17f) {
                        row.module.toggle();
                    } else {
                        expandedModule = row.label.equals(expandedModule) ? null : row.label;
                    }
                    return true;
                }
                applySettingClick(row, x, rowY, contentW, mx, button);
                return true;
            }
            return true;
        }
        return false;
    }

    private void applySettingClick(Row row, float panelX, float rowY, float contentW, double mx, int button) {
        switch (row.kind) {
            case BOOL -> {
                BooleanSetting bool = (BooleanSetting) row.setting;
                bool.setValue(!Boolean.TRUE.equals(bool.getValue()));
            }
            case MODE -> {
                ModeSetting mode = (ModeSetting) row.setting;
                List<String> modes = mode.getModes();
                if (modes.isEmpty()) {
                    return;
                }
                int index = Math.max(0, modes.indexOf(mode.getValue()));
                int next = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                        ? (index - 1 + modes.size()) % modes.size()
                        : (index + 1) % modes.size();
                mode.setValue(modes.get(next));
            }
            case BUTTON -> ((ButtonSetting) row.setting).press();
            case NUMBER, COLOR -> {
                settingDragRow = row;
                settingDragPanelX = panelX;
                applySettingSlider(row, panelX, rowY, contentW, mx);
            }
            default -> {
            }
        }
    }

    private void applySettingSlider(Row row, float panelX, float rowY, float contentW, double mx) {
        float[] t = settingTrack(row, panelX, rowY, contentW);
        double p = Math.clamp((mx - t[0]) / t[2], 0.0, 1.0);
        if (row.kind == Kind.NUMBER) {
            NumberSetting number = (NumberSetting) row.setting;
            number.setValue(number.getMin() + (number.getMax() - number.getMin()) * p);
            return;
        }
        ColorSetting color = (ColorSetting) row.setting;
        Color current = color.getValue();
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        int rgb = Color.HSBtoRGB((float) p, Math.max(0.15f, hsb[1]), Math.max(0.25f, hsb[2]));
        color.setValue(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, current.getAlpha()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        HandTweaker tweaker = HandTweaker.getInstance();
        if (tweaker == null) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        if (settingDragRow != null) {
            
            boolean right = settingDragPanelX > width * 0.5f;
            float contentW = COL_W - PAD * 2f - (moduleMaxScroll(right) > 0f ? SCROLLBAR_W + SCROLLBAR_GAP : 0f);
            applySettingSlider(settingDragRow, settingDragPanelX,
                    modulePanelY() + settingDragRow.y - scroll(right), contentW, mouseX);
            return true;
        }

        if (handDragRow >= 0) {
            applyHandSlider(tweaker.all(handDragRight)[handDragRow], mouseX, columnX(handDragRight));
            return true;
        }

        if (grabbing) {
            float[] p = tweaker.projectHand(grabbingRight, width, height);
            if (p == null) {
                nudge(tweaker.x(grabbingRight), deltaX / FALLBACK_PER_UNIT);
                nudge(tweaker.y(grabbingRight), -deltaY / FALLBACK_PER_UNIT);
                return true;
            }
            perUnitX = p[2];
            perUnitY = p[3];
            NumberSetting sx = tweaker.x(grabbingRight);
            NumberSetting sy = tweaker.y(grabbingRight);
            set(sx, sx.getValue() + ((mouseX - grabOffsetX) - p[0]) / perUnitX);
            set(sy, sy.getValue() + ((mouseY - grabOffsetY) - p[1]) / perUnitY);
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        handDragRow = -1;
        grabbing = false;
        settingDragRow = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0.0) {
            return false;
        }

        
        for (boolean right : new boolean[]{false, true}) {
            float x = columnX(right);
            if (mouseX >= x && mouseX <= x + COL_W && mouseY >= MARGIN && mouseY <= MARGIN + modulePanelH()) {
                int index = right ? 1 : 0;
                moduleScroll[index] = Math.clamp(
                        moduleScroll[index] - (float) vertical * 14f, 0f, moduleMaxScroll(right));
                return true;
            }
        }

        HandTweaker tweaker = HandTweaker.getInstance();
        if (tweaker == null) {
            return false;
        }
        boolean right = mouseX >= width * 0.5;
        NumberSetting z = tweaker.z(right);
        nudge(z, vertical * z.getStep() * 2.0 / (z.getMax() - z.getMin()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    

    private void applyHandSlider(NumberSetting setting, double mouseX, float panelX) {
        float trackX = panelX + PAD;
        float trackW = COL_W - PAD * 2f;
        double t = Math.clamp((mouseX - trackX) / trackW, 0.0, 1.0);
        setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * t);
    }

    private static void nudge(NumberSetting setting, double fraction) {
        double range = setting.getMax() - setting.getMin();
        set(setting, setting.getValue() + range * fraction);
    }

    private static void set(NumberSetting setting, double value) {
        setting.setValue(Math.clamp(value, setting.getMin(), setting.getMax()));
    }

    private static double progressOf(NumberSetting setting) {
        double range = setting.getMax() - setting.getMin();
        if (range <= 0.0) {
            return 0.0;
        }
        return Math.clamp((setting.getValue() - setting.getMin()) / range, 0.0, 1.0);
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
