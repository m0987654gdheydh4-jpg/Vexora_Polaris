package polaris.api.module.impl.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;

public class ElytraRecast extends Module {

    private final ModeSetting exploit = register(new ModeSetting("Exploit", "Elytra exploit mode.", "None", "None", "Strict", "Strong"));
    private final BooleanSetting changePitch = register(new BooleanSetting("Change Pitch", "Automatically changes pitch.", true));
    private final NumberSetting pitchValue = register(new NumberSetting("Pitch Value", "Pitch angle to maintain.", 55.0, -90.0, 90.0, 1.0));
    private final BooleanSetting autoWalk = register(new BooleanSetting("Auto Walk", "Automatically presses forward.", true));
    private final BooleanSetting autoJump = register(new BooleanSetting("Auto Jump", "Automatically presses jump.", true));
    private final BooleanSetting allowBroken = register(new BooleanSetting("Allow Broken", "Allows the use of broken elytra.", true));

    private final Minecraft mc = Minecraft.getInstance();
    private float jitter;
    private final long initTime = System.currentTimeMillis();

    public ElytraRecast() {
        super("Elytra Recast", "Auto re-cast elytra with exploit features.", ModuleCategory.MOVEMENT);
        pitchValue.visibleWhen(() -> changePitch.getValue());
    }

    @Override
    protected void onDisable() {
        if (mc.options == null) return;
        // Отжимаем клавиши при выключении
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    @SubscribeEvent
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // Авто-ходьба и авто-прыжок
        if (autoJump.getValue()) mc.options.keyJump.setDown(true);
        if (autoWalk.getValue()) mc.options.keyUp.setDown(true);

        // Проверка для авто-рекаста при падении
        if (!mc.player.isFallFlying() && mc.player.fallDistance > 0.5f) {
            if (checkElytra()) {
                castElytra();
            }
        }

        // Расчет джиттера
        jitter = (float) (20 * Math.sin((System.currentTimeMillis() - initTime) / 50f));

        // Обход углов (ReallyWorld и подобные АЧ)
        if (changePitch.getValue()) {
            mc.player.setXRot(pitchValue.getValue().floatValue());

            if (exploit.is("Strict")) {
                mc.player.setYRot(mc.player.getYRot() + jitter);
            } else if (exploit.is("Strong")) {
                mc.player.setXRot(pitchValue.getValue().floatValue() - Math.abs(jitter / 2f));
            }
        }
    }

    private boolean castElytra() {
        if (mc.player.isInWater() || mc.player.hasEffect(MobEffects.LEVITATION)) {
            return false;
        }

        if (mc.player.connection != null) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            mc.player.startFallFlying(); // Синхронизация на клиенте
            return true;
        }
        return false;
    }

    private boolean checkElytra() {
        // Проверяем наличие элитр и их состояние
        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chestStack.getItem() == Items.ELYTRA) {
            if (allowBroken.getValue()) {
                return true;
            } else {
                // Элитры работают, пока урон меньше максимума
                return chestStack.getDamageValue() < chestStack.getMaxDamage();
            }
        }
        return false;
    }
}