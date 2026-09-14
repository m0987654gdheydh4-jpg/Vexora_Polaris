package polaris.api.drag.core;

import polaris.api.drag.impl.HudTheme;
import polaris.api.module.impl.visual.Hud;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;


public final class ElementContextMenu {
    private static final float WIDTH = 118.0f;
    private static final float HEADER_H = 20.0f;
    private static final float ROW_H = 17.0f;
    private static final float PAD = 8.0f;
    private static final float ROUND = 7.0f;
    private static final float TEXT_SIZE = 7.0f;
    private static final float HEADER_TEXT_SIZE = 7.5f;
    
    private static final float MARK_W = 2.0f;
    private static final float SWITCH_W = 15.0f;
    private static final float SWITCH_H = 8.0f;

    private static final ElementContextMenu INSTANCE = new ElementContextMenu();

    private final List<Row> rows = new ArrayList<>();
    private ElementComponent target;
    private float x;
    private float y;

    private ElementContextMenu() {
    }

    public static ElementContextMenu getInstance() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return target != null;
    }

    public ElementComponent target() {
        return target;
    }

    public void close() {
        target = null;
        rows.clear();
    }

    
    public void open(ElementComponent component, float mouseX, float mouseY, ElementScreen screen, Runnable onChanged) {
        if (component == null) {
            close();
            return;
        }

        target = component;
        buildRows(component, onChanged);

        float height = height();
        float maxX = screen != null && screen.valid() ? screen.width() - WIDTH - 2.0f : mouseX;
        float maxY = screen != null && screen.valid() ? screen.height() - height - 2.0f : mouseY;
        x = Math.max(2.0f, Math.min(mouseX, Math.max(2.0f, maxX)));
        y = Math.max(2.0f, Math.min(mouseY, Math.max(2.0f, maxY)));
    }

    private void buildRows(ElementComponent component, Runnable onChanged) {
        rows.clear();
        String name = component.title();

        rows.add(new Row("Visible", () -> isElementVisible(name), () -> {
            setElementVisible(name, !isElementVisible(name));
            run(onChanged);
        }));
        rows.add(new Row("Locked", component::locked, () -> {
            component.locked(!component.locked());
            run(onChanged);
        }));
        rows.add(new Row("Bring to front", null, () -> {
            ElementManager.getInstance().bringToFront(component);
            run(onChanged);
        }));
        rows.add(new Row("Reset position", null, () -> {
            component.resetPosition();
            run(onChanged);
            close();
        }));
        rows.add(new Row("Reset all", null, () -> {
            ElementManager.getInstance().resetAllPositions();
            run(onChanged);
            close();
        }));
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    private float height() {
        return HEADER_H + rows.size() * ROW_H + PAD;
    }

    
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!isOpen()) {
            return false;
        }

        float height = height();
        boolean inside = mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height;
        if (!inside) {
            close();
            return false;
        }
        if (button != 0) {
            return true;
        }

        float rowY = y + HEADER_H;
        for (Row row : rows) {
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                row.action.run();
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    public void render(float mouseX, float mouseY) {
        if (!isOpen()) {
            return;
        }

        float height = height();
        int accent = HudTheme.current().accentColor;

        
        Render2D.rect(x - 1.5f, y - 1.0f, WIDTH + 3.0f, height + 3.5f, ROUND + 1.5f,
                ColorUtil.rgba(0, 0, 0, 70));
        Render2D.rect(x, y, WIDTH, height, ROUND, ColorUtil.rgba(11, 11, 14, 247));
        Render2D.outline(x, y, WIDTH, height, ROUND, 0.8f, ColorUtil.rgba(58, 58, 66, 240));

        
        Render2D.rect(x + 1.0f, y + 1.0f, WIDTH - 2.0f, HEADER_H - 1.0f, ROUND - 1.0f, ROUND - 1.0f, 0.0f, 0.0f,
                ColorUtil.rgba(255, 255, 255, 10));
        Render2D.rect(x + PAD, y + (HEADER_H - 8.0f) * 0.5f, MARK_W, 8.0f, MARK_W * 0.5f, accent);

        String title = trim(target.title(), WIDTH - PAD * 2.0f - MARK_W - 5.0f);
        Render2D.text(FontType.BOLD, title, x + PAD + MARK_W + 5.0f,
                y + (HEADER_H - HEADER_TEXT_SIZE) * 0.5f + 0.5f, HEADER_TEXT_SIZE,
                ColorUtil.rgba(240, 242, 248, 255));
        Render2D.rect(x + PAD, y + HEADER_H - 0.6f, WIDTH - PAD * 2.0f, 0.6f, ColorUtil.rgba(58, 58, 66, 220));

        float rowY = y + HEADER_H;
        for (Row row : rows) {
            boolean hovered = mouseX >= x && mouseX <= x + WIDTH && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                Render2D.rect(x + 2.0f, rowY + 1.0f, WIDTH - 4.0f, ROW_H - 2.0f, 4.0f,
                        ColorUtil.rgba(255, 255, 255, 20));
                Render2D.rect(x + 2.0f, rowY + 3.5f, MARK_W, ROW_H - 7.0f, MARK_W * 0.5f, accent);
            }

            boolean toggle = row.state != null;
            boolean on = toggle && row.state.getAsBoolean();
            int textColor = hovered
                    ? ColorUtil.rgba(242, 244, 250, 255)
                    : ColorUtil.rgba(176, 179, 189, 255);
            Render2D.text(FontType.BOLD, row.label, x + PAD + MARK_W + 5.0f,
                    rowY + (ROW_H - TEXT_SIZE) * 0.5f + 0.5f, TEXT_SIZE, textColor);

            if (toggle) {
                float switchX = x + WIDTH - PAD - SWITCH_W;
                float switchY = rowY + (ROW_H - SWITCH_H) * 0.5f;
                Render2D.rect(switchX, switchY, SWITCH_W, SWITCH_H, SWITCH_H * 0.5f,
                        on ? ColorUtil.withAlpha(accent, 225) : ColorUtil.rgba(255, 255, 255, 22));
                float knob = SWITCH_H - 3.0f;
                float knobX = on ? switchX + SWITCH_W - knob - 1.5f : switchX + 1.5f;
                Render2D.rect(knobX, switchY + 1.5f, knob, knob, knob * 0.5f,
                        on ? ColorUtil.rgba(250, 251, 255, 255) : ColorUtil.rgba(150, 152, 162, 255));
            }
            rowY += ROW_H;
        }
    }

    private static String trim(String text, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (Render2D.textWidth(FontType.BOLD, text, TEXT_SIZE) <= maxWidth) {
            return text;
        }
        String value = text;
        while (value.length() > 1 && Render2D.textWidth(FontType.BOLD, value + "..", TEXT_SIZE) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "..";
    }

    private static boolean isElementVisible(String name) {
        Hud hud = Hud.getInstance();
        return hud == null || hud.isElementEnabled(name);
    }

    private static void setElementVisible(String name, boolean visible) {
        Hud hud = Hud.getInstance();
        if (hud != null) {
            hud.setElementEnabled(name, visible);
        }
    }

    private record Row(String label, BooleanSupplier state, Runnable action) {
    }
}
