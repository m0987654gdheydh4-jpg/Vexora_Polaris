package polaris.api.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import polaris.api.events.annotation.SubscribeEvent;
import polaris.api.events.impl.HandOffsetEvent;
import polaris.api.module.Module;
import polaris.api.module.ModuleCategory;
import polaris.api.settings.impl.BooleanSetting;
import polaris.api.settings.impl.ButtonSetting;
import polaris.api.settings.impl.NumberSetting;
import polaris.screens.handeditor.HandEditorScreen;


public final class HandTweaker extends Module {
    private static HandTweaker instance;

    private final ButtonSetting editor = register(new ButtonSetting(
            "Open Editor", "Hide the menu and place the hands by dragging them.", HandTweaker::openEditor));
    private final BooleanSetting skipCrossbow = register(new BooleanSetting(
            "Skip Crossbow", "Leave the crossbow at its vanilla position.", true));

    private final NumberSetting mainX = register(new NumberSetting("Main X", "Main hand X offset.", 0.0, -1.5, 1.5, 0.01));
    private final NumberSetting mainY = register(new NumberSetting("Main Y", "Main hand Y offset.", 0.0, -1.5, 1.5, 0.01));
    private final NumberSetting mainZ = register(new NumberSetting("Main Z", "Main hand Z offset.", 0.0, -2.5, 2.5, 0.01));
    private final NumberSetting mainScale = register(new NumberSetting("Main Scale", "Main hand scale.", 1.0, 0.3, 2.0, 0.01));
    private final NumberSetting mainPitch = register(new NumberSetting("Main Pitch", "Main hand pitch.", 0.0, -90.0, 90.0, 1.0));
    private final NumberSetting mainYaw = register(new NumberSetting("Main Yaw", "Main hand yaw.", 0.0, -90.0, 90.0, 1.0));
    private final NumberSetting mainRoll = register(new NumberSetting("Main Roll", "Main hand roll.", 0.0, -90.0, 90.0, 1.0));

    private final NumberSetting offX = register(new NumberSetting("Off X", "Off hand X offset.", 0.0, -1.5, 1.5, 0.01));
    private final NumberSetting offY = register(new NumberSetting("Off Y", "Off hand Y offset.", 0.0, -1.5, 1.5, 0.01));
    private final NumberSetting offZ = register(new NumberSetting("Off Z", "Off hand Z offset.", 0.0, -2.5, 2.5, 0.01));
    private final NumberSetting offScale = register(new NumberSetting("Off Scale", "Off hand scale.", 1.0, 0.3, 2.0, 0.01));
    private final NumberSetting offPitch = register(new NumberSetting("Off Pitch", "Off hand pitch.", 0.0, -90.0, 90.0, 1.0));
    private final NumberSetting offYaw = register(new NumberSetting("Off Yaw", "Off hand yaw.", 0.0, -90.0, 90.0, 1.0));
    private final NumberSetting offRoll = register(new NumberSetting("Off Roll", "Off hand roll.", 0.0, -90.0, 90.0, 1.0));

    public HandTweaker() {
        super("HandTweaker", "Place the first-person hands by hand.", ModuleCategory.VISUAL);
        instance = this;
    }

    public static HandTweaker getInstance() {
        return instance;
    }

    private static void openEditor() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        
        client.setScreen(new HandEditorScreen(client.screen));
    }

    

    public NumberSetting x(boolean main) {
        return main ? mainX : offX;
    }

    public NumberSetting y(boolean main) {
        return main ? mainY : offY;
    }

    public NumberSetting z(boolean main) {
        return main ? mainZ : offZ;
    }

    public NumberSetting scale(boolean main) {
        return main ? mainScale : offScale;
    }

    public NumberSetting pitch(boolean main) {
        return main ? mainPitch : offPitch;
    }

    public NumberSetting yaw(boolean main) {
        return main ? mainYaw : offYaw;
    }

    public NumberSetting roll(boolean main) {
        return main ? mainRoll : offRoll;
    }

    
    public NumberSetting[] all(boolean main) {
        return new NumberSetting[]{
                x(main), y(main), z(main), scale(main), pitch(main), yaw(main), roll(main)
        };
    }

    public void reset(boolean main) {
        for (NumberSetting setting : all(main)) {
            setting.reset();
        }
    }

    public void resetBoth() {
        reset(true);
        reset(false);
    }

    

    
    private static final float ARM_BASE_X = 0.56f;
    private static final float ARM_BASE_Y = -0.52f;
    private static final float ARM_BASE_Z = -0.72f;
    
    private static final long CAPTURE_TTL_MS = 250L;

    private static final Matrix4f[] handPose = {new Matrix4f(), new Matrix4f()};
    private static final long[] handCapturedAt = new long[2];
    private static final Matrix4f projection = new Matrix4f();
    private static long projectionCapturedAt;

    
    public static void captureProjection(Matrix4f matrix) {
        if (matrix == null) {
            return;
        }
        projection.set(matrix);
        projectionCapturedAt = System.currentTimeMillis();
    }

    private static void captureHand(boolean main, Matrix4f pose) {
        int index = main ? 0 : 1;
        handPose[index].set(pose);
        handCapturedAt[index] = System.currentTimeMillis();
    }

    
    public float[] projectHand(boolean main, float screenWidth, float screenHeight) {
        int index = main ? 0 : 1;
        long now = System.currentTimeMillis();
        if (now - handCapturedAt[index] > CAPTURE_TTL_MS || now - projectionCapturedAt > CAPTURE_TTL_MS) {
            return null;
        }

        float baseX = (float) (double) x(main).getValue() + (main ? ARM_BASE_X : -ARM_BASE_X);
        float baseY = (float) (double) y(main).getValue() + ARM_BASE_Y;
        float baseZ = (float) (double) z(main).getValue() + ARM_BASE_Z;

        float[] anchor = project(index, baseX, baseY, baseZ, screenWidth, screenHeight);
        if (anchor == null) {
            return null;
        }
        
        float[] alongX = project(index, baseX + 1.0f, baseY, baseZ, screenWidth, screenHeight);
        float[] alongY = project(index, baseX, baseY + 1.0f, baseZ, screenWidth, screenHeight);
        if (alongX == null || alongY == null) {
            return null;
        }

        float perX = alongX[0] - anchor[0];
        float perY = alongY[1] - anchor[1];
        if (Math.abs(perX) < 1.0e-3f || Math.abs(perY) < 1.0e-3f) {
            return null;
        }
        return new float[]{anchor[0], anchor[1], perX, perY};
    }

    private static float[] project(int index, float x, float y, float z, float screenWidth, float screenHeight) {
        Vector4f point = new Vector4f(x, y, z, 1.0f);
        handPose[index].transform(point);
        projection.transform(point);
        if (point.w <= 1.0e-4f) {
            return null;
        }
        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;
        return new float[]{
                (ndcX * 0.5f + 0.5f) * screenWidth,
                (1.0f - (ndcY * 0.5f + 0.5f)) * screenHeight
        };
    }

    

    @SubscribeEvent
    private void onHandOffset(HandOffsetEvent event) {
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        if (main && skipCrossbow.getValue() && event.getStack().getItem() instanceof CrossbowItem) {
            return;
        }

        PoseStack matrices = event.getMatrices();
        
        
        captureHand(main, matrices.last().pose());
        matrices.translate(x(main).getValue(), y(main).getValue(), z(main).getValue());

        float pitchValue = pitch(main).getFloat();
        float yawValue = yaw(main).getFloat();
        float rollValue = roll(main).getFloat();
        if (pitchValue != 0.0f) {
            matrices.mulPose(Axis.XP.rotationDegrees(pitchValue));
        }
        if (yawValue != 0.0f) {
            matrices.mulPose(Axis.YP.rotationDegrees(yawValue));
        }
        if (rollValue != 0.0f) {
            matrices.mulPose(Axis.ZP.rotationDegrees(rollValue));
        }

        float scaleValue = scale(main).getFloat();
        if (scaleValue != 1.0f) {
            event.setScale(scaleValue);
        }
    }
}
