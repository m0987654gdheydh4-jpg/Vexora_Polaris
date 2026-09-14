package polaris.mixin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import polaris.api.events.impl.PacketEvent;
import polaris.manager.Manager;
import polaris.utils.proxy.ProxyData;
import polaris.utils.proxy.ProxyManager;
import polaris.utils.proxy.ProxyRelay;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void cataclysm$onSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        try {
            PacketEvent event = Manager.postEvent(new PacketEvent(PacketEvent.Type.SEND, packet));
            if (event.isCancelled()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    @Redirect(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;"
            )
    )
    private static ChannelFuture cataclysm$redirectThroughProxy(Bootstrap bootstrap, InetAddress address, int port) {
        ProxyData proxy = ProxyManager.getInstance().getActiveProxy();
        System.out.println("[Polaris] Connection.connect intercepted -> "
                + address.getHostAddress() + ":" + port
                + " | activeProxy=" + (proxy == null ? "NONE" : proxy.getIp() + ":" + proxy.getPort()));

        if (proxy == null) {
            return bootstrap.connect(address, port);
        }
        try {
            InetSocketAddress relay = ProxyRelay.open(proxy, address.getHostAddress(), port);
            System.out.println("[Polaris] Relay OK: 127.0.0.1:" + relay.getPort()
                    + " -> " + proxy.getIp() + ":" + proxy.getPort()
                    + " (" + proxy.getType() + ") -> " + address.getHostAddress() + ":" + port);
            return bootstrap.connect(relay.getAddress(), relay.getPort());
        } catch (Throwable t) {
            System.out.println("[Polaris] Relay FAILED -> direct: " + t);
            t.printStackTrace();
            return bootstrap.connect(address, port);
        }
    }
}