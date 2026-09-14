package polaris.api.module.impl.visual;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.DrawEvent;
import polaris.api.events.types.EventPriority;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.animation.Easings;
import polaris.utils.render.animation.SmoothAnimation;
import polaris.utils.render.color.ColorUtil;
import polaris.utils.render.ui.Render2D;
import polaris.utils.repository.friend.FriendUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public final class Arrows extends Module {
    private static final String ARROW_TEXTURE = "cataclysm:textures/features/arrows.png";

    private final NumberSetting radius = register(new NumberSetting("Radius", "Distance from screen center.", 50.0, 30.0, 100.0, 1.0));
    private final NumberSetting arrowSize = register(new NumberSetting("Size", "Arrow size (px).", 16.0, 8.0, 28.0, 1.0));
    private final NumberSetting smooth = register(new NumberSetting("Smooth", "Angle smoothing.", 14.0, 4.0, 30.0, 0.5));
    private final ColorSetting arrowColor = register(new ColorSetting("Color", "Arrow color.", new Color(255, 255, 255, 255)));
    private final ColorSetting friendColor = register(new ColorSetting("Friend Color", "Friend arrow color.", new Color(70, 255, 120, 255)));
    private final BooleanSetting ignoreNaked = register(new BooleanSetting("Ignore Naked", "Hide players without armor.", true));
    private final BooleanSetting hideOnScreen = register(new BooleanSetting("Hide On-Screen", "Fade arrows for on-screen players.", true));

    private final Map<Player, ArrowState> states = new IdentityHashMap<>();
    private final SmoothAnimation radiusAnim = new SmoothAnimation();
    private long lastNs = System.nanoTime();

    public Arrows() {
        super("Arrows", "Direction arrows for nearby players.", ModuleCategory.VISUAL);
        radiusAnim.set(50.0);
    }

    @Override
    protected void onDisable() {
        states.clear();
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    private void onDraw(DrawEvent event) {
        if (mc.player == null || mc.level == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        if (event.getLayer() != DrawEvent.Layer.GAME || mc.options.hideGui) {
            return;
        }

        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        float extra = 0f;
        if (mc.screen instanceof InventoryScreen) {
            extra = 80f;
        } else if (mc.screen instanceof ContainerScreen) {
            extra = 100f;
        } else if (mc.player.isSprinting()) {
            extra = 12f;
        }
        float targetRadius = radius.getFloat() + extra;
        radiusAnim.update();
        radiusAnim.run(targetRadius, 0.15, Easings.CUBIC_OUT);
        float r = (float) radiusAnim.get();

        for (ArrowState st : states.values()) {
            st.alive = false;
        }

        float partial = event.getPartialTicks();
        float cx = mc.getWindow().getGuiScaledWidth() * 0.5f;
        float cy = mc.getWindow().getGuiScaledHeight() * 0.5f;
        float size = arrowSize.getFloat();
        float lerp = Math.min(1f, dt * smooth.getFloat());
        float playerYaw = mc.player.getYRot();

        List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());
        for (AbstractClientPlayer player : players) {
            if (player == mc.player || player.isRemoved() || !player.isAlive()) {
                continue;
            }
            if (ignoreNaked.getValue() && isNaked(player)) {
                continue;
            }

            float worldAngle = yawTo(player, partial);
            float targetAngle = Mth.wrapDegrees(worldAngle - playerYaw);

            ArrowState st = states.computeIfAbsent(player, p -> new ArrowState(targetAngle));
            st.alive = true;
            st.player = player;

            float delta = Mth.wrapDegrees(targetAngle - st.angle);
            st.angle += delta * lerp;
            st.angle = Mth.wrapDegrees(st.angle);

            
            
            float screenAngle = st.angle - 90f;
            boolean onScreen = isRoughlyInFront(player, partial);
            st.targetAlpha = hideOnScreen.getValue() && onScreen ? 0f : 1f;
            st.alpha += (st.targetAlpha - st.alpha) * Math.min(1f, dt * 12f);
            st.screenAngle = screenAngle;
        }

        Iterator<Map.Entry<Player, ArrowState>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            ArrowState st = it.next().getValue();
            if (!st.alive) {
                st.alpha += (0f - st.alpha) * Math.min(1f, dt * 10f);
                if (st.alpha <= 0.02f) {
                    it.remove();
                }
            }
        }

        for (ArrowState st : states.values()) {
            if (st.alpha <= 0.02f) {
                continue;
            }
            
            float drawAngle = st.angle;
            float rad = (float) Math.toRadians(drawAngle);
            
            float x = cx + Mth.sin(rad) * r;
            float y = cy - Mth.cos(rad) * r;

            int base = FriendUtils.isFriend(st.player)
                    ? friendColor.getValue().getRGB()
                    : arrowColor.getValue().getRGB();
            int col = ColorUtil.multAlpha(base, st.alpha);
            int glow = ColorUtil.multAlpha(base, st.alpha * 0.35f);
            float glowSize = size * 1.3f;
            
            float rot = drawAngle;
            Render2D.image(ARROW_TEXTURE, x - glowSize * 0.5f, y - glowSize * 0.5f, glowSize, 0f, rot, x, y, glow);
            Render2D.image(ARROW_TEXTURE, x - size * 0.5f, y - size * 0.5f, size, 0f, rot, x, y, col);
        }
    }

    private static float yawTo(Player entity, float partial) {
        double dx = Mth.lerp(partial, entity.xo, entity.getX()) - Mth.lerp(partial, mcPlayerXo(), mcPlayerX());
        double dz = Mth.lerp(partial, entity.zo, entity.getZ()) - Mth.lerp(partial, mcPlayerZo(), mcPlayerZ());
        return (float) (-(Math.atan2(dx, dz) * (180.0 / Math.PI)));
    }

    private static double mcPlayerX() {
        return net.minecraft.client.Minecraft.getInstance().player.getX();
    }

    private static double mcPlayerZ() {
        return net.minecraft.client.Minecraft.getInstance().player.getZ();
    }

    private static double mcPlayerXo() {
        return net.minecraft.client.Minecraft.getInstance().player.xo;
    }

    private static double mcPlayerZo() {
        return net.minecraft.client.Minecraft.getInstance().player.zo;
    }

    private boolean isRoughlyInFront(Player player, float partial) {
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 look = mc.player.getViewVector(partial);
        Vec3 to = player.getEyePosition(partial).subtract(eye).normalize();
        return look.dot(to) > 0.55;
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final class ArrowState {
        Player player;
        float angle;
        float screenAngle;
        float alpha;
        float targetAlpha = 1f;
        boolean alive;

        ArrowState(float angle) {
            this.angle = angle;
            this.alpha = 0f;
        }
    }
}
