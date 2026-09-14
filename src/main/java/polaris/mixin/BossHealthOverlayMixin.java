package polaris.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.drag.impl.DynamicIsland;
import polaris.mixin.accessor.BossHealthOverlayAccessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Unique
    private final List<Map.Entry<UUID, LerpingBossEvent>> cataclysm$hidden = new ArrayList<>();

    @Unique
    private boolean cataclysm$pushed;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cataclysm$beforeBossBars(GuiGraphics graphics, CallbackInfo ci) {
        cataclysm$restoreHidden();
        cataclysm$pushed = false;
        if (!DynamicIsland.isLive()) {
            return;
        }

        try {
            Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) (Object) this).cataclysm$getEvents();
            if (events != null && !events.isEmpty()) {
                Iterator<Map.Entry<UUID, LerpingBossEvent>> it = events.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, LerpingBossEvent> entry = it.next();
                    if (DynamicIsland.isIntegratedBossbar(entry.getValue())) {
                        cataclysm$hidden.add(Map.entry(entry.getKey(), entry.getValue()));
                        it.remove();
                    }
                }
            }

            float offset = DynamicIsland.bossbarPushDown();
            if (offset > 0.5f) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(0f, offset);
                cataclysm$pushed = true;
            }
        } catch (Throwable throwable) {
            
            cataclysm$restoreHidden();
            cataclysm$pushed = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void cataclysm$afterBossBars(GuiGraphics graphics, CallbackInfo ci) {
        if (cataclysm$pushed) {
            cataclysm$pushed = false;
            try {
                graphics.pose().popMatrix();
            } catch (Throwable ignored) {
            }
        }
        cataclysm$restoreHidden();
    }

    @Unique
    private void cataclysm$restoreHidden() {
        if (cataclysm$hidden.isEmpty()) {
            return;
        }
        try {
            Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) (Object) this).cataclysm$getEvents();
            if (events != null) {
                for (Map.Entry<UUID, LerpingBossEvent> entry : cataclysm$hidden) {
                    events.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable ignored) {
        }
        cataclysm$hidden.clear();
    }
}
