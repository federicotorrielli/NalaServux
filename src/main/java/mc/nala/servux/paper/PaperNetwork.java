package mc.nala.servux.paper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;

/** Plugin channels for C2S; S2C as raw DiscardedPayload (no 1 MiB Messenger cap, upstream slices are 1 MiB). */
public class PaperNetwork
{
    private static final Map<Identifier, PluginMessageListener> LISTENERS = new ConcurrentHashMap<>();
    // Players who sent C2S on a channel can receive on it, even before Paper registers the channel.
    private static final Map<Identifier, Set<UUID>> PROVEN_RECEIVERS = new ConcurrentHashMap<>();

    public static void registerChannel(Identifier channel, BiConsumer<ServerPlayer, RegistryFriendlyByteBuf> receiver)
    {
        Messenger messenger = NalaServuxPlugin.getInstance().getServer().getMessenger();
        String name = channel.toString();
        PluginMessageListener listener = (ch, player, bytes) ->
        {
            ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
            PROVEN_RECEIVERS.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
            receiver.accept(serverPlayer, wrap(Unpooled.wrappedBuffer(bytes)));
        };

        unregisterChannel(channel);
        messenger.registerIncomingPluginChannel(NalaServuxPlugin.getInstance(), name, listener);
        messenger.registerOutgoingPluginChannel(NalaServuxPlugin.getInstance(), name);
        LISTENERS.put(channel, listener);
    }

    public static void unregisterChannel(Identifier channel)
    {
        PluginMessageListener listener = LISTENERS.remove(channel);

        if (listener != null)
        {
            Messenger messenger = NalaServuxPlugin.getInstance().getServer().getMessenger();
            messenger.unregisterIncomingPluginChannel(NalaServuxPlugin.getInstance(), channel.toString(), listener);
            messenger.unregisterOutgoingPluginChannel(NalaServuxPlugin.getInstance(), channel.toString());
        }

        PROVEN_RECEIVERS.remove(channel);
    }

    public static RegistryFriendlyByteBuf wrap(ByteBuf buf)
    {
        return new RegistryFriendlyByteBuf(buf, MinecraftServer.getServer().registryAccess());
    }

    public static boolean canSend(ServerPlayer player, Identifier channel)
    {
        Player bukkit = player.getBukkitEntity();

        if (bukkit.getListeningPluginChannels().contains(channel.toString()))
        {
            return true;
        }

        Set<UUID> proven = PROVEN_RECEIVERS.get(channel);
        return proven != null && proven.contains(player.getUUID());
    }

    public static void send(ServerPlayer player, Identifier channel, byte[] data)
    {
        if (player.connection != null)
        {
            player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(channel, data)));
        }
    }

    public static void onPlayerQuit(UUID uuid)
    {
        PROVEN_RECEIVERS.values().forEach(set -> set.remove(uuid));
    }
}
