package polaris.screens.clickgui.dropdown;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import polaris.IMinecraft;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.ModuleManager;
import polaris.manager.Manager;
import polaris.screens.clickgui.ClickGuiOpenEffects;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.sounds.SoundManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DropDownScreen extends Screen implements IMinecraft {
    public static final DropDownScreen INSTANCE = new DropDownScreen();

    private static final float PANEL_WIDTH = 80f;
    private static final float PANEL_HEIGHT = 200f;
    private static final float PANEL_SPACING = 5f;

    private final List<DropDownCategoryPanel> panels = new ArrayList<>();

    private static final long ANIM_DURATION_MS = 280L;
    private long animStartMs = 0L;
    private boolean wasClosing = false;

    private final StringBuilder searchBuffer = new StringBuilder();
    private boolean searchFocused = true;
    private float searchAnim = 0f;
    private static final float SEARCH_BAR_WIDTH = 110f;
    private static final float SEARCH_BAR_HEIGHT = 16f;

    private boolean closing;
    private float autoScale = 1.0f;
    private int lastMouseX;
    private int lastMouseY;
    private float lastDelta;
    
    private float openYaw;
    private float openPitch;
    
    private float contentScale = 1.0f;
    private float openCamX;
    private float openCamY;

    public DropDownScreen() {
        super(Component.literal("DropDown"));
    }

    public void openGui() {
        closing = false;
        wasClosing = false;
        animStartMs = System.currentTimeMillis();
        float[] look = ClickGuiOpenEffects.captureLook();
        openYaw = look[0];
        openPitch = look[1];
        
        ClickGuiOpenEffects.onGuiOpening();
        for (DropDownCategoryPanel panel : panels) {
            panel.resetForOpen();
        }
        searchBuffer.setLength(0);
        searchFocused = false;
        searchAnim = 0f;
        if (mc != null) mc.setScreen(this);
        SoundManager.playSoundDirect(SoundManager.SLIDER, 1.0f, 1.0f);
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    protected void init() {
        super.init();
        rebuildPanelLayout();
    }

    private void rebuildPanelLayout() {
        List<ModuleCategory> categories = getDropdownCategories();

        int vw = Render2D.getFixedScaledWidth();
        int vh = Render2D.getFixedScaledHeight();

        float totalWidth = categories.size() * PANEL_WIDTH + Math.max(0, categories.size() - 1) * PANEL_SPACING;

        float widthScale = (vw * 0.95f) / Math.max(totalWidth, 1f);
        float heightScale = (vh * 0.92f) / Math.max(PANEL_HEIGHT, 1f);
        autoScale = Math.min(1.0f, Math.min(widthScale, heightScale));
        autoScale = Math.max(0.5f, autoScale);

        float startX = (vw - totalWidth) / 2f;
        float y = (vh - PANEL_HEIGHT) / 2f;

        if (panels.size() == categories.size()) {
            for (int i = 0; i < categories.size(); i++) {
                DropDownCategoryPanel panel = panels.get(i);
                if (panel.getCategory() == categories.get(i)) {
                    float x = startX + i * (PANEL_WIDTH + PANEL_SPACING);
                    panel.updateLayout(x, y);
                    continue;
                }
                rebuildPanelsFromScratch(categories, startX, y);
                return;
            }
            return;
        }

        rebuildPanelsFromScratch(categories, startX, y);
    }

    private void rebuildPanelsFromScratch(List<ModuleCategory> categories, float startX, float y) {
        panels.clear();
        for (int i = 0; i < categories.size(); i++) {
            float x = startX + i * (PANEL_WIDTH + PANEL_SPACING);
            DropDownCategoryPanel panel = new DropDownCategoryPanel(this, categories.get(i), i,
                    x, y, PANEL_WIDTH, PANEL_HEIGHT);
            panels.add(panel);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastDelta = delta;

        long now = System.currentTimeMillis();
        if (closing != wasClosing) {
            animStartMs = now;
            wasClosing = closing;
        }
        long elapsed = now - animStartMs;
        float t = Mth.clamp(elapsed / (float) ANIM_DURATION_MS, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t);

        float anim;
        if (closing) {
            anim = 1f - eased;
            if (t >= 1f) {
                closing = false;
                if (mc.screen == this) mc.setScreen(null);
                return;
            }
        } else {
            anim = eased;
        }

        int vw = Render2D.getFixedScaledWidth();
        int vh = Render2D.getFixedScaledHeight();

        float openScale = ClickGuiOpenEffects.openScale(anim);
        contentScale = autoScale * openScale;
        openCamX = ClickGuiOpenEffects.cameraOffsetX(openYaw);
        openCamY = ClickGuiOpenEffects.cameraOffsetY(openPitch);

        Render2D.beginFrame(context);
        renderBackgroundBlur(context, vw, vh, anim);
        Render2D.flush();

        float cx = vw / 2f;
        float cy = vh / 2f;
        context.pose().pushMatrix();
        context.pose().translate(openCamX, openCamY);
        context.pose().translate(cx, cy);
        context.pose().scale(contentScale, contentScale);
        context.pose().translate(-cx, -cy);

        Render2D.beginFrame(context);

        float pmx = (float) ClickGuiOpenEffects.unprojectX(mouseX, openCamX, cx, contentScale);
        float pmy = (float) ClickGuiOpenEffects.unprojectY(mouseY, openCamY, cy, contentScale);

        String query = searchBuffer.toString();
        for (DropDownCategoryPanel panel : panels) {
            panel.setSearchQuery(query);
            panel.render(context, (int) pmx, (int) pmy, lastDelta, anim);
        }

        Render2D.flush();

        context.pose().popMatrix();

        
        Render2D.beginFrame(context);
        renderSearchBar(context, mouseX, mouseY, anim);
        Render2D.flush();
    }

    private void renderBackgroundBlur(GuiGraphics context, int vw, int vh, float anim) {
        
        if (ClickGuiOpenEffects.openDarknessEnabled()) {
            ClickGuiOpenEffects.renderDarkness(anim, vw, vh);
            Render2D.blur(0f, 0f, vw, vh, 0f, 18f, 1f,
                    new Color(255, 255, 255, Math.round(35 * anim)).getRGB());
        } else {
            
            float intensity = 0.35f * anim;
            if (intensity > 0.01f) {
                Render2D.blur(0f, 0f, vw, vh, 0f, 12f, 1f,
                        new Color(255, 255, 255, Math.round(22 * intensity)).getRGB());
            }
        }
    }

    private double panelMouseX(double screenX) {
        int vw = Render2D.getFixedScaledWidth();
        return ClickGuiOpenEffects.unprojectX(screenX, openCamX, vw / 2f, contentScale);
    }

    private double panelMouseY(double screenY) {
        int vh = Render2D.getFixedScaledHeight();
        return ClickGuiOpenEffects.unprojectY(screenY, openCamY, vh / 2f, contentScale);
    }

    private void renderSearchBar(GuiGraphics context, float pmx, float pmy, float anim) {
        searchAnim = Mth.lerp(0.18f, searchAnim, anim);

        int vw = Render2D.getFixedScaledWidth();
        int vh = Render2D.getFixedScaledHeight();

        
        
        float barX = (vw - SEARCH_BAR_WIDTH) / 2f;
        float barY = vh - SEARCH_BAR_HEIGHT - 20f;

        float yOffset = (1f - searchAnim) * 12f;
        float bx = barX;
        float by = barY + yOffset;

        
        int bgAlpha = (int) (220 * searchAnim);
        Render2D.blur(bx, by, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, 5f, 22f, 1.2f,
                new Color(15, 15, 18, bgAlpha).getRGB());
        Render2D.outline(bx, by, SEARCH_BAR_WIDTH, SEARCH_BAR_HEIGHT, 5f, 0.5f,
                new Color(60, 70, 90, (int) (180 * searchAnim)).getRGB());

        float iconSize = 7f;
        float iconX = bx + 6f;
        float iconY = by + (SEARCH_BAR_HEIGHT - iconSize) / 2f;
        Render2D.text(FontType.GUI_ICONS, "Q", iconX, iconY, iconSize,
                new Color(170, 170, 180, (int) (255 * searchAnim)).getRGB());

        String shown = searchBuffer.toString();
        boolean empty = shown.isEmpty();
        if (empty) shown = "Поиск...";

        if (searchFocused && !empty && System.currentTimeMillis() % 1000L < 500L) {
            shown = searchBuffer.toString() + "|";
        }

        int textColor = empty
                ? new Color(140, 140, 150, (int) (255 * searchAnim)).getRGB()
                : new Color(225, 225, 230, (int) (255 * searchAnim)).getRGB();
        
        float textSize = 6f;
        float textY = by + (SEARCH_BAR_HEIGHT - textSize) / 2f - 0.5f;
        Render2D.text(FontType.SEMIBOLD, shown,
                iconX + iconSize + 4f, textY, textSize, textColor);

        if (!searchBuffer.isEmpty()) {
            float clearW = 10f;
            float clearX = bx + SEARCH_BAR_WIDTH - clearW - 4f;
            float clearY = by + (SEARCH_BAR_HEIGHT - clearW) / 2f;
            Render2D.text(FontType.SEMIBOLD, "x",
                    clearX + 3f, clearY + 1f, 7f,
                    new Color(170, 170, 180, (int) (255 * searchAnim)).getRGB());
        }
    }

    private boolean searchBarHit(double mx, double my) {
        int vw = Render2D.getFixedScaledWidth();
        int vh = Render2D.getFixedScaledHeight();
        float barX = (vw - SEARCH_BAR_WIDTH) / 2f;
        float barY = vh - SEARCH_BAR_HEIGHT - 20f;
        return mx >= barX && mx <= barX + SEARCH_BAR_WIDTH
                && my >= barY && my <= barY + SEARCH_BAR_HEIGHT;
    }

    private boolean clearButtonHit(double mx, double my) {
        if (searchBuffer.isEmpty()) return false;
        int vw = Render2D.getFixedScaledWidth();
        int vh = Render2D.getFixedScaledHeight();
        float barX = (vw - SEARCH_BAR_WIDTH) / 2f;
        float barY = vh - SEARCH_BAR_HEIGHT - 20f;
        float clearW = 10f;
        float clearX = barX + SEARCH_BAR_WIDTH - clearW - 4f;
        float clearY = barY + (SEARCH_BAR_HEIGHT - clearW) / 2f;
        return mx >= clearX && mx <= clearX + clearW
                && my >= clearY && my <= clearY + clearW;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (closing) return false;
        if (event.button() == 0) {
            if (clearButtonHit(event.x(), event.y())) {
                searchBuffer.setLength(0);
                searchFocused = true;
                return true;
            }
            if (searchBarHit(event.x(), event.y())) {
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }
        }

        double mx = panelMouseX(event.x());
        double my = panelMouseY(event.y());

        for (DropDownCategoryPanel panel : panels) {
            if (panel.mouseClicked(mx, my, event.button())) return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mx = panelMouseX(event.x());
        double my = panelMouseY(event.y());

        for (DropDownCategoryPanel panel : panels) {
            panel.mouseReleased(mx, my, event.button());
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double mx = panelMouseX(mouseX);
        double my = panelMouseY(mouseY);

        for (DropDownCategoryPanel panel : panels) {
            if (panel.mouseScrolled(mx, my, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        
        boolean anyBinding = false;
        for (DropDownCategoryPanel panel : panels) {
            if (panel.isBinding()) {
                anyBinding = true;
                break;
            }
        }
        if (anyBinding) {
            for (DropDownCategoryPanel panel : panels) {
                if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) return true;
            }
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused && !searchBuffer.isEmpty()) {
                searchBuffer.setLength(0);
                return true;
            }
            if (closing) {
                if (mc != null) mc.setScreen(null);
                return true;
            }
            close();
            return true;
        }

        if (searchFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchBuffer.length() > 0) {
                    searchBuffer.deleteCharAt(searchBuffer.length() - 1);
                    playTypingSound();
                }
                return true;
            }
        }

        for (DropDownCategoryPanel panel : panels) {
            if (panel.keyPressed(event.key(), event.scancode(), event.modifiers())) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (DropDownCategoryPanel panel : panels) {
            if (panel.charTyped((char) event.codepoint())) return true;
        }
        if (searchFocused) {
            char c = (char) event.codepoint();
            if (c >= 32 && c < 127 || (c >= 0x0400 && c <= 0x04FF)) {
                if (searchBuffer.length() < 32) {
                    searchBuffer.append(c);
                    playTypingSound();
                }
                return true;
            }
        }
        return super.charTyped(event);
    }

    private void playTypingSound() {
        float pitch = 0.92f + (float) Math.random() * 0.16f;
        SoundManager.playSoundDirect(SoundManager.SEARCH_TYPING, 0.6f, pitch);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing) {
            close();
        }
    }

    public void close() {
        if (!closing) {
            closing = true;
            animStartMs = System.currentTimeMillis();
            ClickGuiOpenEffects.onGuiClosing();
            SoundManager.playSoundDirect(SoundManager.SLIDER, 0.7f, 0.7f);
            for (DropDownCategoryPanel panel : panels) {
                panel.persistState();
            }
        }
    }

    public List<Module> getModulesForCategory(ModuleCategory category) {
        ModuleManager manager = Manager.getModules();
        if (manager == null) {
            return List.of();
        }
        List<Module> result = new ArrayList<>();
        for (Module module : manager.getByCategory(category)) {
            if (!module.isHidden()) {
                result.add(module);
            }
        }
        return result;
    }

    private List<ModuleCategory> getDropdownCategories() {
        List<ModuleCategory> result = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            result.add(category);
        }
        return result;
    }

    public String getCategoryIcon(ModuleCategory category) {
        return category.icon();
    }

    public String getCategoryLabel(ModuleCategory category) {
        return category.getDisplayName();
    }
}

