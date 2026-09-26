package mc.nala.servux.syncmatica.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import mc.nala.servux.syncmatica.Context;
import mc.nala.servux.syncmatica.Syncmatica;
import mc.nala.servux.syncmatica.communication.exchange.Exchange;
import mc.nala.servux.syncmatica.network.handler.ServerPlayHandler;
import mc.nala.servux.syncmatica.network.PacketType;
import mc.nala.servux.syncmatica.network.SyncmaticaPacket;

// since Client/Server PlayNetworkHandler are 2 different classes, but I want to use exchanges
// on both without having to recode them individually, I have an adapter class here
public class ExchangeTarget
{
    public final ServerGamePacketListenerImpl serverPlayNetworkHandler;
    private final String persistentName;

    private FeatureSet features;
    private final List<Exchange> ongoingExchanges = new ArrayList<>(); // implicitly relies on priority

    public ExchangeTarget(ServerGamePacketListenerImpl serverPlayContext)
    {
        this.serverPlayNetworkHandler = serverPlayContext;
        this.persistentName = serverPlayContext.getPlayer().getStringUUID();
    }

    // this application exclusively communicates in CustomPayLoad packets
    // this class handles the sending of either S2C or C2S packets
    /**
     * The Fabric API call mode sometimes fails here, because the channels might not be registered in PLAY mode, especially for Single Player.
     */
    public void sendPacket(final PacketType type, final FriendlyByteBuf byteBuf, final Context context)
    {
        //SyncLog.debug("ExchangeTarget#sendPacket(): invoked.");
        if (context != null) {
            context.getDebugService().logSendPacket(type, persistentName);
        }
        final SyncmaticaPacket newPacket = new SyncmaticaPacket(type.getId(), byteBuf);

        if (newPacket.getType() == null)
        {
            Syncmatica.LOGGER.error("ExchangeTarget#sendPacket(): error, PacketType {} resulted in a null Payload", type.toString());
            return;
        }
        if (serverPlayNetworkHandler != null)
        {
            //ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
            //SyncLog.debug("ExchangeTarget#sendPacket(): in Server Context, packet type: {}, size in bytes: {} to player: {}", type.getId().toString(), buf.readableBytes(), player.getName().getLiteralString());
            ServerPlayHandler.encodeSyncData(newPacket, serverPlayNetworkHandler);
        }
    }

    // removed equals code due to issues with Collection.contains
    public FeatureSet getFeatureSet() { return features; }

    public void setFeatureSet(final FeatureSet f) { features = f; }

    public Collection<Exchange> getExchanges() { return ongoingExchanges; }

    public String getPersistentName() { return persistentName; }

    public boolean isServer() { return serverPlayNetworkHandler != null; }

    public boolean isClient() { return false; }
}
