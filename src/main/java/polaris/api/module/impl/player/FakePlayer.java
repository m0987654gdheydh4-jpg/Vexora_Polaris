package polaris.api.module.impl.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.AttackEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;

import java.util.UUID;


public class FakePlayer extends Module {
    private static final int FAKE_PLAYER_ENTITY_ID = -1337001;
    private static final UUID FAKE_UUID = UUID.fromString("66123666-6666-6666-6666-666666666600");
    private static final float HIT_DAMAGE = 1.0f;

    private RemotePlayer fake;
    private ClientLevel lastWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    public FakePlayer() {
        super("FakePlayer", "Spawns a fake player for practice.", ModuleCategory.PLAYER);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        removeFake(mc);

        String name = resolveName(mc) + "_fake";
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }

        GameProfile profile = new GameProfile(FAKE_UUID, name);
        fake = new RemotePlayer(mc.level, profile);
        fake.setId(FAKE_PLAYER_ENTITY_ID);

        spawnX = mc.player.getX();
        spawnY = mc.player.getY();
        spawnZ = mc.player.getZ();
        spawnYaw = mc.player.getYRot();
        spawnPitch = mc.player.getXRot();

        fake.copyPosition(mc.player);
        fake.setPos(spawnX, spawnY, spawnZ);
        fake.setYRot(spawnYaw);
        fake.setXRot(spawnPitch);
        fake.setYHeadRot(spawnYaw);
        fake.setYBodyRot(spawnYaw);
        fake.setHealth(fake.getMaxHealth());
        fake.setAbsorptionAmount(4.0f);
        fake.setNoGravity(true);
        fake.setDeltaMovement(Vec3.ZERO);
        fake.setCustomNameVisible(false);
        fake.setPose(mc.player.getPose());
        fake.invulnerableTime = 0;
        fake.hurtTime = 0;
        fake.hurtDuration = 0;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            fake.getInventory().setItem(i, mc.player.getInventory().getItem(i).copy());
        }
        fake.getInventory().setSelectedSlot(mc.player.getInventory().getSelectedSlot());
        fake.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        mc.level.addEntity(fake);
        lastWorld = mc.level;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        removeFake(Minecraft.getInstance());
        super.onDisable();
    }

    @SubscribeEvent
    public void onTick(TickEvent.Post event) {
        Minecraft mc = event.getClient();
        if (mc.player == null || mc.level == null || fake == null) {
            setEnabled(false);
            return;
        }
        if (mc.level != lastWorld || fake.isRemoved()) {
            setEnabled(false);
            return;
        }

        
        fake.setPos(spawnX, spawnY, spawnZ);
        fake.xo = spawnX;
        fake.yo = spawnY;
        fake.zo = spawnZ;
        fake.setYRot(spawnYaw);
        fake.setXRot(spawnPitch);
        fake.setYHeadRot(spawnYaw);
        fake.setYBodyRot(spawnYaw);
        fake.setDeltaMovement(Vec3.ZERO);
        fake.setNoGravity(true);

        if (fake.hurtTime > 0) {
            fake.hurtTime--;
        }
        if (fake.invulnerableTime > 0) {
            fake.invulnerableTime--;
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEvent event) {
        if (fake == null || event.getTarget() == null) {
            return;
        }
        if (event.getTarget() != fake && event.getTarget().getId() != fake.getId()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        
        if (fake.invulnerableTime > 0 || fake.hurtTime > 0) {
            return;
        }

        float damage = HIT_DAMAGE;
        if (mc.player.fallDistance > 0.0F && !mc.player.onGround()) {
            damage += 1.0f; 
        }

        float absorption = fake.getAbsorptionAmount();
        float remaining = damage;
        if (absorption > 0.0f) {
            float used = Math.min(absorption, remaining);
            fake.setAbsorptionAmount(absorption - used);
            remaining -= used;
        }
        if (remaining > 0.0f) {
            fake.setHealth(Math.max(0.0f, fake.getHealth() - remaining));
        }

        float hurtDir = (float) (Mth.atan2(mc.player.getZ() - fake.getZ(), mc.player.getX() - fake.getX())
                * (180.0D / Math.PI) - fake.getYRot());
        fake.animateHurt(hurtDir);
        fake.hurtTime = 10;
        fake.hurtDuration = 10;
        fake.invulnerableTime = 10;
        fake.hurtMarked = true;

        boolean crit = mc.player.fallDistance > 0.0F && !mc.player.onGround();
        mc.level.playLocalSound(
                fake.getX(), fake.getY(), fake.getZ(),
                crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0f, 1.0f, false
        );
        mc.level.playLocalSound(
                fake.getX(), fake.getY(), fake.getZ(),
                SoundEvents.PLAYER_HURT,
                SoundSource.PLAYERS, 1.0f, 1.0f, false
        );

        if (fake.getHealth() <= 0.0f) {
            
            fake.setHealth(fake.getMaxHealth());
            fake.setAbsorptionAmount(4.0f);
            fake.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            fake.handleEntityEvent((byte) 35);
            mc.level.playLocalSound(
                    fake.getX(), fake.getY(), fake.getZ(),
                    SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS, 1.0f, 1.0f, false
            );
            fake.invulnerableTime = 20;
        }
    }

    public boolean isFakeEntity(Entity entity) {
        return entity != null && fake != null && entity.getId() == fake.getId();
    }

    private void removeFake(Minecraft client) {
        if (client != null && client.level != null) {
            client.level.removeEntity(FAKE_PLAYER_ENTITY_ID, Entity.RemovalReason.DISCARDED);
            if (fake != null && fake.getId() != FAKE_PLAYER_ENTITY_ID) {
                client.level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        if (fake != null) {
            fake.discard();
        }
        fake = null;
        lastWorld = null;
    }

    private static String resolveName(Minecraft mc) {
        if (mc.player != null) {
            GameProfile profile = mc.player.getGameProfile();
            try {
                Object value = GameProfile.class.getMethod("name").invoke(profile);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
            try {
                Object value = GameProfile.class.getMethod("getName").invoke(profile);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        try {
            if (mc.getUser() != null && mc.getUser().getName() != null && !mc.getUser().getName().isBlank()) {
                return mc.getUser().getName();
            }
        } catch (Throwable ignored) {
        }
        return "Player";
    }
}
