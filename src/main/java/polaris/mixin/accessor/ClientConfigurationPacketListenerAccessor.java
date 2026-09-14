package polaris.mixin.accessor;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.KnownPacksManager;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public interface ClientConfigurationPacketListenerAccessor {
    @Accessor("registryDataCollector")
    RegistryDataCollector cataclysm$getRegistryDataCollector();

    @Accessor("receivedRegistries")
    RegistryAccess.Frozen cataclysm$getReceivedRegistries();

    @Accessor("enabledFeatures")
    FeatureFlagSet cataclysm$getEnabledFeatures();

    @Accessor("knownPacks")
    KnownPacksManager cataclysm$getKnownPacks();

    @Accessor("chatState")
    ChatComponent.State cataclysm$getChatState();
}

