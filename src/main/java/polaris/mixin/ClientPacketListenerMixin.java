package polaris.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.ChatEvent;
import polaris.api.events.impl.PacketEvent;
import polaris.manager.Manager;
import polaris.utils.network.Network;
import polaris.utils.sounds.SoundManager;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onSendChat(String message, CallbackInfo ci) {
        ChatEvent event = Manager.postEvent(new ChatEvent(message));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        Network.handleTimePacket();
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$onSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        
        if (!mc.isSameThread()) {
            return;
        }
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        if (mc.player == null) return;
        String msg = packet.content().getString();
        String name = mc.player.getName().getString();
        if (name.isEmpty()) return;
        int idx = msg.toLowerCase().indexOf(name.toLowerCase());
        if (idx < 0) return;

        SoundManager.playSoundDirect(SoundManager.ACCOUNTSWITCH, 1.0f, 1.0f);

        String before = msg.substring(0, idx);
        String namePart = msg.substring(idx, idx + name.length());
        String after = msg.substring(idx + name.length());

        MutableComponent colored = Component.empty();
        if (!before.isEmpty()) colored.append(Component.literal(before));
        colored.append(Component.literal(namePart).withStyle(ChatFormatting.GOLD));
        if (!after.isEmpty()) colored.append(Component.literal(after));

        ci.cancel();
        if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addMessage(colored);
        }
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onPlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onGameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void cataclysm$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.RECEIVE, packet));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}

