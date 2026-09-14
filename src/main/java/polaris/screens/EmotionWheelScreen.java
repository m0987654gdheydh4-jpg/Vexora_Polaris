package polaris.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import polaris.emotions.EmotionWheelManager;


public class EmotionWheelScreen extends Screen {
    public EmotionWheelScreen(EmotionWheelManager manager) {
        super(Component.empty());
        if (manager != null) {
            manager.openFromModule();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
