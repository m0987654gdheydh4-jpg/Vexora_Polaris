package polaris.mixin.accessor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
    @Accessor("level")
    void cataclysm$setLevel(ClientLevel level);

    @Accessor("levelData")
    void cataclysm$setLevelData(ClientLevel.ClientLevelData levelData);

    @Accessor("levelData")
    ClientLevel.ClientLevelData cataclysm$getLevelData();

    @Accessor("serverChunkRadius")
    void cataclysm$setServerChunkRadius(int serverChunkRadius);

    @Accessor("serverChunkRadius")
    int cataclysm$getServerChunkRadius();

    @Accessor("serverSimulationDistance")
    void cataclysm$setServerSimulationDistance(int serverSimulationDistance);

    @Accessor("serverSimulationDistance")
    int cataclysm$getServerSimulationDistance();

    @Accessor("levels")
    void cataclysm$setLevels(Set<ResourceKey<Level>> levels);
}

