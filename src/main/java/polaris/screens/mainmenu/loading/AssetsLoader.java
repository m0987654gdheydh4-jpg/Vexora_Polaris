package polaris.screens.mainmenu.loading;

import polaris.screens.clickgui.ClickGui;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;
import polaris.utils.render.ui.gif.MainMenuGifPreloader;


public final class AssetsLoader {
    private static final int[] BOLD_SIZES = {17, 15, 14, 12, 11, 13, 10, 16, 20, 24, 18};
    private static final int[] SEMI_SIZES = {16, 14, 15, 13, 12, 10, 17};

    private static final String[] IMAGES = {
            "icons/menu/setting",
            "icons/info",
            "icons/yes",
            "icons/hud/logo",
            "icons/hud/coords",
            "icons/hud/server",
            "icons/hud/speed",
            "icons/hud/bind",
            "masks/wheel_element",
            "masks/glow",
            "masks/circle",
            "ui/check",
            "ui/close",
            "emotions/hello",
            "emotions/happy",
            "menu/backmenu"
    };

    private final java.util.List<Runnable> steps = new java.util.ArrayList<>();
    private int index;
    private boolean started;
    private boolean finished;
    private long nextAt;

    public AssetsLoader() {
        build();
    }

    private void build() {
        
        steps.add(() -> {
            LoadingStage.CLIENT.activate();
            LoadingStage.stage("Загружаю менеджеры…");
        });
        steps.add(() -> LoadingStage.stage("Загружаю модули…"));
        steps.add(() -> LoadingStage.stage("Загружаю конфиги…"));
        steps.add(() -> LoadingStage.stage("Загружаю команды…"));

        
        steps.add(LoadingStage.FONTS::activate);
        for (int size : BOLD_SIZES) {
            int s = size;
            steps.add(() -> {
                LoadingStage.stage("Шрифт " + s + " с начертанием Bold");
                warmup(FontType.BOLD, s);
            });
        }
        for (int size : SEMI_SIZES) {
            int s = size;
            steps.add(() -> {
                LoadingStage.stage("Шрифт " + s + " с начертанием Semibold");
                warmup(FontType.SEMIBOLD, s);
            });
        }

        
        steps.add(LoadingStage.IMAGES::activate);
        for (String path : IMAGES) {
            String p = path;
            steps.add(() -> {
                LoadingStage.stage("Картинка " + p.replace("/", " -> "));
                try {
                    Render2D.warmupText(FontType.SEMIBOLD, p, 10);
                } catch (Throwable ignored) {
                }
            });
        }
        steps.add(() -> {
            LoadingStage.stage("Картинка ui -> back.gif");
            var gif = MainMenuGifPreloader.background();
            if (gif != null && !gif.isReady() && !gif.isFailed()) {
                gif.ensureLoaded();
            }
        });
        steps.add(() -> {
            LoadingStage.stage("Прогрев Click GUI…");
            try {
                ClickGui.warmupText();
            } catch (Throwable ignored) {
            }
        });
        steps.add(() -> {
            LoadingStage.IMAGES.getReadyAnim().setForward(true);
            LoadingStage.IMAGES.setStage("Готово!");
            LoadingScreen.FINISH = true;
            finished = true;
        });
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        index = 0;
        nextAt = System.currentTimeMillis();
    }

    public void tick() {
        if (!started || finished) {
            return;
        }
        long now = System.currentTimeMillis();
        
        while (index < steps.size() && now >= nextAt) {
            try {
                steps.get(index++).run();
            } catch (Throwable ignored) {
            }
            nextAt = now + 95;
            now = System.currentTimeMillis();
        }
        if (index >= steps.size()) {
            finished = true;
            LoadingScreen.FINISH = true;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    private static void warmup(FontType font, float size) {
        try {
            Render2D.warmupText(font, "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩabcABC0123456789", size);
            Render2D.warmupText(font, "Загрузка", size);
        } catch (Throwable ignored) {
        }
    }
}
