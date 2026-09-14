package polaris.api.module.impl.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.WorldRenderEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ColorSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.utils.render.world.blockoutline.BlockOutline3D;

import java.awt.Color;
import java.util.List;

public final class BlockOutline extends Module {
    private static BlockOutline instance;

    private final NumberSetting opacity = register(new NumberSetting("Opacity", "Outline shader opacity.", 0.85, 0.1, 1.0, 0.05));
    private final NumberSetting speed = register(new NumberSetting("Speed", "Shader animation speed.", 1.0, 0.0, 5.0, 0.1));
    private final NumberSetting inflate = register(new NumberSetting("Inflate", "How much to expand the outline.", 0.01, 0.0, 0.1, 0.005));
    private final BooleanSetting customColor = register(new BooleanSetting("Custom Color", "Tint the shader with a custom color.", false));
    private final ColorSetting tintColor = register(new ColorSetting("Color", "Outline tint color.", new Color(64, 224, 208, 255)));
    private final NumberSetting tintAmount = register(new NumberSetting("Tint Amount", "How strongly the tint replaces the original palette.", 1.0, 0.0, 1.0, 0.05));

    public BlockOutline() {
        super("Block Outline", "Draws a shader-powered outline on the targeted block.", ModuleCategory.VISUAL);
        tintColor.visibleWhen(customColor::getValue);
        tintAmount.visibleWhen(customColor::getValue);
        instance = this;
    }

    public static BlockOutline getInstance() {
        return instance;
    }

    public boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit) || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        VoxelShape shape = state.getShape(mc.level, pos);
        if (shape.isEmpty()) {
            return;
        }

        float grow = inflate.getFloat();
        List<AABB> boxes = shape.toAabbs();

        BlockOutline3D.begin();
        for (AABB local : boxes) {
            AABB worldBox = local.move(pos.getX(), pos.getY(), pos.getZ()).inflate(grow);
            BlockOutline3D.box(worldBox);
        }
        float amount = customColor.getValue() ? tintAmount.getFloat() : 0.0f;
        BlockOutline3D.end(opacity.getFloat(), speed.getFloat(), tintColor.getValue().getRGB(), amount);
    }
}

