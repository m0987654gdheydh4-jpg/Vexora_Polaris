package polaris.screens.mainmenu.loading;

import polaris.utils.timer.StopWatch;


public enum LoadingStage {
    CLIENT("Загрузка клиента"),
    FONTS("Загрузка шрифтов"),
    IMAGES("Загрузка картинок");

    public static LoadingStage current;
    public static final StopWatch afterReady = new StopWatch();

    private final String title;
    private volatile String stage = "";
    private final ForwardAnim activeAnim = new ForwardAnim().duration(420).finishAt(false);
    private final ForwardAnim readyAnim = new ForwardAnim().duration(420).finishAt(false);

    LoadingStage(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getStage() {
        return stage == null ? "" : stage;
    }

    public void setStage(String stage) {
        this.stage = stage == null ? "" : stage;
    }

    public ForwardAnim getActiveAnim() {
        return activeAnim;
    }

    public ForwardAnim getReadyAnim() {
        return readyAnim;
    }

    public void activate() {
        if (current != null) {
            current.stage = "Готово!";
            current.readyAnim.setForward(true);
            afterReady.reset();
        }
        activeAnim.setForward(true);
        current = this;
    }

    public static void stage(String message) {
        if (current != null) {
            current.stage = message;
        }
    }

    public static void resetAll() {
        current = null;
        afterReady.reset();
        for (LoadingStage s : values()) {
            s.stage = "";
            s.activeAnim.finishAt(false);
            s.readyAnim.finishAt(false);
        }
    }
}
