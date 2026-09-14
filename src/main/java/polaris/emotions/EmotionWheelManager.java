package polaris.emotions;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.events.impl.KeyEvent;
import polaris.api.events.impl.MouseRotationEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.impl.visual.Emotions;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.font.FontType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class EmotionWheelManager {
    private static final float FADE_IN_MS = 280f;
    private static final float FADE_OUT_MS = 320f;
    private static final EmotionType[] EMOTIONS = EmotionType.values();
    private static final int SLOT_COUNT = EMOTIONS.length; 

    private static EmotionWheelManager instance;

    private final Map<UUID, ActiveEmotion> activeEmotions = new ConcurrentHashMap<>();

    private boolean wheelOpen;
    private float wheelAnim;
    private float wheelAnimTarget;
    private long lastAnimMs;

    private final float[] slotHover = new float[SLOT_COUNT];
    private double cursorX;
    private double cursorY;
    private int stickySlot = -1;
    private EmotionType hovered;
    private EmotionType previewEmotion = EmotionType.HELLO;
    private long previewStartedAtMs;

    private EmotionWheelManager() {
    }

    public static EmotionWheelManager create() {
        instance = new EmotionWheelManager();
        return instance;
    }

    public static EmotionWheelManager getInstance() {
        return instance;
    }

    public boolean isWheelOpen() {
        return wheelOpen || wheelAnim > 0.01f;
    }

    @SubscribeEvent
    public void onKey(KeyEvent event) {
        Emotions module = Emotions.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        int openKey = module.getWheelBind().getValue().getCode();
        if (openKey < 0 || event.key() != openKey
                || event.type() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (event.action() == GLFW.GLFW_PRESS) {
            if (mc.player != null && mc.level != null && mc.screen == null) {
                openWheel();
            }
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            if (wheelOpen) {
                EmotionType pick = hovered;
                closeWheel(false);
                if (pick != null) {
                    playSelectedEmotion(pick);
                }
            }
        }
    }

    @SubscribeEvent
    public void onMouse(MouseRotationEvent event) {
        if (!wheelOpen) {
            return;
        }
        
        cursorX += event.getCursorDeltaX() * 0.85;
        cursorY += event.getCursorDeltaY() * 0.85;
        double max = 160.0;
        double len = Math.hypot(cursorX, cursorY);
        if (len > max) {
            cursorX = cursorX / len * max;
            cursorY = cursorY / len * max;
        }
        event.setCancelled(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent.Post event) {
        Emotions module = Emotions.getInstance();
        Minecraft mc = Minecraft.getInstance();

        if (module == null || !module.isEnabled()) {
            closeWheel(true);
            stopLocalEmotion();
            return;
        }
        if (mc.player == null || mc.level == null) {
            closeWheel(true);
            stopLocalEmotion();
            return;
        }
        if (mc.screen != null && wheelOpen) {
            closeWheel(true);
        }

        Iterator<Map.Entry<UUID, ActiveEmotion>> it = activeEmotions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveEmotion> entry = it.next();
            if (!entry.getValue().isExpired()) {
                continue;
            }
            if (entry.getValue().local && mc.player.getUUID().equals(entry.getKey())) {
                mc.options.setCameraType(entry.getValue().previousCameraType);
            }
            it.remove();
        }
    }

    @SubscribeEvent
    public void onDraw(DrawEvent event) {
        if (event.getLayer() != DrawEvent.Layer.GAME) {
            return;
        }

        
        long now = System.currentTimeMillis();
        float dt = lastAnimMs == 0L ? 0.016f : Mth.clamp((now - lastAnimMs) / 1000f, 0.001f, 0.05f);
        lastAnimMs = now;

        wheelAnimTarget = wheelOpen ? 1f : 0f;
        wheelAnim = approach(wheelAnim, wheelAnimTarget, dt, 10f);

        if (wheelAnim <= 0.001f && !wheelOpen) {
            return;
        }

        updateHoverStable();
        for (int i = 0; i < SLOT_COUNT; i++) {
            float target = (stickySlot == i) ? 1f : 0f;
            slotHover[i] = approach(slotHover[i], target, dt, 14f);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return;
        }
        drawWheel(event);
    }

    public void closeWheel() {
        closeWheel(true);
    }

    public void closeWheel(boolean force) {
        wheelOpen = false;
        if (force) {
            hovered = null;
            stickySlot = -1;
        }
    }

    public void openFromModule() {
        Emotions module = Emotions.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        openWheel();
    }

    public void setPreviewEmotion(EmotionType emotionType) {
        if (emotionType == null) {
            return;
        }
        if (previewEmotion != emotionType) {
            previewEmotion = emotionType;
            previewStartedAtMs = System.currentTimeMillis();
        }
    }

    public EmotionType getPreviewEmotion() {
        return previewEmotion;
    }

    public void playSelectedEmotion(EmotionType emotionType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || emotionType == null) {
            return;
        }
        startEmotion(mc.player, emotionType, true, System.currentTimeMillis());
    }

    public void applyToModel(AvatarRenderState state,
                             ModelPart head, ModelPart hat, ModelPart body,
                             ModelPart rightArm, ModelPart leftArm,
                             ModelPart rightLeg, ModelPart leftLeg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) {
            return;
        }

        EmotionType type = null;
        float blend = 0f;
        long startedAt = 0L;

        ActiveEmotion active = activeEmotions.get(player.getUUID());
        if (active != null) {
            type = active.type;
            blend = active.getBlend();
            startedAt = active.startedAt;
        } else if (isWheelOpen() && mc.player != null && player == mc.player && hovered != null) {
            type = hovered;
            startedAt = previewStartedAtMs;
            blend = Mth.clamp(wheelAnim * 0.85f, 0f, 1f);
        }

        if (type == null || blend <= 0.001f) {
            return;
        }

        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        boolean hands = canAnimateHands(player);

        switch (type) {
            case DEB -> poseDeb(blend, hands, head, rightArm, leftArm);
            case FLOSS -> poseFloss(blend, elapsed, hands, body, rightArm, leftArm, rightLeg, leftLeg);
            case MASTURBATE -> poseMasturbate(blend, elapsed, hands, player, body, rightArm, leftArm, rightLeg, leftLeg);
            case HELLO -> poseHello(blend, elapsed, hands, rightArm);
            case GET_GRIDDY -> poseGriddy(blend, elapsed, hands, head, body, rightArm, leftArm, rightLeg, leftLeg);
            case HAPPY -> poseHappy(blend, hands, rightArm, leftArm);
        }

        hat.xRot = head.xRot;
        hat.yRot = head.yRot;
        hat.zRot = head.zRot;
    }

    

    private void openWheel() {
        wheelOpen = true;
        cursorX = 0;
        cursorY = 0;
        stickySlot = -1;
        hovered = null;
        previewEmotion = EmotionType.HELLO;
        previewStartedAtMs = System.currentTimeMillis();
        lastAnimMs = System.currentTimeMillis();
    }

    
    private void updateHoverStable() {
        double len = Math.hypot(cursorX, cursorY);
        
        if (len < 28.0) {
            
            if (stickySlot >= 0 && stickySlot < SLOT_COUNT) {
                hovered = EMOTIONS[stickySlot];
            } else {
                hovered = null;
            }
            return;
        }

        
        
        double ang = Math.atan2(cursorY, cursorX); 
        double deg = Math.toDegrees(ang); 
        
        double fromTop = deg + 90.0;
        if (fromTop < 0) {
            fromTop += 360.0;
        }
        if (fromTop >= 360.0) {
            fromTop -= 360.0;
        }

        float sector = 360f / SLOT_COUNT;
        int raw = (int) Math.floor(fromTop / sector + 0.0001);
        raw = Mth.clamp(raw, 0, SLOT_COUNT - 1);

        
        if (stickySlot < 0) {
            stickySlot = raw;
        } else if (raw != stickySlot) {
            double centerOfRaw = raw * sector + sector * 0.5;
            double centerOfSticky = stickySlot * sector + sector * 0.5;
            double dRaw = angularDist(fromTop, centerOfRaw);
            double dSticky = angularDist(fromTop, centerOfSticky);
            
            if (dRaw + 8.0 < dSticky) {
                stickySlot = raw;
            }
        }

        if (stickySlot >= 0 && stickySlot < SLOT_COUNT) {
            hovered = EMOTIONS[stickySlot];
            setPreviewEmotion(hovered);
        } else {
            hovered = null;
        }
    }

    private void drawWheel(DrawEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float guiW = mc.getWindow().getGuiScaledWidth();
        float guiH = mc.getWindow().getGuiScaledHeight();
        float anim = easeOutCubic(Mth.clamp(wheelAnim, 0f, 1f));

        Render2D.beginFrame(event.getGraphics());

        
        Render2D.rect(0, 0, Render2D.guiToFixed(guiW), Render2D.guiToFixed(guiH), 0, withAlpha(0xFF000000, 0.45f * anim));

        float cx = guiW * 0.5f;
        float cy = guiH * 0.5f;
        float fcx = Render2D.guiToFixed(cx);
        float fcy = Render2D.guiToFixed(cy);

        
        float ringR = 118f * (0.70f + 0.30f * anim);
        float slotBase = 46f;
        float hubR = 58f;

        
        float outerSize = (ringR + slotBase + 14f) * 2f;
        Render2D.rect(
                Render2D.guiToFixed(cx - outerSize / 2f),
                Render2D.guiToFixed(cy - outerSize / 2f),
                Render2D.guiToFixed(outerSize),
                Render2D.guiToFixed(outerSize),
                Render2D.guiToFixed(outerSize / 2f),
                withAlpha(0xFF12161C, 0.72f * anim)
        );

        
        float hubSize = hubR * 2f;
        Render2D.rect(
                Render2D.guiToFixed(cx - hubR),
                Render2D.guiToFixed(cy - hubR),
                Render2D.guiToFixed(hubSize),
                Render2D.guiToFixed(hubSize),
                Render2D.guiToFixed(hubR),
                withAlpha(0xFF1C222C, 0.95f * anim)
        );
        Render2D.rect(
                Render2D.guiToFixed(cx - hubR + 6f),
                Render2D.guiToFixed(cy - hubR + 6f),
                Render2D.guiToFixed(hubSize - 12f),
                Render2D.guiToFixed(hubSize - 12f),
                Render2D.guiToFixed(hubR - 6f),
                withAlpha(0xFF252D3A, 0.95f * anim)
        );

        
        String hubText = hovered != null ? hovered.getLocalized() : "Эмоции";
        float hubTw = Render2D.textWidth(FontType.SEMIBOLD, hubText, 15);
        Render2D.text(FontType.SEMIBOLD, hubText, fcx - hubTw / 2f, fcy - Render2D.guiToFixed(7), 15, withAlpha(0xFFFFFFFF, anim));

        
        for (int i = 0; i < SLOT_COUNT; i++) {
            float hover = slotHover[i];
            double ang = Math.toRadians(-90.0 + i * (360.0 / SLOT_COUNT));
            float sx = cx + (float) Math.cos(ang) * ringR;
            float sy = cy + (float) Math.sin(ang) * ringR;

            float r = slotBase * (0.92f + 0.18f * hover) * (0.85f + 0.15f * anim);
            float size = r * 2f;

            int plate = lerpColor(0xFF222833, 0xFF3D6FE0, hover);
            Render2D.rect(
                    Render2D.guiToFixed(sx - r),
                    Render2D.guiToFixed(sy - r),
                    Render2D.guiToFixed(size),
                    Render2D.guiToFixed(size),
                    Render2D.guiToFixed(r),
                    withAlpha(plate, anim)
            );

            
            if (hover > 0.02f) {
                float gr = r + 5f * hover;
                Render2D.rect(
                        Render2D.guiToFixed(sx - gr),
                        Render2D.guiToFixed(sy - gr),
                        Render2D.guiToFixed(gr * 2f),
                        Render2D.guiToFixed(gr * 2f),
                        Render2D.guiToFixed(gr),
                        withAlpha(0xFF5B8CFF, anim * hover * 0.22f)
                );
            }

            EmotionType type = EMOTIONS[i];
            float icon = r * (1.15f + 0.20f * hover);
            Render2D.image(
                    type.getTexture(),
                    Render2D.guiToFixed(sx - icon / 2f),
                    Render2D.guiToFixed(sy - icon / 2f),
                    Render2D.guiToFixed(icon),
                    Render2D.guiToFixed(icon),
                    0f,
                    withAlpha(0xFFFFFFFF, anim * (0.70f + 0.30f * hover))
            );
        }

        
        String title = "Выбери нужную эмоцию";
        String sub = "Отпусти клавишу чтобы применить";
        float titleY = cy - ringR - slotBase - 36f - 24f * (1f - anim);
        float tw = Render2D.textWidth(FontType.SEMIBOLD, title, 20);
        Render2D.text(FontType.SEMIBOLD, title, fcx - tw / 2f, Render2D.guiToFixed(titleY), 20, withAlpha(0xFFFFFFFF, anim));
        float sw = Render2D.textWidth(FontType.REGULAR, sub, 13);
        Render2D.text(FontType.REGULAR, sub, fcx - sw / 2f, Render2D.guiToFixed(titleY + 18f), 13, withAlpha(0xFFA8B4C4, anim));

        
        float mx = (float) (cx + cursorX);
        float my = (float) (cy + cursorY);
        Render2D.rect(
                Render2D.guiToFixed(mx - 3.5f),
                Render2D.guiToFixed(my - 3.5f),
                Render2D.guiToFixed(7f),
                Render2D.guiToFixed(7f),
                Render2D.guiToFixed(3.5f),
                withAlpha(0xFFFFFFFF, anim * 0.9f)
        );

        Render2D.flush();
    }

    

    private void poseDeb(float b, boolean hands, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
        if (hands) {
            armXZ(rightArm, b, -0.70f, -2.00f);
            armXZ(leftArm, b, -0.70f, -2.00f);
        }
        head.xRot = Mth.lerp(b, head.xRot, 0.70f);
        head.yRot = Mth.lerp(b, head.yRot, 0.50f);
        head.zRot = Mth.lerp(b, head.zRot, 0.0f);
    }

    private void poseFloss(float b, long t, boolean hands, ModelPart body,
                           ModelPart rightArm, ModelPart leftArm,
                           ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 250L);
        float rightSide = tri(t, 500L);
        float leftSide = tri(t + 250L, 500L);
        if (hands) {
            armXZ(rightArm, b, 0.50f - rightSide, 1.00f - 2.00f * action);
            armXZ(leftArm, b, 0.50f - leftSide, 1.50f - 2.00f * action);
        }
        body.zRot = Mth.lerp(b, body.zRot, 0.30f * action - 0.15f);
        rightLeg.x = Mth.lerp(b, rightLeg.x, -0.50f - 3.00f * action);
        leftLeg.x = Mth.lerp(b, leftLeg.x, 3.00f - 2.50f * action);
    }

    private void poseHello(float b, long t, boolean hands, ModelPart rightArm) {
        if (!hands) {
            return;
        }
        float wave = tri(t, 250L);
        armXZ(rightArm, b, -0.20f, 2.75f - 0.25f * wave);
    }

    private void poseHappy(float b, boolean hands, ModelPart rightArm, ModelPart leftArm) {
        if (!hands) {
            return;
        }
        armXZ(rightArm, b, 0.0f, 2.75f);
        armXZ(leftArm, b, 0.0f, -2.75f);
    }

    private void poseMasturbate(float b, long t, boolean hands, Player player, ModelPart body,
                                ModelPart rightArm, ModelPart leftArm,
                                ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 100L);
        if (!player.isShiftKeyDown()) {
            if (hands) {
                armXZ(rightArm, b, -0.30f - 0.70f * action, -0.55f);
                armXZ(leftArm, b, 0.30f, 1.0f);
            }
            body.xRot = Mth.lerp(b, body.xRot, -0.10f);
            rightLeg.z = Mth.lerp(b, rightLeg.z, -1.0f);
            leftLeg.z = Mth.lerp(b, leftLeg.z, -1.0f);
        } else if (hands) {
            armXZ(rightArm, b, 0.70f - 0.70f * action, -0.55f);
        }
    }

    private void poseGriddy(float b, long t, boolean hands, ModelPart head, ModelPart body,
                            ModelPart rightArm, ModelPart leftArm,
                            ModelPart rightLeg, ModelPart leftLeg) {
        float action = tri(t, 350L);
        float leg = tri(t, 300L);
        float eye = griddyEye(t);
        if (hands) {
            float armX = 1.0f - 1.5f * action - 3.5f * eye;
            armXZ(rightArm, b, armX, 0.5f - 0.75f * action);
            armXZ(leftArm, b, armX, -0.5f + 0.75f * action);
        }
        body.xRot = Mth.lerp(b, body.xRot, 0.2f - 0.05f * action);
        rightLeg.z = Mth.lerp(b, rightLeg.z, 2f - 0.5f * action);
        leftLeg.z = Mth.lerp(b, leftLeg.z, 2f - 0.5f * action);
        rightLeg.xRot = Mth.lerp(b, rightLeg.xRot, 0.3f * leg - 0.3f);
        leftLeg.xRot = Mth.lerp(b, leftLeg.xRot, -0.3f * leg - 0.1f);
        head.yRot = Mth.lerp(b, head.yRot, -0.4f + 0.4f * eye);
        head.xRot = Mth.lerp(b, head.xRot, -0.3f * eye);
        head.zRot = Mth.lerp(b, head.zRot, 0f);
    }

    private static void armXZ(ModelPart arm, float blend, float xRot, float zRot) {
        float a = Mth.clamp(blend, 0f, 1f);
        arm.xRot = Mth.lerp(a, arm.xRot, xRot);
        arm.zRot = Mth.lerp(a, arm.zRot, zRot);
    }

    private static float tri(long elapsed, long halfMs) {
        if (halfMs <= 0L) {
            return 0f;
        }
        long cycle = halfMs * 2L;
        long m = elapsed % cycle;
        return m < halfMs ? (m / (float) halfMs) : (2f - m / (float) halfMs);
    }

    private static float griddyEye(long elapsed) {
        long cycle = elapsed % 3100L;
        if (cycle < 2100L) {
            return 0f;
        }
        return tri(cycle - 2100L, 500L);
    }

    private static boolean canAnimateHands(Player target) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || target != mc.player || !mc.options.getCameraType().isFirstPerson();
    }

    private void stopLocalEmotion() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            activeEmotions.clear();
            return;
        }
        ActiveEmotion active = activeEmotions.remove(mc.player.getUUID());
        if (active != null && active.local) {
            mc.options.setCameraType(active.previousCameraType);
        }
    }

    private void startEmotion(Player player, EmotionType type, boolean local, long startedAt) {
        Minecraft mc = Minecraft.getInstance();
        CameraType previous = CameraType.FIRST_PERSON;
        if (local) {
            ActiveEmotion prev = activeEmotions.get(player.getUUID());
            previous = prev != null && prev.local
                    ? prev.previousCameraType
                    : mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        activeEmotions.put(player.getUUID(), new ActiveEmotion(type, startedAt, previous, local));
    }

    private static float approach(float current, float target, float dt, float speed) {
        float t = 1f - (float) Math.exp(-speed * dt);
        return current + (target - current) * t;
    }

    private static float easeOutCubic(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    private static double angularDist(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) (((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f)), 0, 255);
        if (((argb >>> 24) & 0xFF) == 0) {
            a = Mth.clamp((int) (255 * Mth.clamp(alpha, 0f, 1f)), 0, 255);
        }
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        if (aa == 0) aa = 255;
        if (ba == 0) ba = 255;
        int ra = (int) (aa + (ba - aa) * t);
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static final class ActiveEmotion {
        private final EmotionType type;
        private final long startedAt;
        private final CameraType previousCameraType;
        private final boolean local;

        private ActiveEmotion(EmotionType type, long startedAt, CameraType previousCameraType, boolean local) {
            this.type = type;
            this.startedAt = startedAt;
            this.previousCameraType = previousCameraType;
            this.local = local;
        }

        private long elapsed() {
            return System.currentTimeMillis() - startedAt;
        }

        private boolean isExpired() {
            return elapsed() >= type.getDurationMs();
        }

        private float getBlend() {
            long e = elapsed();
            long left = Math.max(0L, type.getDurationMs() - e);
            float in = Mth.clamp(e / FADE_IN_MS, 0f, 1f);
            float out = Mth.clamp(left / FADE_OUT_MS, 0f, 1f);
            float v = Math.min(in, out);
            return v * v * (3f - 2f * v);
        }
    }
}
