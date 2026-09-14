package polaris.screens.mainmenu.loading;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import polaris.screens.mainmenu.MainMenuScreen;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;


public final class LoadingScreen extends Screen {
    public static boolean LOADED;
    public static boolean FINISH;
    public static boolean COMPLETED;

    
    private static final int FIRST = ColorUtil.rgba(14, 17, 21, 255);
    private static final int SECOND = ColorUtil.rgba(24, 28, 36, 255);
    private static final int TEXT = ColorUtil.rgba(235, 237, 242, 255);
    private static final int TEXT_SECOND = ColorUtil.rgba(140, 146, 160, 255);
    private static final int THIRD = ColorUtil.rgba(90, 110, 160, 255);

    private final ForwardAnim bgAnim = new ForwardAnim().duration(700).ease(ForwardAnim.Ease.BOTH_SINE);
    private final ForwardAnim showBgAnim = new ForwardAnim().duration(650).ease(ForwardAnim.Ease.BOTH_SINE);
    private final ForwardAnim showingAnim = new ForwardAnim().duration(420).ease(ForwardAnim.Ease.OUT_CIRC);
    private final ForwardAnim logoAnim = new ForwardAnim().duration(420).ease(ForwardAnim.Ease.OUT_CIRC);

    private final AssetsLoader assetsLoader = new AssetsLoader();
    private final SmoothScroll yAnim = new SmoothScroll();

    private boolean start;
    private boolean hello;
    private boolean finishHold;
    private long finishAtMs;
    private float spinTicks;

    public LoadingScreen() {
        super(Component.literal("Загрузка"));
        FINISH = false;
        LoadingStage.resetAll();
    }

    @Override
    protected void init() {
        super.init();
        LOADED = true;
    }

    @Override
    public void tick() {
        super.tick();
        spinTicks += 1f;
        if (start && !assetsLoader.isFinished()) {
            assetsLoader.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dw = Render2D.getFixedScaledWidth();
        float dh = Render2D.getFixedScaledHeight();

        
        boolean fadingOut = false;
        if (FINISH) {
            if (!finishHold) {
                finishHold = true;
                finishAtMs = System.currentTimeMillis();
            }
            fadingOut = System.currentTimeMillis() - finishAtMs > 650;
        }

        
        bgAnim.setForward(!fadingOut);
        showBgAnim.setForward(bgAnim.get() > 0.7f && !fadingOut);
        showingAnim.setForward(showBgAnim.isForward() && showBgAnim.get() > 0.8f && !fadingOut);
        logoAnim.setForward(showingAnim.finished() && !fadingOut);

        if (start && !assetsLoader.isFinished()) {
            assetsLoader.tick();
        }

        Render2D.beginFrame(graphics);

        
        float bg = bgAnim.get();
        Render2D.rect(0, 0, dw, dh, 0f, ColorUtil.withAlpha(FIRST, Math.round(255 * Math.max(bg, FINISH ? bg : 1f))));

        if (bg > 0.7f) {
            
            if (!start && logoAnim.finished()) {
                assetsLoader.start();
                start = true;
            }

            
            int i = 0;
            for (LoadingStage stage : LoadingStage.values()) {
                if (stage == LoadingStage.current && LoadingStage.afterReady.finished(300)) {
                    yAnim.animateTo(i * 60f);
                }
                i++;
            }
            yAnim.update();
            float summ = yAnim.get();

            
            float logo = logoAnim.get();
            float offY = lerp(-LoadingStage.values().length * 50f - 20f, dh / 2f, logo);

            
            float left = dw / 2f - dw / 7f;
            float iconX = left;
            float textX = left + 18f;
            float titleSize = 13.5f;
            float detailSize = 9.5f;

            for (LoadingStage stage : LoadingStage.values()) {
                float active = stage.getActiveAnim().get();
                float ready = stage.getReadyAnim().get();
                float rowY = offY - summ;

                
                text(stage.getTitle(), textX, rowY, titleSize,
                        ColorUtil.withAlpha(TEXT, Math.round(255 * logo)));

                
                String detail = stage.getStage();
                if (detail != null && !detail.isEmpty()) {
                    text(detail, textX, rowY + 12f, detailSize,
                            ColorUtil.withAlpha(TEXT_SECOND, Math.round(255 * logo * active)));
                }

                
                float iconY = rowY + 1f;

                float idleA = logo * (1f - active);
                if (idleA > 0.02f) {
                    Render2D.rect(iconX + 2f, iconY + 2f, 10f, 10f, 5f,
                            ColorUtil.withAlpha(SECOND, Math.round(255 * idleA)));
                }

                float waitA = logo * active * (1f - ready);
                if (waitA > 0.02f) {
                    drawWaitIcon(iconX, iconY - 1f, waitA);
                }

                float readyA = logo * ready;
                if (readyA > 0.02f) {
                    drawSuccessIcon(iconX, iconY - 1f, readyA);
                }

                
                offY += 30f + 30f * active;
            }

            
            int c = ColorUtil.withAlpha(FIRST, Math.round(255 * logo));
            int c0 = ColorUtil.withAlpha(FIRST, 0);
            float q = dh / 4f;
            Render2D.rect(0, 0, dw, q, 0f, c);
            Render2D.rect(0, dh - q, dw, q, 0f, c);
            Render2D.rect(0, q - 1f, dw, q, 0f, c, c, c0, c0);
            Render2D.rect(0, dh / 2f + 1f, dw, q, 0f, c0, c0, c, c);

            
            drawGrid(dw, dh, bg * 0.1f);
        }

        Render2D.flush();

        
        if (bgAnim.finished(false)) {
            if (!hello) {
                hello = true;
                COMPLETED = true;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(new MainMenuScreen());
            }
        }
    }

    private void drawWaitIcon(float x, float y, float anim) {
        
        float size = 9f;
        float ox = x + 3f;
        float oy = y + 3.5f;
        float thickness = 1.4f;
        float rot = (spinTicks * 8f) % 360f;
        Render2D.arc(ox, oy, size, thickness, 360f, 0f,
                ColorUtil.rgba(220, 220, 220, Math.round(255 * anim * 0.3f)));
        Render2D.arc(ox, oy, size, thickness, 110f, rot,
                ColorUtil.rgba(255, 255, 255, Math.round(255 * anim)));
    }

    private void drawSuccessIcon(float x, float y, float anim) {
        
        float ox = x + 3f;
        float oy = y + 3f;
        Render2D.rect(ox, oy, 9f, 9f, 4.5f,
                ColorUtil.rgba(0, 200, 90, Math.round(255 * anim)));
        
        Render2D.rect(ox + 1.6f, oy + 4.2f, 2.6f, 1.3f, 0.6f,
                ColorUtil.rgba(255, 255, 255, Math.round(255 * anim)));
        Render2D.rect(ox + 3.2f, oy + 2.2f, 1.3f, 4.2f, 0.6f,
                ColorUtil.rgba(255, 255, 255, Math.round(255 * anim)));
    }

    private void drawGrid(float dw, float dh, float alpha) {
        float step = Math.max(1f, dw / 15f);
        int col = ColorUtil.withAlpha(THIRD, Math.round(255 * alpha));
        for (float x = 0; x < dw; x += step) {
            Render2D.rect(x, 0, 1f, dh, 0f, col);
        }
        for (float y = 0; y < dh; y += step) {
            Render2D.rect(0, y, dw, 1f, 0f, col);
        }
    }

    private static void text(String value, float x, float y, float size, int color) {
        Render2D.text("semibold", value, x, y, size, color);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        
    }

    
    private static final class SmoothScroll {
        private float value;
        private float target;

        void animateTo(float target) {
            this.target = target;
        }

        void update() {
            
            value += (target - value) * 0.18f;
            if (Math.abs(target - value) < 0.05f) {
                value = target;
            }
        }

        float get() {
            return value;
        }
    }
}
