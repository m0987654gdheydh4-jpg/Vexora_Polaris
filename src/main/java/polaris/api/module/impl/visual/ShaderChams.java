package polaris.api.module.impl.visual;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.ModeSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.post.shaderchams.ShaderChamsRenderer;

import java.awt.Color;


public final class ShaderChams extends Module {
    private static ShaderChams instance;

    
    private static final String[] MODES = {
            "Glow", "Outline", "Neon", "Rainbow", "Ghost", "Pulse",
            "Glass", "Fill", "Gradient", "Fresnel"
    };

    
    private static final int MASK_PLAYER = 0xFFFF0000;
    private static final int MASK_MOB = 0xFF00FF00;
    private static final int MASK_OTHER = 0xFF0000FF;

    
    
    
    private final ModeSetting mode = register(new ModeSetting(
            "Mode", "Chams shader style.", "Fill", MODES));
    private final BooleanSetting players = register(new BooleanSetting(
            "Players", "Apply chams to other players.", true));
    private final BooleanSetting mobs = register(new BooleanSetting(
            "Mobs", "Apply chams to mobs and animals.", true));
    private final BooleanSetting others = register(new BooleanSetting(
            "Other Entities", "Apply chams to items, arrows, crystals, etc.", false));
    private final BooleanSetting self = register(new BooleanSetting(
            "Self", "Apply chams to yourself in third person.", false));
    private final ColorSetting playerColor = register(new ColorSetting(
            "Player Color", "Chams color for players.", new Color(120, 60, 255, 255)));
    private final ColorSetting mobColor = register(new ColorSetting(
            "Mob Color", "Chams color for mobs.", new Color(60, 255, 170, 255)));
    private final ColorSetting otherColor = register(new ColorSetting(
            "Other Color", "Chams color for other entities.", new Color(255, 200, 60, 255)));
    private final NumberSetting outlineWidth = register(new NumberSetting(
            "Outline Width", "Outline thickness in pixels.", 2.0, 1.0, 6.0, 0.5));
    private final NumberSetting glowRadius = register(new NumberSetting(
            "Glow Radius", "Halo radius in pixels.", 16.0, 4.0, 48.0, 1.0));
    private final NumberSetting glowStrength = register(new NumberSetting(
            "Glow Strength", "Halo brightness %.", 100.0, 10.0, 200.0, 5.0));
    private final NumberSetting fillOpacity = register(new NumberSetting(
            "Fill Opacity", "Silhouette fill opacity %.", 100.0, 0.0, 100.0, 5.0));
    private final NumberSetting speed = register(new NumberSetting(
            "Speed", "Animation speed.", 1.0, 0.0, 5.0, 0.1));

    public ShaderChams() {
        super("ShaderChams", "Shader chams through walls: Glow / Outline / Neon / Rainbow / "
                + "Ghost / Pulse / Glass / Fill / Gradient / Fresnel.", ModuleCategory.VISUAL);
        instance = this;
        playerColor.visibleWhen(() -> !mode.is("Rainbow") && players.getValue());
        mobColor.visibleWhen(() -> !mode.is("Rainbow") && mobs.getValue());
        otherColor.visibleWhen(() -> !mode.is("Rainbow") && others.getValue());
        
        
        outlineWidth.visibleWhen(() -> !mode.is("Glow") && !mode.is("Pulse") && !mode.is("Fill"));
        glowRadius.visibleWhen(() -> !mode.is("Outline") && !mode.is("Fill"));
        glowStrength.visibleWhen(() -> !mode.is("Outline") && !mode.is("Fill"));
        fillOpacity.visibleWhen(() -> !mode.is("Fill"));
        speed.visibleWhen(this::isAnimated);
    }

    private boolean isAnimated() {
        return mode.is("Rainbow") || mode.is("Ghost") || mode.is("Pulse");
    }

    public static ShaderChams getInstance() {
        return instance;
    }

    public static boolean isActive() {
        ShaderChams chams = instance;
        return chams != null && chams.isEnabled() && !ShaderChamsRenderer.isDisabledAfterError();
    }

    
    public static boolean shouldForceGlow(Entity entity) {
        ShaderChams chams = instance;
        if (chams == null || !chams.isEnabled() || entity == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        if (entity == client.player) {
            return chams.self.getValue() && !client.options.getCameraType().isFirstPerson();
        }
        if (entity instanceof Player) {
            return chams.players.getValue();
        }
        if (entity instanceof LivingEntity) {
            return chams.mobs.getValue();
        }
        return chams.others.getValue();
    }

    
    public static int maskColor(Entity entity) {
        if (!shouldForceGlow(entity)) {
            return 0;
        }
        if (entity instanceof Player) {
            return MASK_PLAYER;
        }
        if (entity instanceof LivingEntity) {
            return MASK_MOB;
        }
        return MASK_OTHER;
    }

    @Override
    protected void onDisable() {
        ShaderChamsRenderer.clear();
    }

    
    public void onComposite(RenderTarget main, RenderTarget outlineMask) {
        if (main == null || outlineMask == null || mc.player == null || mc.level == null) {
            return;
        }
        int modeId = 0;
        for (int i = 0; i < MODES.length; i++) {
            if (mode.is(MODES[i])) {
                modeId = i;
                break;
            }
        }
        ShaderChamsRenderer.apply(
                main,
                outlineMask,
                modeId,
                isAnimated() ? speed.getFloat() : 1f,
                playerColor.getValue().getRGB(),
                mobColor.getValue().getRGB(),
                otherColor.getValue().getRGB(),
                outlineWidth.getFloat(),
                glowRadius.getFloat(),
                glowStrength.getFloat() / 100f,
                fillOpacity.getFloat() / 100f,
                0.35f
        );
    }
}
