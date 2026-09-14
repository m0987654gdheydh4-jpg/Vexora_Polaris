package polaris.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.screens.mainmenu.CustomMultiplayerScreen;
import polaris.screens.mainmenu.MainMenuScreen;
import polaris.screens.mainmenu.loading.LoadingScreen;
import polaris.utils.window.WindowStyle;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$shaderChamsGlow(net.minecraft.world.entity.Entity entity,
                                           org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (polaris.api.module.impl.visual.ShaderChams.shouldForceGlow(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "resizeDisplay", at = @At("TAIL"))
    private void applyDarkMode(CallbackInfo ci) {
        applyWindowTitle();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow() != null) {
            WindowStyle.setDarkMode(client.getWindow().handle(), true);
        }
    }

    @Inject(method = "updateTitle", at = @At("TAIL"))
    private void applyCustomTitle(CallbackInfo ci) {
        applyWindowTitle();
    }

    
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void polaris$replaceTitleScreen(Screen screen, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;

        
        if (screen instanceof TitleScreen
                && !(screen instanceof MainMenuScreen)
                && !(screen instanceof LoadingScreen)) {
            if (!LoadingScreen.COMPLETED) {
                self.setScreen(new LoadingScreen());
            } else {
                self.setScreen(new MainMenuScreen());
            }
            ci.cancel();
            return;
        }

        
        if (screen instanceof JoinMultiplayerScreen && !(screen instanceof CustomMultiplayerScreen)) {
            Screen parent;
            try {
                parent = self.screen instanceof MainMenuScreen || self.screen instanceof CustomMultiplayerScreen
                        ? self.screen
                        : new MainMenuScreen();
            } catch (Throwable ignored) {
                parent = new MainMenuScreen();
            }
            self.setScreen(new CustomMultiplayerScreen(parent != null ? parent : new MainMenuScreen()));
            ci.cancel();
        }
    }

    private static void applyWindowTitle() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        String user = client.getUser() == null ? "User" : client.getUser().getName();
        if (user == null || user.isBlank()) {
            user = "User";
        }
        client.getWindow().setTitle("Vexora Client - 1.21.11");
    }
}
