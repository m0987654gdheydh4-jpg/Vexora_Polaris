package polaris.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public class MixinDisconnectedScreen extends Screen {

    protected MixinDisconnectedScreen(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addReconnectButton(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();

        if (client.getConnection() == null || client.getConnection().getServerData() == null) {
            return;
        }

        final ServerData lastServerData = client.getConnection().getServerData();

        int buttonWidth = 150;
        int buttonHeight = 20;

        int x = 10;
        int y = this.height - buttonHeight - 10;

        Button reconnectButton = Button.builder(Component.literal("Reconnect"), button -> {
            if (this.minecraft != null) {
                ServerAddress address = ServerAddress.parseString(lastServerData.ip);

                ConnectScreen.startConnecting(
                        new JoinMultiplayerScreen(new TitleScreen()),
                        this.minecraft,
                        address,
                        lastServerData,
                        false,
                        null
                );
            }
        }).bounds(x, y, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(reconnectButton);
    }
}
