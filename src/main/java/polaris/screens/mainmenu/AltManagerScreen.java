package polaris.screens.mainmenu;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import polaris.utils.render.ui.gif.GifRenderer;
import polaris.utils.render.ui.gif.MainMenuGifPreloader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

public final class AltManagerScreen extends Screen {
    private static final float REF_W = 960f;
    private static final float REF_H = 540f;

    public static class AltAccount {
        public String name;
        public String type;
        public String uuid;
        public boolean isFavorite = false;

        public AltAccount(String name, String type, String uuid) {
            this.name = name;
            this.type = type;
            this.uuid = uuid;
        }
    }

    public static final List<AltAccount> registry = new ArrayList<>();
    private static com.sun.net.httpserver.HttpServer localServer = null;

    public String searchQuery = "";
    public boolean isSearchFocused = false;
    public float scrollY = 0;
    public float targetScrollY = 0;

    public boolean isMicrosoftAuthLoading = false;
    public String authStatusMessage = "";

    public boolean premiumOnly = false;
    public boolean favoritesOnly = false;
    public int accountTypeIndex = 0;

    public final String[] accountTypeModes = {"Все", "Пиратский", "Microsoft"};
    public boolean isTypeMenuOpen = false;

    public String modalMode = "NONE";
    public String inputNick = "";
    public boolean isInputNickFocused = false;
    public int modalTypeIndex = 0;

    private final Screen parent;
    private AltAccount selectedAccount = null;
    private float uiScale = 1f;
    private float lastDw = -1f;
    private float lastDh = -1f;

    private GifRenderer backgroundGif;


    private Supplier<PlayerSkin> skinLookup;
    private String cachedPlayerName = "Player";

    static {
        if (registry.isEmpty()) {
            registry.add(new AltAccount("xxx_max_xxx", "Пиратский", "6ca3a882-d8a8-3b9f-8819-81babd36da22"));
            registry.add(new AltAccount("YoGoQmWb", "Microsoft", "b3835d18-81cb-3193-bbc0-59c0a4c83bb3"));
        }
    }

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.isSearchFocused = false;
        this.isInputNickFocused = false;
        this.isTypeMenuOpen = false;
        this.modalMode = "NONE";
        this.inputNick = "";

        // Принудительно подгружаем инстанс гифки для текущего экрана
        this.backgroundGif = MainMenuGifPreloader.background();
        if (this.backgroundGif != null && !this.backgroundGif.isReady() && !this.backgroundGif.isFailed()) {
            this.backgroundGif.ensureLoaded();
        }

        loadAlts();
    }


    private float designW() { return Render2D.getFixedScaledWidth(); }
    private float designH() { return Render2D.getFixedScaledHeight(); }
    private float mx(double guiX) { return (float) Render2D.guiToFixed(guiX); }
    private float my(double guiY) { return (float) Render2D.guiToFixed(guiY); }

    private float computeUiScale(float dw, float dh) {
        float s = Math.min(dw / REF_W, dh / REF_H);
        return Math.max(0.70f, Math.min(s, 1.20f));
    }

    private String generateFormattedRandomNick() {
        String[] words = {"Sky", "Fire", "Ice", "Dark", "Light", "Ghost", "Storm", "Shadow", "Frost", "Wolf", "Knight", "Vex", "Blade", "Void", "Flame", "Ash", "Rune", "Mist", "Crown", "Fang", "Soul", "Echo"};
        Random rnd = new Random();
        return words[rnd.nextInt(words.length)] + "_" + (1000 + rnd.nextInt(9000));
    }
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        float dw = designW();
        float dh = designH();
        uiScale = computeUiScale(dw, dh);
        lastDw = dw;
        lastDh = dh;

        float s = uiScale;
        float fmx = mx(mouseX);
        float fmy = my(mouseY);

        scrollY = Math.max(Math.min(0, (dh - 190f * s) - (registry.size() * 52f * s)), targetScrollY);
        scrollY = scrollY + (targetScrollY - scrollY) * 0.15f;

        Render2D.beginFrame(g);

        // Сначала заливаем экран базовым темным цветом подложки
        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.rgba(16, 16, 16, 255));

        // Внедряем динамическую фоновую гифку Polaris лаунчера
        if (backgroundGif != null) {
            backgroundGif.renderCover(0, 0, dw, dh, ColorUtil.rgba(255, 255, 255, 255));
        }

        Render2D.text(FontType.SEMIBOLD, "VEXORA", 40f * s, 25f * s, 14f * s, ColorUtil.rgba(110, 130, 255, 255));
        Render2D.text(FontType.REGULARNEW, "ALT MANAGER", 115f * s, 29f * s, 8.5f * s, ColorUtil.rgba(140, 140, 140, 255));

        float profX = dw - 240f * s, profY = 20f * s, profW = 200f * s, profH = 35f * s;
        Render2D.rect(profX, profY, profW, profH, 5f * s, ColorUtil.rgba(20, 25, 40, 200));

        String currentUsername = minecraft.getUser() != null ? minecraft.getUser().getName() : "Player";
        // Отрисовка головы вашего текущего аккаунта
        drawPlayerHead(profX + 6f * s, profY + 6f * s, 23f * s, currentUsername);
        Render2D.text(FontType.SEMIBOLD, currentUsername, profX + 35f * s, profY + 10f * s, 7.5f * s, -1);

        boolean isMicrosoft = minecraft.getUser() != null && minecraft.getUser().getAccessToken() != null && minecraft.getUser().getAccessToken().length() > 4;
        String currentSessionType = isMicrosoft ? (MainMenuScreen.isRussian ? "Microsoft" : "Microsoft") : (MainMenuScreen.isRussian ? "Пиратский" : "Cracked");
        Render2D.text(FontType.REGULARNEW, currentSessionType, profX + 35f * s, profY + 22f * s, 6.5f * s, isMicrosoft ? ColorUtil.rgba(100, 255, 100, 255) : ColorUtil.rgba(160, 160, 160, 255));

        float barX = 40f * s, barY = 75f * s, barW = dw - 80f * s, barH = 30f * s;
        Render2D.rect(barX, barY, barW, barH, 5f * s, ColorUtil.rgba(15, 20, 32, 255));

        float premX = barX + 15f * s;
        Render2D.rect(premX, barY + 9f * s, 12f * s, 12f * s, 2f * s, premiumOnly ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(30, 40, 60, 255));
        if (premiumOnly) Render2D.text(FontType.SEMIBOLD, "✔", premX + 2f * s, barY + 11f * s, 7f * s, -1);
        Render2D.text(FontType.SEMIBOLD, "Premium Only", premX + 18f * s, barY + 11f * s, 7f * s, premiumOnly ? ColorUtil.rgba(110, 130, 255, 255) : -1);

        float favX = premX + 105f * s;
        Render2D.rect(favX, barY + 9f * s, 12f * s, 12f * s, 2f * s, favoritesOnly ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(30, 40, 60, 255));
        if (favoritesOnly) Render2D.text(FontType.SEMIBOLD, "✔", favX + 2f * s, barY + 11f * s, 7f * s, -1);
        String favFilterText = MainMenuScreen.isRussian ? "Только избранные" : "Favorites Only";
        Render2D.text(FontType.SEMIBOLD, favFilterText, favX + 18f * s, barY + 11f * s, 7f * s, favoritesOnly ? ColorUtil.rgba(110, 130, 255, 255) : -1);

        float typX = favX + 135f * s, typW = 85f * s, typH = 18f * s;
        Render2D.rect(typX, barY + 6f * s, typW, typH, 3f * s, ColorUtil.rgba(25, 35, 55, 255));
        String[] localTypes = MainMenuScreen.isRussian ? new String[]{"Все", "Пиратский", "Microsoft"} : new String[]{"All", "Cracked", "Microsoft"};
        Render2D.text(FontType.SEMIBOLD, localTypes[accountTypeIndex], typX + 8f * s, barY + 11f * s, 6.5f * s, -1);
        Render2D.text(FontType.SEMIBOLD, isTypeMenuOpen ? "▲" : "▼", typX + typW - 14f * s, barY + 11f * s, 6.5f * s, ColorUtil.rgba(150, 150, 150, 255));

        float srcW = 160f * s, srcH = 18f * s, srcX = barX + barW - srcW - 15f * s;
        int srcBg = isSearchFocused ? ColorUtil.rgba(24, 32, 50, 255) : ColorUtil.rgba(10, 14, 23, 255);
        Render2D.rect(srcX, barY + 6f * s, srcW, srcH, 4f * s, srcBg);
        String searchPlaceholder = MainMenuScreen.isRussian ? "Поиск..." : "Search...";
        String dispTxt = searchQuery.isEmpty() ? searchPlaceholder : searchQuery;
        Render2D.text(FontType.REGULARNEW, dispTxt, srcX + 8f * s, barY + 11f * s, 7f * s, searchQuery.isEmpty() ? ColorUtil.rgba(120, 120, 120, 255) : -1);

        float listX = 40f * s, listY = 120f * s, listW = dw - 80f * s, listH = dh - 190f * s;
        int scl = (int) this.minecraft.getWindow().getGuiScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (listX * scl), (int) ((dh - (listY + listH)) * scl), (int) (listW * scl), (int) (listH * scl));
        drawAccountsList(fmx, fmy, listX, listY, listW, listH);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        drawBottomBar(fmx, fmy, dw, dh);

        if (isTypeMenuOpen) {
            float dropY = barY + 24f * s;
            Render2D.rect(typX, dropY, typW, localTypes.length * 14f * s + 4f * s, 3f * s, ColorUtil.rgba(20, 26, 40, 255));
            for (int i = 0; i < localTypes.length; i++) {
                float itemY = dropY + 2f * s + (i * 14f * s);
                if (fmx >= typX && fmx <= typX + typW && fmy >= itemY && fmy <= itemY + 14f * s)
                    Render2D.rect(typX + 2f * s, itemY, typW - 4f * s, 14f * s, 0f, ColorUtil.rgba(35, 45, 70, 255));
                Render2D.text(FontType.SEMIBOLD, localTypes[i], typX + 6f * s, itemY + 3f * s, 6.5f * s, i == accountTypeIndex ? ColorUtil.rgba(110, 130, 255, 255) : -1);
            }
        }
        if (!modalMode.equalsIgnoreCase("NONE")) drawModalWindow(fmx, fmy, dw, dh);

        Render2D.flush();
    }


    private void drawAccountsList(float mx, float my, float lX, float lY, float lW, float lH) {
        float s = uiScale;
        float currentY = lY + scrollY;
        float itemH = 46f * s, itemGap = 6f * s;

        for (AltAccount acc : registry) {
            if (!searchQuery.isEmpty() && !acc.name.toLowerCase().contains(searchQuery.toLowerCase())) continue;
            if (accountTypeIndex == 1 && !acc.type.equals("Пиратский")) continue;
            if (accountTypeIndex == 2 && !acc.type.equals("Microsoft")) continue;
            if (favoritesOnly && !acc.isFavorite) continue;
            if (premiumOnly && !acc.type.equals("Microsoft")) continue;

            float itemY = currentY;
            boolean hvr = mx >= lX && mx <= lX + lW && my >= itemY && my <= itemY + itemH && my >= lY && my <= lY + lH;
            boolean isSelected = (selectedAccount == acc);

            int bgCol = isSelected ? ColorUtil.rgba(35, 45, 75, 200) : (hvr ? ColorUtil.rgba(24, 32, 50, 180) : ColorUtil.rgba(16, 22, 36, 140));
            Render2D.rect(lX, itemY, lW, itemH, 4f * s, bgCol);

            drawPlayerHead(lX + 12f * s, itemY + 9f * s, 28f * s, acc.name);

            Render2D.text(FontType.SEMIBOLD, acc.name, lX + 50f * s, itemY + 12f * s, 8.5f * s, -1);

            String rowTypeStr = acc.type.equals("Microsoft") ? "Microsoft" : (MainMenuScreen.isRussian ? "Пиратский" : "Cracked");
            float nameW = Render2D.textWidth(FontType.SEMIBOLD, acc.name, 8.5f * s);
            Render2D.text(FontType.REGULARNEW, rowTypeStr, lX + 50f * s + nameW + 8f * s, itemY + 14f * s, 6.5f * s, acc.type.equals("Microsoft") ? ColorUtil.rgba(100, 255, 100, 255) : ColorUtil.rgba(160, 160, 160, 255));
            Render2D.text(FontType.REGULARNEW, acc.uuid, lX + 50f * s, itemY + 28f * s, 6.5f * s, ColorUtil.rgba(120, 120, 120, 255));

            float delX = lX + lW - 55f * s, delY = itemY + 18f * s;
            boolean hDel = mx >= delX && mx <= delX + 45f * s && my >= delY - 4f * s && my <= delY + 10f * s && my >= lY && my <= lY + lH;
            String delRowText = MainMenuScreen.isRussian ? "Удалить" : "Delete";
            Render2D.text(FontType.SEMIBOLD, delRowText, delX, delY, 7f * s, hDel ? ColorUtil.rgba(255, 50, 50, 255) : ColorUtil.rgba(150, 150, 150, 255));

            String favText = acc.isFavorite ? (MainMenuScreen.isRussian ? "В избранном" : "Favorite") : (MainMenuScreen.isRussian ? "В избранное" : "To Favorite");
            float favW = Render2D.textWidth(FontType.SEMIBOLD, favText, 7f * s);
            float accountFavX = delX - favW - 15f * s;
            boolean hFav = mx >= accountFavX && mx <= accountFavX + favW && my >= delY - 4f * s && my <= delY + 10f * s && my >= lY && my <= lY + lH;
            Render2D.text(FontType.SEMIBOLD, favText, accountFavX, delY, 7f * s, acc.isFavorite ? ColorUtil.rgba(255, 215, 0, 255) : (hFav ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(150, 150, 150, 255)));

            currentY += itemH + itemGap;
        }
    }

    private void drawBottomBar(float mx, float my, float wW, float wH) {
        float s = uiScale;
        float barY = wH - 50f * s;

        float addX = 40f * s;
        boolean hAdd = mx >= addX && mx <= addX + 65f * s && my >= barY + 10f * s && my <= barY + 32f * s;
        Render2D.rect(addX, barY + 10f * s, 65f * s, 22f * s, 3f * s, hAdd ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(16, 22, 36, 255));
        String bottomAddText = MainMenuScreen.isRussian ? "➕ Добавить" : "➕ Add";
        Render2D.text(FontType.SEMIBOLD, bottomAddText, addX + 6f * s, barY + 16f * s, 7f * s, -1);

        float dirX = 115f * s;
        boolean hDir = mx >= dirX && mx <= dirX + 70f * s && my >= barY + 10f * s && my <= barY + 32f * s;
        Render2D.rect(dirX, barY + 10f * s, 70f * s, 22f * s, 3f * s, hDir ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(16, 22, 36, 255));
        String bottomDirText = MainMenuScreen.isRussian ? "📝 Прямой" : "📝 Direct";
        Render2D.text(FontType.SEMIBOLD, bottomDirText, dirX + 6f * s, barY + 16f * s, 7f * s, -1);

        float rndX = 195f * s;
        boolean hRnd = mx >= rndX && mx <= rndX + 75f * s && my >= barY + 10f * s && my <= barY + 32f * s;
        Render2D.rect(rndX, barY + 10f * s, 75f * s, 22f * s, 3f * s, hRnd ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(16, 22, 36, 255));
        String bottomRndText = MainMenuScreen.isRussian ? "🎲 Рандом" : "🎲 Random";
        Render2D.text(FontType.SEMIBOLD, bottomRndText, rndX + 6f * s, barY + 16f * s, 7f * s, -1);
        float rstX = 280f * s;
        boolean hRst = mx >= rstX && mx <= rstX + 75f * s && my >= barY + 10f * s && my <= barY + 32f * s;
        Render2D.rect(rstX, barY + 10f * s, 75f * s, 22f * s, 3f * s, hRst ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(16, 22, 36, 255));
        String bottomRstText = MainMenuScreen.isRussian ? "🔄 Сброс" : "🔄 Restore";
        Render2D.text(FontType.SEMIBOLD, bottomRstText, rstX + 6f * s, barY + 16f * s, 7f * s, -1);

        float bckX = wW - 100f * s;
        boolean hBck = mx >= bckX && mx <= bckX + 60f * s && my >= barY + 10f * s && my <= barY + 32f * s;
        Render2D.rect(bckX, barY + 10f * s, 60f * s, 22f * s, 3f * s, hBck ? ColorUtil.rgba(200, 60, 60, 255) : ColorUtil.rgba(16, 22, 36, 255));
        String bottomBackText = MainMenuScreen.isRussian ? "🚪 Назад" : "🚪 Back";
        Render2D.text(FontType.SEMIBOLD, bottomBackText, bckX + 8f * s, barY + 16f * s, 7f * s, -1);
    }

    private void drawModalWindow(float mx, float my, float wW, float wH) {
        float s = uiScale;
        Render2D.rect(0, 0, wW, wH, 0f, ColorUtil.rgba(0, 0, 0, 180));

        float mW = 280f * s, mH = 160f * s;
        float mX = (wW / 2f) - (mW / 2f), mY = (wH / 2f) - (mH / 2f);

        Render2D.rect(mX - 1f * s, mY - 1f * s, mW + 2f * s, mH + 2f * s, 6f * s, ColorUtil.rgba(110, 130, 255, 255));
        Render2D.rect(mX, mY, mW, mH, 6f * s, ColorUtil.rgba(12, 16, 26, 255));

        boolean isDirect = modalMode.equalsIgnoreCase("DIRECT");
        String modalTitle = isDirect ? (MainMenuScreen.isRussian ? "Прямой вход" : "Direct Login") : (MainMenuScreen.isRussian ? "Добавить аккаунт" : "Add Account");
        float titleW = Render2D.textWidth(FontType.SEMIBOLD, modalTitle, 9.5f * s);
        Render2D.text(FontType.SEMIBOLD, modalTitle, mX + (mW - titleW) / 2f, mY + 12f * s, 9.5f * s, -1);

        Render2D.rect(mX + 40f * s, mY + 26f * s, mW - 80f * s, 2f * s, 1f * s, ColorUtil.rgba(110, 130, 255, 255));

        String[] types = isDirect ? (MainMenuScreen.isRussian ? new String[]{"Пиратский"} : new String[]{"Cracked"}) : (MainMenuScreen.isRussian ? new String[]{"Майкрософт", "TheAltening", "Пиратский", "В разработке"} : new String[]{"Premium", "TheAltening", "Cracked", "Session"});
        float tabW = 55f * s, tabH = 30f * s;
        float startTabsX = mX + (mW / 2f) - ((types.length * (tabW + 4f * s)) / 2f);

        for (int i = 0; i < types.length; i++) {
            float tX = startTabsX + (i * (tabW + 4f * s)), tY = mY + 38f * s;
            boolean hTab = mx >= tX && mx <= tX + tabW && my >= tY && my <= tY + tabH;
            boolean isSelTab = isDirect || (modalTypeIndex == i);
            int tabBg = isSelTab ? ColorUtil.rgba(24, 34, 55, 255) : (hTab ? ColorUtil.rgba(20, 26, 40, 255) : ColorUtil.rgba(14, 18, 28, 255));
            Render2D.rect(tX, tY, tabW, tabH, 3f * s, tabBg);
            if (isSelTab) {
                Render2D.rect(tX - 0.5f, tY - 0.5f, tabW + 1.0f, tabH + 1.0f, 3f * s, ColorUtil.rgba(110, 130, 255, 255));
                Render2D.rect(tX, tY, tabW, tabH, 3f * s, tabBg);
            }
            float tw = Render2D.textWidth(FontType.SEMIBOLD, types[i], 6f * s);
            Render2D.text(FontType.SEMIBOLD, types[i], tX + (tabW - tw) / 2f, tY + (tabH - 6f * s) * 0.48f, 6f * s, isSelTab ? ColorUtil.rgba(110, 130, 255, 255) : -1);
        }

        if (isDirect || modalTypeIndex == 2) {
            float inpX = mX + 20f * s, inpY = mY + 76f * s, inpW = mW - 40f * s, inpH = 16f * s;
            int inpBg = isInputNickFocused ? ColorUtil.rgba(24, 34, 55, 255) : ColorUtil.rgba(10, 14, 23, 255);
            Render2D.rect(inpX, inpY, inpW, inpH, 4f * s, inpBg);

            String inputPlaceholder = MainMenuScreen.isRussian ? "Введите ник..." : "Type nickname...";
            String txt = inputNick.isEmpty() ? inputPlaceholder : inputNick;
            Render2D.text(FontType.REGULARNEW, txt, inpX + 8f * s, inpY + (inpH - 7f * s) * 0.48f, 7f * s, inputNick.isEmpty() ? ColorUtil.rgba(120, 120, 120, 255) : -1);

            float btnW = 55f * s, btnH = 16f * s;
            float btnSaveX = mX + 45f * s, btnCancelX = mX + mW - 45f * s - btnW;
            float crackedBtnsY = inpY + inpH + 12f * s;

            boolean hSave = mx >= btnSaveX && mx <= btnSaveX + btnW && my >= crackedBtnsY && my <= crackedBtnsY + btnH;
            Render2D.rect(btnSaveX, crackedBtnsY, btnW, btnH, 3f * s, hSave ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(24, 34, 55, 255));
            String saveText = isDirect ? (MainMenuScreen.isRussian ? "Войти" : "Login") : (MainMenuScreen.isRussian ? "Сохранить" : "Save");
            float stW = Render2D.textWidth(FontType.SEMIBOLD, saveText, 6.5f * s);
            Render2D.text(FontType.SEMIBOLD, saveText, btnSaveX + (btnW - stW) / 2f, crackedBtnsY + (btnH - 6.5f * s) * 0.48f, 6.5f * s, -1);

            boolean hCancel = mx >= btnCancelX && mx <= btnCancelX + btnW && my >= crackedBtnsY && my <= crackedBtnsY + btnH;
            Render2D.rect(btnCancelX, crackedBtnsY, btnW, btnH, 3f * s, hCancel ? ColorUtil.rgba(180, 50, 50, 255) : ColorUtil.rgba(30, 20, 30, 255));
            String cancelText = MainMenuScreen.isRussian ? "Отмена" : "Cancel";
            float ctW = Render2D.textWidth(FontType.SEMIBOLD, cancelText, 6.5f * s);
            Render2D.text(FontType.SEMIBOLD, cancelText, btnCancelX + (btnW - ctW) / 2f, crackedBtnsY + (btnH - 6.5f * s) * 0.48f, 6.5f * s, -1);
        } else if (modalTypeIndex == 0) {
            float btnX = mX + 20f * s, btnY = mY + 84f * s, btnW = mW - 40f * s, btnH = 22f * s;
            boolean hLink = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH;
            Render2D.rect(btnX, btnY, btnW, btnH, 4f * s, hLink ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(24, 34, 55, 255));
            String linkDefaultText = MainMenuScreen.isRussian ? "Авторизоваться" : "Link Account";
            String btnText = isMicrosoftAuthLoading ? authStatusMessage : linkDefaultText;
            float btW = Render2D.textWidth(FontType.SEMIBOLD, btnText, 8f * s);
            Render2D.text(FontType.SEMIBOLD, btnText, btnX + (btnW - btW) / 2f, btnY + (btnH - 8f * s) * 0.48f, 8f * s, -1);

            float cpY = mY + 114f * s;
            boolean hCp = mx >= mX + (mW / 2f) - 35f * s && mx <= mX + (mW / 2f) + 35f * s && my >= cpY && my <= cpY + 12f * s;
            float cpW = Render2D.textWidth(FontType.SEMIBOLD, "Copy URL", 6.5f * s);
            Render2D.text(FontType.SEMIBOLD, "Copy URL", mX + (mW - cpW) / 2f, cpY + 2f * s, 6.5f * s, hCp ? ColorUtil.rgba(110, 130, 255, 255) : ColorUtil.rgba(140, 140, 140, 255));
        } else {
            String devNotice = MainMenuScreen.isRussian ? "Раздел в разработке..." : "In Development...";
            float dnW = Render2D.textWidth(FontType.SEMIBOLD, devNotice, 7.5f * s);
            Render2D.text(FontType.SEMIBOLD, devNotice, mX + (mW - dnW) / 2f, mY + 95f * s, 7.5f * s, ColorUtil.rgba(140, 140, 140, 255));
        }

        String exitText = MainMenuScreen.isRussian ? "Выход" : "Exit";
        float exitW = Render2D.textWidth(FontType.SEMIBOLD, exitText, 6.5f * s);
        float clsX = mX + mW - exitW - 12f * s; float clsY = mY + 8f * s;
        boolean hCls = mx >= clsX - 2f * s && mx <= clsX + exitW + 2f * s && my >= clsY - 2f * s && my <= clsY + 10f * s;
        Render2D.text(FontType.SEMIBOLD, exitText, clsX, clsY, 6.5f * s, hCls ? ColorUtil.rgba(255, 75, 75, 255) : ColorUtil.rgba(140, 140, 140, 255));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT && event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return super.mouseClicked(event, doubled);
        }

        float dw = designW();
        float dh = designH();
        float s = uiScale;
        float mx = mx(event.x());
        float my = my(event.y());
        int button = event.button();

        float barX = 40f * s, barW = dw - 80f * s, barY = dh - 50f * s;
        float premX = barX + 15f * s, favX = premX + 105f * s, typX = favX + 115f * s, typW = 85f * s, srcW = 160f * s, srcX = barX + barW - srcW - 15f * s;

        if (isTypeMenuOpen) {
            float dropY = 75f * s + 24f * s;
            if (mx >= typX && mx <= typX + typW && my >= dropY && my <= dropY + 3 * 14f * s + 4f * s) {
                for (int i = 0; i < 3; i++) {
                    if (mx >= typX && mx <= typX + typW && my >= dropY + 2f * s + (i * 14f * s) && my <= dropY + 2f * s + (i * 14f * s) + 14f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        accountTypeIndex = i; isTypeMenuOpen = false; return true;
                    }
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { isTypeMenuOpen = false; }
        }

        if (!modalMode.equalsIgnoreCase("NONE")) {
            float mW = 280f * s, mH = 160f * s; float mX = (dw / 2f) - (mW / 2f), mY = (dh / 2f) - (mH / 2f);
            String localExitStr = MainMenuScreen.isRussian ? "Выход" : "Exit";
            float txtExitW = Render2D.textWidth(FontType.SEMIBOLD, localExitStr, 6.5f * s);

            if (mx >= mX + mW - txtExitW - 14f * s && mx <= mX + mW - 10f * s && my >= mY + 6f * s && my <= mY + 18f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                stopLocalHttpServer(); modalMode = "NONE"; return true;
            }
            if (!modalMode.equalsIgnoreCase("DIRECT")) {
                float tabW = 55f * s;
                float startTabsX = mX + (mW / 2f) - ((4 * (tabW + 4f * s)) / 2f);
                for (int i = 0; i < 4; i++) {
                    if (mx >= startTabsX + (i * (tabW + 4f * s)) && mx <= startTabsX + (i * (tabW + 4f * s)) + tabW && my >= mY + 38f * s && my <= mY + 68f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        modalTypeIndex = i; inputNick = ""; isInputNickFocused = false; stopLocalHttpServer(); return true;
                    }
                }
            }

            if (modalTypeIndex == 0) {
                if (mx >= mX + 20f * s && mx <= mX + mW - 20f * s && my >= mY + 84f * s && my <= mY + 106f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !isMicrosoftAuthLoading) { startLocalHttpServer(); return true; }
                if (mx >= mX + (mW / 2f) - 35f * s && mx <= mX + (mW / 2f) + 35f * s && my >= mY + 114f * s && my <= mY + 126f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { minecraft.keyboardHandler.setClipboard(MicrosoftAuth.AUTH_URL); authStatusMessage = MainMenuScreen.isRussian ? "Скопировано!" : "Copied URL!"; return true; }
            } else if (modalTypeIndex == 2) {
                if (mx >= mX + 20f * s && mx <= mX + mW - 20f * s && my >= mY + 76f * s && my <= mY + 92f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { isInputNickFocused = true; return true; }
                if (mx >= mX + 45f * s && mx <= mX + 45f * s + 55f * s && my >= mY + 104f * s && my <= mY + 120f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (!inputNick.trim().isEmpty()) {
                        String cleanNick = inputNick.trim();
                        String generatedUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanNick).getBytes(StandardCharsets.UTF_8)).toString();
                        if (modalMode.equalsIgnoreCase("DIRECT")) {
                            setSessionReflect(cleanNick, generatedUuid);
                        } else {
                            registry.add(new AltAccount(cleanNick, "Пиратский", generatedUuid));
                            saveAlts();
                        }
                        modalMode = "NONE";
                    }
                    return true;
                }
                if (mx >= mX + mW - 45f * s - 55f * s && mx <= mX + mW - 45f * s && my >= mY + 104f * s && my <= mY + 120f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { inputNick = ""; isInputNickFocused = false; modalMode = "NONE"; return true; }
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) isInputNickFocused = false;
            }
            return true;
        }

        if (mx >= dw - 100f * s && mx <= dw - 40f * s && my >= barY + 10f * s && my <= barY + 32f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { this.minecraft.setScreen(parent); return true; }
        if (mx >= 40f * s && mx <= 105f * s && my >= barY + 10f * s && my <= barY + 32f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { modalMode = "ADD"; inputNick = ""; isInputNickFocused = true; return true; }
        if (mx >= 115f * s && mx <= 185f * s && my >= barY + 10f * s && my <= barY + 32f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { modalMode = "DIRECT"; modalTypeIndex = 2; inputNick = ""; isInputNickFocused = true; return true; }
        if (mx >= 195f * s && mx <= 270f * s && my >= barY + 10f * s && my <= barY + 32f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { String rNick = generateFormattedRandomNick(); setSessionReflect(rNick, UUID.nameUUIDFromBytes(("OfflinePlayer:" + rNick).getBytes(StandardCharsets.UTF_8)).toString()); return true; }
        if (mx >= 280f * s && mx <= 355f * s && my >= barY + 10f * s && my <= barY + 32f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { setSessionReflect(System.getProperty("user.name"), ""); return true; }

        if (mx >= premX && mx <= premX + 80f * s && my >= 84f * s && my <= 96f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { premiumOnly = !premiumOnly; return true; }
        if (mx >= favX && mx <= favX + 90f * s && my >= 84f * s && my <= 96f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { favoritesOnly = !favoritesOnly; return true; }
        if (mx >= typX && mx <= typX + typW && my >= 81f * s && my <= 99f * s) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) isTypeMenuOpen = !isTypeMenuOpen; else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) accountTypeIndex = (accountTypeIndex + 1) % accountTypeModes.length; return true;
        }
        if (mx >= srcX && mx <= srcX + srcW && my >= 81f * s && my <= 99f * s && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { isSearchFocused = true; return true; } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) isSearchFocused = false;

        float listX = 40f * s, listY = 120f * s, listW = dw - 80f * s, listH = dh - 190f * s;
        float curY = listY + scrollY; AltAccount toRemove = null;
        for (AltAccount acc : registry) {
            if (!searchQuery.isEmpty() && !acc.name.toLowerCase().contains(searchQuery.toLowerCase())) continue;
            if (accountTypeIndex == 1 && !acc.type.equals("Пиратский")) continue;
            if (accountTypeIndex == 2 && !acc.type.equals("Microsoft")) continue;
            if (favoritesOnly && !acc.isFavorite) continue; if (premiumOnly && !acc.type.equals("Microsoft")) continue;

            float delX = listX + listW - 55f * s, delY = curY + 18f * s;
            String fTxt = acc.isFavorite ? (MainMenuScreen.isRussian ? "В избранном" : "Favorite") : (MainMenuScreen.isRussian ? "В избранное" : "To Favorite");
            float favW = Render2D.textWidth(FontType.SEMIBOLD, fTxt, 7f * s);
            float accountFavX = delX - favW - 15f * s;

            if (mx >= accountFavX && mx <= accountFavX + favW && my >= delY - 4f * s && my <= delY + 10f * s && my >= listY && my <= listY + listH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                acc.isFavorite = !acc.isFavorite; saveAlts(); return true;
            }
            if (mx >= delX && mx <= delX + 45f * s && my >= delY - 4f * s && my <= delY + 10f * s && my >= listY && my <= listY + listH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { toRemove = acc; break; }
            if (mx >= listX && mx <= listX + listW - 130f * s && my >= curY && my <= curY + 46f * s && my >= listY && my <= listY + listH && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selectedAccount = acc; setSessionReflect(acc.name, acc.uuid); return true;
            }
            curY += 52f * s;
        }
        if (toRemove != null) { registry.remove(toRemove); saveAlts(); return true; }
        return super.mouseClicked(event, doubled);
    }

    public void startLocalHttpServer() {
        if (localServer != null) return;
        isMicrosoftAuthLoading = true;
        authStatusMessage = MainMenuScreen.isRussian ? "Ожидание..." : "Waiting...";
        try {
            localServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(56964), 0);
            localServer.createContext("/login", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String resp = "<html><body style='background:#0a0e17;color:#ff5555;text-align:center;padding-top:50px;'><h1>Error! No code found.</h1></body></html>";

                if (query != null && query.contains("code=")) {
                    String[] parts = query.split("code=");
                    String rawCode = parts[1];
                    String code = rawCode.contains("&") ? rawCode.split("&")[0] : rawCode;
                    resp = "<html><body style='background:#0a0e17;color:#55ff55;text-align:center;padding-top:50px;'><h1>Success! You can close this tab.</h1></body></html>";

                    MicrosoftAuth.loginWithCode(code, profile -> {
                        if (profile != null) {
                            setSessionReflect(profile.name(), profile.id().toString());
                            registry.add(new AltAccount(profile.name(), "Microsoft", profile.id().toString()));


                            saveAlts();
                        }
                    });
                }

                byte[] b = resp.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, b.length);
                exchange.getResponseBody().write(b);
                exchange.getResponseBody().close();

                modalMode = "NONE";
                isMicrosoftAuthLoading = false;
                stopLocalHttpServer();
            });
            localServer.setExecutor(null);
            localServer.start();

            try {
                java.lang.Runtime.getRuntime().exec("cmd /c start " + MicrosoftAuth.AUTH_URL.replace("&", "^&"));
            } catch (Exception e) {
                minecraft.keyboardHandler.setClipboard(MicrosoftAuth.AUTH_URL);
                authStatusMessage = MainMenuScreen.isRussian ? "Скопировано!" : "Copied URL!";
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    public void stopLocalHttpServer() {
        try { if (localServer != null) { localServer.stop(0); localServer = null; } } catch (Throwable ignored) {}
    }

    private void setSessionReflect(String username, String uuidStr) {
        try {
            // Конструктор в 1.21.1 принимает java.util.UUID, а не String!
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);

            // В 1.21.1 убрали параметр Type, осталось всего 5 аргументов:
            net.minecraft.client.User customSession = new net.minecraft.client.User(
                    username,
                    uuid,
                    "0",
                    java.util.Optional.empty(),
                    java.util.Optional.empty()
            );

            // Внедряем новую сессию в клиент через рефлексию
            java.lang.reflect.Field f = net.minecraft.client.Minecraft.class.getDeclaredField("user");
            f.setAccessible(true);
            f.set(minecraft, customSession);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (isSearchFocused || isInputNickFocused) {
            // Получаем символ из события и приводим int к char
            char codePoint = (char) characterEvent.codepoint();

            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                if (isSearchFocused && searchQuery.length() < 24) {
                    searchQuery += codePoint;
                }
                if (isInputNickFocused && inputNick.length() < 16) {
                    inputNick += codePoint;
                }
            }
            return true;
        }
        return super.charTyped(characterEvent);
    }


    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (isSearchFocused || isInputNickFocused) {
            String activeStr = isSearchFocused ? searchQuery : inputNick;

            // Получаем код нажатой клавиши из события
            int keyCode = keyEvent.key();

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !activeStr.isEmpty()) {
                activeStr = activeStr.substring(0, activeStr.length() - 1);
                if (isSearchFocused) searchQuery = activeStr; else inputNick = activeStr;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (isInputNickFocused && !inputNick.trim().isEmpty()) {
                    String cleanNick = inputNick.trim();
                    String generatedUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + cleanNick).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                    if (modalMode.equalsIgnoreCase("DIRECT")) {
                        setSessionReflect(cleanNick, generatedUuid);
                    } else {
                        registry.add(new AltAccount(cleanNick, "Пиратский", generatedUuid));
                        saveAlts();
                    }
                    modalMode = "NONE";
                }
                isSearchFocused = false;
                isInputNickFocused = false;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }


    private static final File altsFile = new File(Minecraft.getInstance().gameDirectory, "polaris" + File.separator + "alts.txt");

    public static void saveAlts() {
        try {
            if (!altsFile.getParentFile().exists()) altsFile.getParentFile().mkdirs();
            try (PrintWriter writer = new PrintWriter(new FileWriter(altsFile))) {
                for (AltAccount acc : registry) {
                    writer.println(acc.name + ";" + acc.type + ";" + acc.uuid + ";" + acc.isFavorite);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static void loadAlts() {
        if (!altsFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(altsFile))) {
            registry.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    AltAccount acc = new AltAccount(parts[0], parts[1], parts[2]);
                    if (parts.length == 4) acc.isFavorite = Boolean.parseBoolean(parts[3]);
                    registry.add(acc);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    private void drawPlayerHead(float x, float y, float size, String name) {
        float radius = Math.max(2.5f, size * 0.22f);
        try {

            String steveTexture = "minecraft:textures/entity/player/wide/steve.png";

            drawSkinFace(steveTexture, x, y, size, radius);
            return;
        } catch (Throwable ignored) {}

        Render2D.rect(x, y, size, size, radius, ColorUtil.rgba(50, 54, 64, 230));
    }


    private void drawSkinFace(String texture, float x, float y, float size, float radius) {
        int col = ColorUtil.rgba(255, 255, 255, 255);
        Render2D.imageUvNearest(texture, x, y, size, size, radius, 0.8f, 8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, col);
        Render2D.imageUvNearest(texture, x, y, size, size, radius, 0.8f, 40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, col);
    }

}
