package mc.nala.servux.syncmatica.network.actor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;

import mc.nala.servux.syncmatica.Context;
import mc.nala.servux.syncmatica.Syncmatica;
import mc.nala.servux.syncmatica.communication.ExchangeTarget;
import mc.nala.servux.syncmatica.communication.ServerCommunicationManager;

/**
 * Per player state that upstream keeps in MixinServerGamePacketListenerImpl.
 */
public class ServerConnection implements IServerPlay
{
    private static final Map<UUID, ServerConnection> CONNECTIONS = new ConcurrentHashMap<>();
    private final ServerPlayer player;
    private ExchangeTarget exTarget = null;
    private ServerCommunicationManager comManager = null;

    private ServerConnection(ServerPlayer player)
    {
        this.player = player;
    }

    public static ServerConnection get(ServerPlayer player)
    {
        ServerConnection connection = CONNECTIONS.get(player.getUUID());

        if (connection == null || connection.player.connection != player.connection)
        {
            connection = new ServerConnection(player);
            CONNECTIONS.put(player.getUUID(), connection);
        }

        return connection;
    }

    // Upstream: ServerGamePacketListenerImpl.<init> TAIL
    public static void onConnect(ServerPlayer player)
    {
        ServerConnection connection = get(player);
        connection.syncmatica$operateComms(sm -> sm.onPlayerJoin(connection.syncmatica$getExchangeTarget(), player));
    }

    // Upstream: ServerGamePacketListenerImpl.onDisconnect HEAD
    public static void onDisconnect(ServerPlayer player)
    {
        ServerConnection connection = CONNECTIONS.remove(player.getUUID());

        if (connection != null)
        {
            connection.syncmatica$operateComms(sm -> sm.onPlayerLeave(connection.syncmatica$getExchangeTarget()));
        }
    }

    @Override
    public void syncmatica$operateComms(final Consumer<ServerCommunicationManager> operation)
    {
        if (this.comManager == null)
        {
            final Context con = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);

            if (con != null)
            {
                this.comManager = (ServerCommunicationManager) con.getCommunicationManager();
            }
        }

        if (this.comManager != null)
        {
            operation.accept(this.comManager);
        }
    }

    @Override
    public ExchangeTarget syncmatica$getExchangeTarget()
    {
        if (this.exTarget == null)
        {
            this.exTarget = new ExchangeTarget(this.player.connection);
        }

        return this.exTarget;
    }
}
