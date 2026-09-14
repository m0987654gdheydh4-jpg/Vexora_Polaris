package polaris.mixin;

import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;
import polaris.api.events.impl.ChunkOcclusionEvent;
import polaris.manager.Manager;

@Mixin(SectionOcclusionGraph.class)
public abstract class SectionOcclusionGraphMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean cataclysm$disableAdvancedCulling(boolean advancedCulling) {
        ChunkOcclusionEvent event = Manager.postEvent(new ChunkOcclusionEvent());
        return event.isCancelled() ? false : advancedCulling;
    }
}

