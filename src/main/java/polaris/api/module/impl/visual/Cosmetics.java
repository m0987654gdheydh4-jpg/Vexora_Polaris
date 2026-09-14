package polaris.api.module.impl.visual;

import net.minecraft.client.Minecraft;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.api.events.impl.TickEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.module.impl.visual.cosmetics.DashTrailEffect;
import polaris.api.module.impl.visual.cosmetics.JumpCircleEffect;
import polaris.api.module.impl.visual.cosmetics.TotemAngelEffect;
import polaris.api.module.impl.visual.cosmetics.TrailEffect;
import polaris.api.module.impl.visual.cosmetics.WingsEffect;
import polaris.api.settings.Setting;

import java.util.List;


public final class Cosmetics extends Module {
    private final JumpCircleEffect jumpCircle = new JumpCircleEffect();
    private final TrailEffect trail = new TrailEffect();
    private final DashTrailEffect dashTrail = new DashTrailEffect();
    private final WingsEffect wings = new WingsEffect();
    private final TotemAngelEffect totemAngel = new TotemAngelEffect();

    public Cosmetics() {
        super("Cosmetics", "Jump circle, trails, dash trail, wings and totem angel.",
                ModuleCategory.VISUAL);
        registerAll(jumpCircle.settings());
        registerAll(trail.settings());
        registerAll(dashTrail.settings());
        registerAll(wings.settings());
        registerAll(totemAngel.settings());
    }

    private void registerAll(List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            register(setting);
        }
    }

    @Override
    protected void onEnable() {
        jumpCircle.onEnable();
    }

    @Override
    protected void onDisable() {
        jumpCircle.onDisable();
        trail.onDisable();
        dashTrail.onDisable();
        wings.onDisable();
        totemAngel.onDisable();
    }

    @Override
    public void onTick(Minecraft client) {
        if (dashTrail.isOn()) {
            dashTrail.onTick(client);
        }
    }

    @SubscribeEvent
    private void onTickPost(TickEvent.Post event) {
        if (jumpCircle.isOn()) {
            jumpCircle.onTick(event);
        }
        if (trail.isOn()) {
            trail.onTick(event);
        }
    }

    @SubscribeEvent
    private void onPacket(PacketEvent event) {
        if (totemAngel.isOn()) {
            totemAngel.onPacket(event);
        }
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (jumpCircle.isOn()) {
            jumpCircle.onWorldRender(event);
        }
        if (trail.isOn()) {
            trail.onWorldRender(event);
        }
        if (dashTrail.isOn()) {
            dashTrail.onWorldRender(event);
        }
        if (wings.isOn()) {
            wings.onWorldRender(event);
        }
        if (totemAngel.isOn()) {
            totemAngel.onWorldRender(event);
        }
    }
}
