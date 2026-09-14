package polaris.api.module.impl.visual;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.MultiModeSetting;
import polaris.api.settings.impl.NumberSetting;


public final class Animations extends Module {
    public static final String CHAT = "Chat";
    public static final String TAB = "Tab";
    public static final String INVENTORY = "Inventory";
    public static final String CONTAINERS = "Containers";
    public static final String BUTTONS = "Buttons";
    public static final String PERSPECTIVE = "Perspective";

    
    private static final long CHAT_OPEN_MS = 240L;
    private static final long CHAT_CLOSE_MS = 220L;
    private static final long TAB_OPEN_MS = 300L;
    private static final long TAB_CLOSE_MS = 240L;
    private static final long SCREEN_OPEN_MS = 140L;
    private static final long SCREEN_CLOSE_MS = 120L;
    
    private static final long CLOSE_TIMEOUT_MS = 400L;
    
    private static final float CHAT_SLIDE = 30.0f;

    private static Animations instance;

    private final MultiModeSetting targets = register(new MultiModeSetting("Animate", "Interfaces to animate.",
            new String[]{CHAT, TAB, INVENTORY, CONTAINERS, BUTTONS, PERSPECTIVE},
            CHAT, TAB, INVENTORY, CONTAINERS, PERSPECTIVE));
    private final ModeSetting easing = register(new ModeSetting("Easing", "Animation curve.",
            "Ease Out Back",
            "Linear", "Ease Out Quad", "Ease Out Cubic", "Ease Out Quart",
            "Ease Out Expo", "Ease Out Back", "Ease Out Elastic", "Ease Out Bounce"));
    private final NumberSetting chatSpeed = register(new NumberSetting("Chat Speed", "Chat animation speed.", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting tabSpeed = register(new NumberSetting("Tab Speed", "Tab list animation speed.", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting inventorySpeed = register(new NumberSetting("Inventory Speed", "Inventory animation speed.", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting containerSpeed = register(new NumberSetting("Container Speed", "Container animation speed.", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting buttonSpeed = register(new NumberSetting("Button Speed", "Widget hover speed.", 1.0, 0.1, 3.0, 0.1));
    private final NumberSetting perspectiveSpeed = register(new NumberSetting("F5 Speed", "Perspective switch speed.", 1.0, 0.1, 3.0, 0.1));

    private final Fade chatFade = new Fade();
    private final Fade tabFade = new Fade();
    private final Fade screenFade = new Fade();

    private boolean chatClosing;
    private long chatCloseStart;
    private boolean chatReleased;

    private Screen animatedScreen;
    private boolean screenClosing;
    private long screenCloseStart;
    private boolean screenReleased;

    public Animations() {
        super("Animations", "Open / close animations for chat, tab, inventory and widgets.", ModuleCategory.VISUAL);
        chatSpeed.visibleWhen(() -> targets.isSelected(CHAT));
        tabSpeed.visibleWhen(() -> targets.isSelected(TAB));
        inventorySpeed.visibleWhen(() -> targets.isSelected(INVENTORY));
        containerSpeed.visibleWhen(() -> targets.isSelected(CONTAINERS));
        buttonSpeed.visibleWhen(() -> targets.isSelected(BUTTONS));
        perspectiveSpeed.visibleWhen(() -> targets.isSelected(PERSPECTIVE));
        instance = this;
    }

    public static Animations getInstance() {
        return instance;
    }

    private static boolean active(String target) {
        Animations animations = instance;
        return animations != null && animations.isEnabled() && animations.targets.isSelected(target);
    }

    @Override
    protected void onDisable() {
        resetChat();
        resetScreen();
        tabFade.reset(0.0f);
    }

    

    public static boolean chatEnabled() {
        return active(CHAT);
    }

    
    public static float chatOffset() {
        Animations animations = instance;
        if (animations == null || !chatEnabled()) {
            return 0.0f;
        }
        long duration = animations.chatClosing
                ? scale(CHAT_CLOSE_MS, animations.chatSpeed)
                : scale(CHAT_OPEN_MS, animations.chatSpeed);
        float raw = animations.chatFade.update(duration, animations.chatClosing);
        return (1.0f - animations.ease(raw)) * CHAT_SLIDE;
    }

    
    public static boolean deferChatClose() {
        Animations animations = instance;
        if (animations == null || !chatEnabled() || animations.chatReleased) {
            return false;
        }
        if (animations.chatClosing) {
            
            animations.resetChat();
            return false;
        }
        animations.chatClosing = true;
        animations.chatCloseStart = System.currentTimeMillis();
        return true;
    }

    public static void resetChatState() {
        Animations animations = instance;
        if (animations != null) {
            animations.resetChat();
        }
    }

    private void resetChat() {
        chatClosing = false;
        chatCloseStart = 0L;
        chatFade.reset(0.0f);
    }

    

    public static boolean tabEnabled() {
        return active(TAB);
    }

    
    public static boolean keepTabVisible(boolean pressed) {
        Animations animations = instance;
        if (animations == null || !tabEnabled()) {
            return pressed;
        }
        long duration = pressed
                ? scale(TAB_OPEN_MS, animations.tabSpeed)
                : scale(TAB_CLOSE_MS, animations.tabSpeed);
        float raw = animations.tabFade.update(duration, !pressed);
        return pressed || raw > 0.001f;
    }

    
    public static float tabScale() {
        Animations animations = instance;
        if (animations == null || !tabEnabled()) {
            return 1.0f;
        }
        return Math.max(0.0f, animations.ease(animations.tabFade.value()));
    }

    

    public static boolean screenEnabled(Screen screen) {
        if (screen == null) {
            return false;
        }
        return screen instanceof InventoryScreen ? active(INVENTORY) : active(CONTAINERS);
    }

    
    public static float screenScale(Screen screen) {
        Animations animations = instance;
        if (animations == null || !screenEnabled(screen)) {
            return 1.0f;
        }
        if (animations.animatedScreen != screen) {
            animations.animatedScreen = screen;
            animations.screenClosing = false;
            animations.screenCloseStart = 0L;
            animations.screenReleased = false;
            animations.screenFade.reset(0.0f);
        }
        long duration = animations.screenClosing
                ? scale(SCREEN_CLOSE_MS, animations.speedFor(screen))
                : scale(SCREEN_OPEN_MS, animations.speedFor(screen));
        float raw = animations.screenFade.update(duration, animations.screenClosing);
        
        return 0.75f + 0.25f * animations.ease(raw);
    }

    
    public static boolean deferScreenClose(Screen screen) {
        Animations animations = instance;
        if (animations == null || !screenEnabled(screen) || animations.screenReleased) {
            return false;
        }
        if (animations.screenClosing) {
            animations.resetScreen();
            return false;
        }
        animations.animatedScreen = screen;
        animations.screenClosing = true;
        animations.screenCloseStart = System.currentTimeMillis();
        return true;
    }

    public static void resetScreenState() {
        Animations animations = instance;
        if (animations != null) {
            animations.resetScreen();
        }
    }

    private void resetScreen() {
        animatedScreen = null;
        screenClosing = false;
        screenCloseStart = 0L;
        screenReleased = false;
        screenFade.reset(0.0f);
    }

    private NumberSetting speedFor(Screen screen) {
        return screen instanceof InventoryScreen ? inventorySpeed : containerSpeed;
    }

    

    public static boolean buttonsEnabled() {
        return active(BUTTONS);
    }

    
    public static float buttonRate() {
        Animations animations = instance;
        float speed = animations == null ? 1.0f : clampSpeed(animations.buttonSpeed);
        return 12.0f * speed;
    }

    

    public static boolean perspectiveEnabled() {
        return active(PERSPECTIVE);
    }

    
    public static float perspectiveRate() {
        Animations animations = instance;
        return animations == null ? 1.0f : clampSpeed(animations.perspectiveSpeed);
    }

    

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        if (chatClosing && chatFinished()) {
            Screen screen = mc.screen;
            resetChat();
            if (screen instanceof ChatScreen) {
                
                chatReleased = true;
                try {
                    screen.onClose();
                } finally {
                    chatReleased = false;
                }
            }
        }
        if (screenClosing && screenFinished()) {
            Screen screen = animatedScreen;
            resetScreen();
            if (screen != null && mc.screen == screen) {
                
                screenReleased = true;
                try {
                    screen.onClose();
                } finally {
                    screenReleased = false;
                }
            }
        }
    }

    private boolean chatFinished() {
        long duration = scale(CHAT_CLOSE_MS, chatSpeed);
        if (System.currentTimeMillis() - chatCloseStart >= duration + CLOSE_TIMEOUT_MS) {
            return true;
        }
        return chatFade.value() <= 0.001f;
    }

    private boolean screenFinished() {
        long duration = scale(SCREEN_CLOSE_MS, speedFor(animatedScreen));
        if (System.currentTimeMillis() - screenCloseStart >= duration + CLOSE_TIMEOUT_MS) {
            return true;
        }
        return screenFade.value() <= 0.001f;
    }

    
    public static boolean isScreenCloseReleased() {
        Animations animations = instance;
        return animations != null && animations.screenReleased;
    }

    

    private float ease(float t) {
        float x = Math.max(0.0f, Math.min(1.0f, t));
        return switch (easing.getValue()) {
            case "Linear" -> x;
            case "Ease Out Quad" -> 1.0f - (1.0f - x) * (1.0f - x);
            case "Ease Out Cubic" -> 1.0f - (float) Math.pow(1.0f - x, 3.0);
            case "Ease Out Quart" -> 1.0f - (float) Math.pow(1.0f - x, 4.0);
            case "Ease Out Expo" -> x >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * x);
            case "Ease Out Elastic" -> elasticOut(x);
            case "Ease Out Bounce" -> bounceOut(x);
            default -> backOut(x);
        };
    }

    private static float backOut(float x) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float inv = x - 1.0f;
        return 1.0f + c3 * inv * inv * inv + c1 * inv * inv;
    }

    private static float elasticOut(float x) {
        if (x <= 0.0f || x >= 1.0f) {
            return x;
        }
        double c4 = (2.0 * Math.PI) / 3.0;
        return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
    }

    private static float bounceOut(float x) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (x < 1.0f / d1) {
            return n1 * x * x;
        }
        if (x < 2.0f / d1) {
            float v = x - 1.5f / d1;
            return n1 * v * v + 0.75f;
        }
        if (x < 2.5f / d1) {
            float v = x - 2.25f / d1;
            return n1 * v * v + 0.9375f;
        }
        float v = x - 2.625f / d1;
        return n1 * v * v + 0.984375f;
    }

    private static long scale(long baseMs, NumberSetting speed) {
        return Math.max(1L, Math.round(baseMs / clampSpeed(speed)));
    }

    private static float clampSpeed(NumberSetting setting) {
        if (setting == null) {
            return 1.0f;
        }
        float value = setting.getFloat();
        return Float.isFinite(value) && value > 0.0f ? value : 1.0f;
    }

    
    private static final class Fade {
        private float value;
        private long last;

        float update(long durationMs, boolean closing) {
            long now = System.currentTimeMillis();
            if (last == 0L) {
                last = now;
            }
            float delta = (now - last) / 1000.0f;
            last = now;
            if (delta > 0.1f) {
                delta = 0.1f;
            }
            float rate = durationMs <= 0L ? 1000.0f : 1000.0f / durationMs;
            value += (closing ? -delta : delta) * rate;
            if (value < 0.0f) {
                value = 0.0f;
            } else if (value > 1.0f) {
                value = 1.0f;
            }
            return value;
        }

        float value() {
            return value;
        }

        void reset(float to) {
            value = to;
            last = 0L;
        }
    }
}
