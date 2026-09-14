package polaris.api.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.api.settings.bind.KeyBind;
import polaris.api.settings.impl.BindSetting;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.RotationUpdateEvent;

import java.util.Comparator;
import java.util.List;

public class FireballHelper extends Module {

    private final BooleanSetting autoDeflect = register(new BooleanSetting("Auto Deflect", "Automatically hits incoming fireballs.", true));
    private final BooleanSetting autoAim = register(new BooleanSetting("Auto Aim", "Aims at the nearest visible player when deflecting.", true));
    private final NumberSetting cps = register(new NumberSetting("CPS", "Clicks per second for deflecting.", 10.0, 1.0, 100.0, 1.0));
    private final NumberSetting range = register(new NumberSetting("Range", "Distance to hit the fireball.", 3.5, 1.0, 6.0, 0.1));

    private final BooleanSetting autoThrow = register(new BooleanSetting("Auto Throw", "Enables throw-on-bind function.", true));
    private final BindSetting bindOption = register(new BindSetting("Throw Bind", "Key to look at target and throw fireball.", KeyBind.keyboard(GLFW.GLFW_KEY_UNKNOWN)));

    private final Minecraft mc = Minecraft.getInstance();
    private long lastHitTime = System.currentTimeMillis();
    private long lastThrowTime = System.currentTimeMillis();

    public FireballHelper() {
        super("Fireball Helper", "Helps you deflect fireballs and aim them at targets.", ModuleCategory.COMBAT);
    }

    @SubscribeEvent
    private void onUpdate(RotationUpdateEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getWindow() == null) return;

        // --- ЛОГИКА 1: АВТО-БРОСОК ПО НАЖАТИЮ КНОПКИ ---
        boolean isBindPressed = bindOption.getValue().isDown(mc.getWindow().handle());

        if (autoThrow.getValue() && isBindPressed) {
            int fireballSlot = findFireballInHotbar();

            if (fireballSlot != -1) {
                Player throwTarget = getBestTarget();

                if (throwTarget != null) {
                    float[] rotations = calculateRotations(throwTarget);

                    // Устанавливаем ротации напрямую игроку, обходя ивент
                    mc.player.setYRot(rotations[0]);
                    mc.player.setXRot(rotations[1]);

                    // Переключаем слот хотбара на файербол
                    mc.player.getInventory().setSelectedSlot(fireballSlot);

                    // Бросаем файербол (задержка 200 миллисекунд)
                    if (System.currentTimeMillis() - lastThrowTime >= 200) {
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        lastThrowTime = System.currentTimeMillis();
                    }
                    return;
                }
            }
        }

        // --- ЛОГИКА 2: АВТО-ОТБИВАНИЕ СНАРЯДОВ ---
        Entity fireball = mc.level.getEntitiesOfClass(Entity.class,
                mc.player.getBoundingBox().inflate(range.getValue().doubleValue()),
                entity -> {
                    String name = entity.getType().getDescriptionId().toLowerCase();
                    return name.contains("fireball") || name.contains("dragon");
                }
        ).stream().min(Comparator.comparingDouble(e -> mc.player.distanceToSqr(e))).orElse(null);

        if (fireball == null) return;

        if (autoAim.getValue()) {
            Player targetPlayer = getBestTarget();
            if (targetPlayer != null) {
                float[] rotations = calculateRotations(targetPlayer);

                // Устанавливаем ротации напрямую игроку при авто-наводке
                mc.player.setYRot(rotations[0]);
                mc.player.setXRot(rotations[1]);
            }
        }

        if (autoDeflect.getValue()) {
            long delay = (long) (1000.0 / cps.getValue().doubleValue());
            if (System.currentTimeMillis() - lastHitTime >= delay) {
                mc.gameMode.attack(mc.player, fireball);
                mc.player.swing(InteractionHand.MAIN_HAND);
                lastHitTime = System.currentTimeMillis();
            }
        }
    }

    private int findFireballInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.FIRE_CHARGE)) {
                return i;
            }
        }
        return -1;
    }

    private Player getBestTarget() {
        if (mc.level == null || mc.player == null) return null;

        List<? extends Player> players = mc.level.players();
        Player bestTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (Player player : players) {
            if (player == mc.player || !player.isAlive()) continue;
            if (!mc.player.hasLineOfSight(player)) continue;

            double dist = mc.player.distanceToSqr(player);
            if (dist < closestDist) {
                closestDist = dist;
                bestTarget = player;
            }
        }
        return bestTarget;
    }

    private float[] calculateRotations(Entity target) {
        if (mc.player == null) return new float[]{0, 0};

        double diffX = target.getX() - mc.player.getX();
        double diffY = (target.getY() + target.getEyeHeight() / 2.0) - (mc.player.getY() + mc.player.getEyeHeight());
        double diffZ = target.getZ() - mc.player.getZ();

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, diffXZ) * 180.0 / Math.PI);

        return new float[]{
                mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()),
                mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot())
        };
    }
}
