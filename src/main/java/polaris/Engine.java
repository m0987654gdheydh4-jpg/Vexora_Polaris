package polaris;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import polaris.manager.Manager;
import polaris.protect.NativeGuard;
import polaris.utils.render.ui.Render2D;
import polaris.utils.render.ui.gif.MainMenuGifPreloader;
import polaris.utils.sounds.SoundManager;

public class Engine implements ModInitializer, ClientModInitializer {

    private final Manager manager = new Manager();

    @Override
    public void onInitialize() {


        NativeGuard.init();
        SoundManager.init();
        Render2D.init();
        MainMenuGifPreloader.init();
    }

    @Override
    public void onInitializeClient() {

        NativeGuard.allowSensitive();
        manager.initClient();
    }
}


