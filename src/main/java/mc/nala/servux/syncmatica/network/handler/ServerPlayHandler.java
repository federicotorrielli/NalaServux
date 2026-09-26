package mc.nala.servux.syncmatica.network.handler;

import javax.annotation.Nonnull;
import io.netty.buffer.Unpooled;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import mc.nala.servux.paper.PaperNetwork;
import mc.nala.servux.syncmatica.Syncmatica;
import mc.nala.servux.syncmatica.network.SyncmaticaPacket;
import mc.nala.servux.syncmatica.network.actor.IServerPlay;
import mc.nala.servux.syncmatica.network.actor.ServerConnection;

/**
 * Network packet senders / receivers (Server Context)
 */
public abstract class ServerPlayHandler
{
    public static void registerReceiver()
    {
        PaperNetwork.registerChannel(Syncmatica.NETWORK_ID, (player, buf) ->
                decodeSyncData(SyncmaticaPacket.Payload.CODEC.decode(buf).data(), ServerConnection.get(player)));
    }

    public static void unregisterReceiver()
    {
        PaperNetwork.unregisterChannel(Syncmatica.NETWORK_ID);
    }

    public static void decodeSyncData(@Nonnull SyncmaticaPacket data, @Nonnull IServerPlay iDo)
    {
        iDo.syncmatica$operateComms(sm -> sm.onPacket(iDo.syncmatica$getExchangeTarget(), data.getType(), data.getPacket()));
    }

    public static void encodeSyncData(@Nonnull SyncmaticaPacket data, @Nonnull ServerGamePacketListenerImpl handler)
    {
        send(data, handler.getPlayer());
    }

    /**
     * Upstream sends straight to the connection, because Fabric registers the channels too late.
     * The raw payload is sent the same way here.
     */
    public static void encodeSyncData(@Nonnull SyncmaticaPacket data, @Nonnull ServerPlayer player)
    {
        send(data, player);
    }

    private static void send(SyncmaticaPacket data, ServerPlayer player)
    {
        RegistryFriendlyByteBuf buf = PaperNetwork.wrap(Unpooled.buffer());

        try
        {
            SyncmaticaPacket.Payload.CODEC.encode(buf, new SyncmaticaPacket.Payload(data));
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            PaperNetwork.send(player, Syncmatica.NETWORK_ID, bytes);
        }
        finally
        {
            buf.release();
        }
    }
}
