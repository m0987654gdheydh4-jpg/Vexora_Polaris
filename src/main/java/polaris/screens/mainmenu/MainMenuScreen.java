package polaris.screens.mainmenu;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ui.gif.GifRenderer;
import polaris.utils.render.ui.gif.MainMenuGifPreloader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public final class MainMenuScreen extends Screen {
    private static final float REF_W = 960f;
    private static final float REF_H = 540f;

    private static final float BASE_BTN_H = 16f;
    private static final float BASE_BTN_GAP = 6f;
    private static final float BASE_BTN_R = 3f;
    private static final float BASE_PRIMARY_W = 130f;

    public static boolean isRussian = true;

    private final Minecraft mc = Minecraft.getInstance();
    private GifRenderer backgroundGif;

    private final List<MenuButton> buttons = new ArrayList<>();
    private float uiScale = 1f;
    private float lastDw = -1f;
    private float lastDh = -1f;

    private Supplier<PlayerSkin> skinLookup;
    private String cachedPlayerName = "Player";

    // Сканирование и хранение файлов кастомных фонов
    private final List<File> discoveredBackgrounds = new ArrayList<>();
    private int selectedBackgroundIndex = 0;
    private float bgPanelScrollY = 0f;

    public enum MenuState {
        MAIN, VEXORA, BACKGROUND
    }
    public MenuState currentMenu = MenuState.MAIN;

    public MainMenuScreen() {
        super(Component.literal("Main Menu"));
    }

    @Override
    protected void init() {
        super.init();
        backgroundGif = MainMenuGifPreloader.background();
        if (backgroundGif != null && !backgroundGif.isReady() && !backgroundGif.isFailed()) {
            backgroundGif.ensureLoaded();
        }
        ensureSkinLookup();
        rebuildButtons();
        scanBackgroundsFolder();
    }

    private float designW() { return Render2D.getFixedScaledWidth(); }
    private float designH() { return Render2D.getFixedScaledHeight(); }
    private float mx(double guiX) { return (float) Render2D.guiToFixed(guiX); }
    private float my(double guiY) { return (float) Render2D.guiToFixed(guiY); }

    private float computeUiScale(float dw, float dh) {
        float s = Math.min(dw / REF_W, dh / REF_H);
        return Math.max(0.70f, Math.min(s, 1.20f));
    }

    private void scanBackgroundsFolder() {
        discoveredBackgrounds.clear();
        try {
            File bgDir = new File(mc.gameDirectory, "polaris" + File.separator + "backgrounds");
            if (!bgDir.exists()) bgDir.mkdirs();

            File[] files = bgDir.listFiles((dir, name) -> {
                String low = name.toLowerCase();
                return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".gif");
            });

            if (files != null) {
                for (File f : files) {
                    discoveredBackgrounds.add(f);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    private void ensureSkinLookup() {
        cachedPlayerName = playerName();
        try {
            var user = mc.getUser();
            if (user == null) {
                skinLookup = DefaultPlayerSkin::getDefaultSkin;
                return;
            }
            java.util.UUID uuid = user.getProfileId();
            String name = user.getName() != null ? user.getName() : "Player";
            GameProfile profile = new GameProfile(uuid, name);
            skinLookup = mc.getSkinManager().createLookup(profile, true);
        } catch (Throwable t) {
            skinLookup = DefaultPlayerSkin::getDefaultSkin;
        }
    }

    private void rebuildButtons() {
        buttons.clear();
        float dw = designW();
        float dh = designH();
        uiScale = computeUiScale(dw, dh);
        lastDw = dw;
        lastDh = dh;

        float s = uiScale;
        float buttonW = BASE_PRIMARY_W * s;
        float buttonH = BASE_BTN_H * s;
        float startY = (dh / 2f) - (55f * s);
        float staticButtonX = (dw / 2f) - (buttonW / 2f);

        String[] ids = {"single", "multi", "account", "background", "vexora", "options", "quit"};
        String[] labelsRu = {"Одиночная игра", "Сетевая игра", "Аккаунты", "Выбор фона", "Vexora", "Настройки", "Выйти"};
        String[] labelsEn = {"Singleplayer", "Multiplayer", "Alt Manager", "Background", "Vexora", "Options", "Quit"};
        String[] labels = isRussian ? labelsRu : labelsEn;

        for (int i = 0; i < ids.length; i++) {
            float bY = startY + (i * (buttonH + BASE_BTN_GAP * s));
            buttons.add(new MenuButton(ids[i], labels[i], staticButtonX, bY, buttonW, buttonH));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float dw = designW();
        float dh = designH();
        float fmx = mx(mouseX);
        float fmy = my(mouseY);

        if (buttons.isEmpty() || Math.abs(dw - lastDw) > 0.5f || Math.abs(dh - lastDh) > 0.5f) {
            rebuildButtons();
        }

        Render2D.beginFrame(g);

        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(16, 16, 16, 255));
        if (backgroundGif != null) {
            backgroundGif.renderCover(0, 0, dw, dh, ColorUtil.rgba(255, 255, 255, 255));
        }

        renderToffiLogo(dw, dh);
        renderToffiButtons(fmx, fmy);
        renderLanguageButton(dw, fmx, fmy);

        // РЕНДЕР ПАНЕЛИ VEXORA (С кнопками Макросы, Прокси, Версия)
        if (this.currentMenu == MenuState.VEXORA) {
            float panelW = 220f * uiScale;
            float panelX = dw - panelW;
            Render2D.rect(panelX, 0, panelW, dh, 0f, ColorUtil.rgba(20, 20, 20, 240));
            Render2D.outline(panelX, 0, panelW, dh, 0f, 1f, ColorUtil.rgba(45, 45, 45, 255));

            String vexoraTitle = "VEXORA PANEL";
            Render2D.text(FontType.SEMIBOLD, vexoraTitle, panelX + 15f * uiScale, 20f * uiScale, 12f * uiScale, -1);
            Render2D.rect(panelX + 15f * uiScale, 38f * uiScale, panelW - 30f * uiScale, 1f * uiScale, 0f, ColorUtil.rgba(60, 60, 60, 255));

            renderVexoraPanelButtons(panelX, panelW, dh, fmx, fmy);
        }
        // РЕНДЕР ПАНЕЛИ ВЫБОРА ФОНА
        if (this.currentMenu == MenuState.BACKGROUND) {
            float panelW = 220f * uiScale;
            float panelX = dw - panelW;

            Render2D.rect(panelX, 0, panelW, dh, 0f, ColorUtil.rgba(20, 20, 20, 240));
            Render2D.outline(panelX, 0, panelW, dh, 0f, 1f, ColorUtil.rgba(45, 45, 45, 255));

            String titleText = isRussian ? "ВЫБОР ФОНА" : "BACKGROUND";
            Render2D.text(FontType.SEMIBOLD, titleText, panelX + 15f * uiScale, 20f * uiScale, 12f * uiScale, -1);
            Render2D.rect(panelX + 15f * uiScale, 38f * uiScale, panelW - 30f * uiScale, 1f * uiScale, 0f, ColorUtil.rgba(60, 60, 60, 255));

            int scl = (int) mc.getWindow().getGuiScale();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int) (panelX * scl), (int) (45f * uiScale * scl), (int) (panelW * scl), (int) ((dh - 85f * uiScale) * scl));

            float itemY = 50f * uiScale + bgPanelScrollY;
            float itemH = 24f * uiScale;

            for (int i = 0; i < discoveredBackgrounds.size(); i++) {
                File bgFile = discoveredBackgrounds.get(i);
                boolean isCurrent = (i == selectedBackgroundIndex);
                boolean hItem = fmx >= panelX + 15f * uiScale && fmx <= dw - 15f * uiScale && fmy >= itemY && fmy <= itemY + itemH;

                int itemBg = isCurrent ? ColorUtil.rgba(35, 45, 75, 200) : (hItem ? ColorUtil.rgba(28, 28, 28, 200) : ColorUtil.rgba(16, 16, 16, 150));
                int itemBorder = isCurrent ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(45, 45, 45, 255);

                Render2D.rect(panelX + 15f * uiScale, itemY, panelW - 30f * uiScale, itemH, 3f * uiScale, itemBg);
                Render2D.outline(panelX + 15f * uiScale, itemY, panelW - 30f * uiScale, itemH, 3f * uiScale, 1f, itemBorder);

                String displayName = bgFile.getName();
                if (displayName.contains(".")) displayName = displayName.substring(0, displayName.lastIndexOf('.'));

                Render2D.text(FontType.SEMIBOLD, displayName, panelX + 22f * uiScale, itemY + (itemH - 7f * uiScale) * 0.48f, 7f * uiScale, isCurrent ? ColorUtil.rgba(110, 130, 255, 255) : -1);
                itemY += itemH + 5f * uiScale;
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            // Нижняя мультиязычная кнопка НАЗАД / BACK внутри панели фонов
            float btnH = 20f * uiScale;
            float btnY = dh - btnH - 15f * uiScale;
            boolean hBackBtn = fmx >= panelX + 15f * uiScale && fmx <= dw - 15f * uiScale && fmy >= btnY && fmy <= btnY + btnH;

            Render2D.rect(panelX + 15f * uiScale, btnY, panelW - 30f * uiScale, btnH, 3f * uiScale, hBackBtn ? ColorUtil.rgba(200, 60, 60, 255) : ColorUtil.rgba(25, 25, 25, 220));
            Render2D.outline(panelX + 15f * uiScale, btnY, panelW - 30f * uiScale, btnH, 3f * uiScale, 1f, hBackBtn ? ColorUtil.rgba(255, 50, 50, 255) : ColorUtil.rgba(50, 50, 50, 255));

            String backBtnText = isRussian ? "Назад" : "Back";
            float tw = Render2D.textWidth(FontType.SEMIBOLD, backBtnText, 7.5f * uiScale);
            Render2D.text(FontType.SEMIBOLD, backBtnText, panelX + 15f * uiScale + ((panelW - 30f * uiScale) - tw) / 2f, btnY + (btnH - 7.5f * uiScale) * 0.48f, 7.5f * uiScale, -1);
        }

        Render2D.flush();
    }
    private void renderVexoraPanelButtons(float panelX, float panelW, float dh, float fmx, float fmy) {
        float s = uiScale;
        float itemY = 50f * s;
        float itemH = 24f * s;

        String[] vexoraIds = {"macros", "proxy", "version"};
        String[] vexoraLabelsRu = {"Макросы", "Прокси", "Версия"};
        String[] vexoraLabelsEn = {"Macro", "Proxy", "Version"};
        String[] labels = isRussian ? vexoraLabelsRu : vexoraLabelsEn;

        for (int i = 0; i < vexoraIds.length; i++) {
            boolean hItem = fmx >= panelX + 15f * s && fmx <= panelX + panelW - 15f * s && fmy >= itemY && fmy <= itemY + itemH;
            int itemBg = hItem ? ColorUtil.rgba(30, 30, 30, 220) : ColorUtil.rgba(20, 20, 20, 180);
            int borderCol = hItem ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(50, 50, 50, 255);

            Render2D.rect(panelX + 15f * s, itemY, panelW - 30f * s, itemH, 3f * s, itemBg);
            Render2D.outline(panelX + 15f * s, itemY, panelW - 30f * s, itemH, 3f * s, 1f, borderCol);

            Render2D.text(FontType.SEMIBOLD, labels[i], panelX + 25f * s, itemY + (itemH - 7.5f * s) * 0.48f, 7.5f * s, hItem ? ColorUtil.rgba(110, 130, 255, 255) : -1);
            itemY += itemH + 6f * s;
        }

        // Нижняя мультиязычная кнопка НАЗАД / BACK внутри панели Vexora
        float btnH = 20f * s;
        float btnY = dh - btnH - 15f * s;
        boolean hBackBtn = fmx >= panelX + 15f * s && fmx <= panelX + panelW - 15f * s && fmy >= btnY && fmy <= btnY + btnH;

        Render2D.rect(panelX + 15f * s, btnY, panelW - 30f * s, btnH, 3f * s, hBackBtn ? ColorUtil.rgba(200, 60, 60, 255) : ColorUtil.rgba(25, 25, 25, 220));
        Render2D.outline(panelX + 15f * s, btnY, panelW - 30f * s, btnH, 3f * s, 1f, hBackBtn ? ColorUtil.rgba(255, 50, 50, 255) : ColorUtil.rgba(50, 50, 50, 255));

        String backBtnText = isRussian ? "Назад" : "Back";
        float tw = Render2D.textWidth(FontType.SEMIBOLD, backBtnText, 7.5f * s);
        Render2D.text(FontType.SEMIBOLD, backBtnText, panelX + 15f * s + ((panelW - 30f * s) - tw) / 2f, btnY + (btnH - 7.5f * s) * 0.48f, 7.5f * s, -1);
    }

    private void renderToffiLogo(float dw, float dh) {
        float s = uiScale;
        float logoSize = 100f * s;
        float logoX = (dw / 2f) - (logoSize / 2f);
        float logoY = (dh / 2f) - (175f * s);

        Identifier logoResource = Identifier.parse("minecraft:textures/gui/logo.png");
        Render2D.imageUvNearest(logoResource.toString(), logoX, logoY, logoSize, logoSize, 0f, 1f, 0, 0, 1, 1, ColorUtil.rgba(255, 255, 255, 255));
    }
    private void renderToffiButtons(float mx, float my) {
        float s = uiScale;
        int clientColor = ColorUtil.rgba(110, 130, 255, 255);

        for (MenuButton b : buttons) {
            boolean hovered = b.contains(mx, my);
            int outlineColor = hovered ? clientColor : ColorUtil.rgba(45, 45, 45, 255);
            int buttonBackgroundColor = hovered ? ColorUtil.rgba(30, 30, 30, 230) : ColorUtil.rgba(20, 20, 20, 200);
            int textColor = hovered ? clientColor : -1;

            Render2D.rect(b.x - 0.5f, b.y - 0.5f, b.w + 1.0f, b.h + 1.0f, BASE_BTN_R * s, outlineColor);
            Render2D.rect(b.x, b.y, b.w, b.h, BASE_BTN_R * s, buttonBackgroundColor);

            float textSize = 7.5f * s;
            float tw = Render2D.textWidth(FontType.SEMIBOLD, b.label, textSize);
            Render2D.text(FontType.SEMIBOLD, b.label, b.x + (b.w - tw) / 2f, b.y + (b.h - textSize) * 0.48f, textSize, textColor);
        }
    }

    private void renderLanguageButton(float dw, float mx, float my) {
        float s = uiScale;
        float langW = 80f * s;
        float langH = 28f * s;
        float langX = dw - langW - 15f * s;
        float langY = 15f * s;

        boolean hLang = mx >= langX && mx <= langX + langW && my >= langY && my <= langY + langH;
        int clientColor = ColorUtil.rgba(110, 130, 255, 255);
        int langBorder = hLang ? clientColor : ColorUtil.rgba(65, 65, 65, 255);

        Render2D.rect(langX - 0.5f, langY - 0.5f, langW + 1.0f, langH + 1.0f, 3f * s, langBorder);
        Render2D.rect(langX, langY, langW, langH, 3f * s, ColorUtil.rgba(20, 20, 20, 200));

        String langText = isRussian ? "ЯЗЫК RU" : "LANG EN";
        float textSize = 8.5f * s;
        float tw = Render2D.textWidth(FontType.SEMIBOLD, langText, textSize);
        Render2D.text(FontType.SEMIBOLD, langText, langX + (langW - tw) / 2f, langY + (langH - textSize) * 0.48f, textSize, hLang ? clientColor : -1);
    }

    private String playerName() {
        try {
            if (mc.getUser() != null && mc.getUser().getName() != null && !mc.getUser().getName().isBlank()) {
                return mc.getUser().getName();
            }
        } catch (Throwable ignored) {}
        return cachedPlayerName != null ? cachedPlayerName : "Player";
    }
    private void drawPlayerHead(float x, float y, float size) {
        float radius = Math.max(2.5f, size * 0.22f);
        try {
            if (mc.player != null) {
                String texture = mc.player.getSkin().body().texturePath().toString();
                drawSkinFace(texture, x, y, size, radius);
                return;
            }
            if (skinLookup != null) {
                PlayerSkin skin = skinLookup.get();
                if (skin != null && skin.body() != null) {
                    Identifier path = skin.body().texturePath();
                    if (path != null) {
                        drawSkinFace(path.toString(), x, y, size, radius);
                        return;
                    }
                }
            }
            try {
                java.util.UUID uuid = mc.getUser() != null ? mc.getUser().getProfileId() : null;
                PlayerSkin def = uuid != null ? DefaultPlayerSkin.get(uuid) : DefaultPlayerSkin.getDefaultSkin();
                if (def != null && def.body() != null) {
                    drawSkinFace(def.body().texturePath().toString(), x, y, size, radius);
                    return;
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        Render2D.rect(x, y, size, size, radius, ColorUtil.rgba(50, 54, 64, 230));
    }

    private void drawSkinFace(String texture, float x, float y, float size, float radius) {
        int col = ColorUtil.rgba(255, 255, 255, 255);
        Render2D.imageUvNearest(texture, x, y, size, size, radius, 0.8f, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, col);
        Render2D.imageUvNearest(texture, x, y, size, size, radius, 0.8f, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, col);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubled);
        }
        float dw = designW();
        float dh = designH();
        float s = uiScale;
        float mx = mx(event.x());
        float my = my(event.y());

        float langW = 80f * s;
        float langH = 28f * s;
        float langX = dw - langW - 15f * s;
        float langY = 15f * s;

        if (mx >= langX && mx <= langX + langW && my >= langY && my <= langY + langH) {
            isRussian = !isRussian;
            rebuildButtons();
            return true;
        }
        // КЛИКИ ВНУТРИ ПАНЕЛИ ВЫБОРА ФОНА
        if (this.currentMenu == MenuState.BACKGROUND) {
            float panelW = 220f * s;
            float panelX = dw - panelW;
            float btnH = 20f * s;
            float btnY = dh - btnH - 15f * s;

            if (mx >= panelX + 15f * s && mx <= dw - 15f * s && my >= btnY && my <= btnY + btnH) {
                this.currentMenu = MenuState.MAIN;
                return true;
            }

            float itemY = 50f * s + bgPanelScrollY;
            float itemH = 24f * s;
            for (int i = 0; i < discoveredBackgrounds.size(); i++) {
                if (mx >= panelX + 15f * s && mx <= dw - 15f * s && my >= itemY && my <= itemY + itemH) {
                    selectedBackgroundIndex = i;
                    // Сюда можно интегрировать применение выбранного файла discoveredBackgrounds.get(i)
                    return true;
                }
                itemY += itemH + 5f * s;
            }
            if (mx >= panelX) return true;
        }

        // КЛИКИ ВНУТРИ ПАНЕЛИ VEXORA
        if (this.currentMenu == MenuState.VEXORA) {
            float panelW = 220f * s;
            float panelX = dw - panelW;
            float btnH = 20f * s;
            float btnY = dh - btnH - 15f * s;

            if (mx >= panelX + 15f * s && mx <= dw - 15f * s && my >= btnY && my <= btnY + btnH) {
                this.currentMenu = MenuState.MAIN;
                return true;
            }

            float itemY = 50f * s;
            float itemH = 24f * s;
            if (mx >= panelX + 15f * s && mx <= panelX + panelW - 15f * s) {
                if (my >= itemY && my <= itemY + itemH) { /* Действие для Макросы */ return true; }
                if (my >= itemY + 30f * s && my <= itemY + 30f * s + itemH) { /* Действие для Прокси */ return true; }
                if (my >= itemY + 60f * s && my <= itemY + 60f * s + itemH) { /* Действие для Версия */ return true; }
            }
            if (mx >= panelX) return true;
        }

        for (MenuButton b : buttons) {
            if (b.contains(mx, my)) {
                onButton(b.id);
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double longitude) {
        if (this.currentMenu == MenuState.BACKGROUND) {
            float dw = designW();
            float panelW = 220f * uiScale;
            float mx = mx(mouseX);
            if (mx >= dw - panelW) {
                bgPanelScrollY += longitude * 15f * uiScale;
                if (bgPanelScrollY > 0) bgPanelScrollY = 0;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, longitude);
    }

    private void onButton(String id) {
        switch (id) {
            case "single" -> mc.setScreen(new SelectWorldScreen(this));
            case "multi" -> mc.setScreen(new polaris.screens.mainmenu.CustomMultiplayerScreen(this));
            case "options" -> mc.setScreen(new OptionsScreen(this, mc.options));
            case "quit" -> mc.stop();
            case "account" -> mc.setScreen(new AltManagerScreen(this));
            case "background" -> {
                scanBackgroundsFolder();
                this.currentMenu = (this.currentMenu == MenuState.BACKGROUND) ? MenuState.MAIN : MenuState.BACKGROUND;
            }
            case "vexora" -> {
                this.currentMenu = (this.currentMenu == MenuState.VEXORA) ? MenuState.MAIN : MenuState.VEXORA;
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private static final class MenuButton {
        final String id;
        final String label;
        final float x, y, w, h;

        MenuButton(String id, String label, float x, float y, float w, float h) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
