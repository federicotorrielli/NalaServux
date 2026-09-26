package mc.nala.servux.network;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import mc.nala.servux.Servux;
import mc.nala.servux.network.PlayPayloadHandler.Context;
import mc.nala.servux.paper.PaperNetwork;

/**
 * Interface for ServerPlayHandler.
 * Upstream registers with the Fabric networking API; this port registers Bukkit plugin channels
 * through {@link PaperNetwork}, and keeps the upstream method set.
 * @param <T> (Payload Param)
 */
public interface IPluginServerPlayHandler<T extends CustomPacketPayload> extends PlayPayloadHandler<@NotNull T>
{
    int FROM_SERVER = 1;
    int TO_SERVER = 2;
    int BOTH_SERVER = 3;
    int TO_CLIENT = 4;
    int FROM_CLIENT = 5;
    int BOTH_CLIENT = 6;
    int MAX_FAILURES = 2;

    Map<Identifier, StreamCodec<? super RegistryFriendlyByteBuf, ?>> CODECS = new ConcurrentHashMap<>();

    /**
     * Returns your HANDLER's CHANNEL ID
     * @return (Channel ID)
     */
    Identifier getPayloadChannel();

    /**
     * Returns if your Channel ID has been registered to your Play Payload.
     * @param channel (Your Channel ID)
     * @return (true / false)
     */
    boolean isPlayRegistered(Identifier channel);

    /**
     * Sets your HANDLER as registered.
     * @param channel (Your Channel ID)
     */
    void setPlayRegistered(Identifier channel);

    /**
     * Send your HANDLER a global reset() event, such as when the server is shutting down.
     * @param channel (Your Channel ID)
     */
    void reset(Identifier channel);

    /**
     * Stores the Payload codec, which decodes the incoming plugin messages of this channel.
     * @param id (Your Payload Id<T>)
     * @param codec (Your Payload's CODEC)
     * @param direction (Payload Direction; plugin channels are always registered in both directions)
     */
    default void registerPlayPayload(@Nonnull CustomPacketPayload.Type<@NotNull T> id, @Nonnull StreamCodec<? super RegistryFriendlyByteBuf, @NotNull T> codec, int direction)
    {
        if (this.isPlayRegistered(this.getPayloadChannel()) == false)
        {
            CODECS.put(id.id(), codec);
            this.setPlayRegistered(this.getPayloadChannel());
            return;
        }

        Servux.LOGGER.error("registerPlayPayload: channel ID [{}] is invalid, or it is already registered", this.getPayloadChannel());
    }

    /**
     * Registers the plugin channel and your Packet Receiver function.
     * @param id (Your Payload Id<T>)
     * @param receiver (Your Packet Receiver // if null, uses this::receivePlayPayload)
     * @return (True / False)
     */
    @SuppressWarnings("unchecked")
    default boolean registerPlayReceiver(@Nonnull CustomPacketPayload.Type<@NotNull T> id, @Nullable PlayPayloadHandler<@NotNull T> receiver)
    {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = (StreamCodec<? super RegistryFriendlyByteBuf, T>) CODECS.get(id.id());

        if (this.isPlayRegistered(this.getPayloadChannel()) && codec != null)
        {
            PlayPayloadHandler<T> handler = Objects.requireNonNullElse(receiver, this::receivePlayPayload);

            PaperNetwork.registerChannel(id.id(), (player, buf) ->
            {
                T payload = codec.decode(buf);
                handler.receive(payload, new Context(player));
            });

            return true;
        }

        Servux.LOGGER.error("registerPlayReceiver: Channel ID [{}] is invalid, or not registered", this.getPayloadChannel());
        return false;
    }

    /**
     * Unregisters the plugin channel and your Packet Receiver function.
     */
    default void unregisterPlayReceiver()
    {
        PaperNetwork.unregisterChannel(this.getPayloadChannel());
    }

    /**
     * Receive Payload by pointing static receive() method to this to convert Payload to its data decode() function.
     * @param payload (Payload to decode)
     * @param ctx (Context)
     */
    void receivePlayPayload(T payload, Context ctx);

    /**
     * Payload Decoder wrapper function [OPTIONAL]
     * @param channel (Channel)
     * @param player (Player received from)
     * @param data (Data Codec)
     */
    default <P extends IServerPayloadData> void decodeServerData(Identifier channel, ServerPlayer player, P data) {}

    /**
     * Payload Encoder wrapper function [OPTIONAL]
     * @param player (Player to send the data to)
     * @param data (Data Codec)
     */
    default <P extends IServerPayloadData> void encodeServerData(ServerPlayer player, P data) {}

    /**
     * Used as an iterative "wrapper" for Payload Splitter to send individual Packets
     * @param player (Player to send the packet to)
     * @param buf (Sliced Buffer to send)
     * @param networkHandler (Network Handler as a fail-over option)
     */
    void encodeWithSplitter(ServerPlayer player, FriendlyByteBuf buf, ServerGamePacketListenerImpl networkHandler);

    /**
     * Sends the Payload to the player, if the client listens on this channel.
     * @param player (Player to send the data to)
     * @param payload (The Payload to send)
     * @return (true/false --> for error control)
     */
    default boolean sendPlayPayload(@Nonnull ServerPlayer player, @Nonnull T payload)
    {
        if (payload.type().id().equals(this.getPayloadChannel()) &&
            this.isPlayRegistered(this.getPayloadChannel()) &&
            this.checkFailures(player))
        {
            if (PaperNetwork.canSend(player, this.getPayloadChannel()))
            {
                return this.sendEncoded(player, payload);
            }
        }
        else
        {
            Servux.LOGGER.warn("sendPlayPayload: error sending payload for channel: {}, check if channel is registered", payload.type().id().toString());
        }

        return false;
    }

    /**
     * Sends the Payload to the player of this network handler.
     * @param handler (ServerGamePacketListenerImpl)
     * @param payload (The Payload to send)
     * @return (true/false --> for error control)
     */
    default boolean sendPlayPayload(@Nonnull ServerGamePacketListenerImpl handler, @Nonnull T payload)
    {
        return this.sendPlayPayload(handler.getPlayer(), payload);
    }

    @SuppressWarnings("unchecked")
    private boolean sendEncoded(ServerPlayer player, T payload)
    {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = (StreamCodec<? super RegistryFriendlyByteBuf, T>) CODECS.get(this.getPayloadChannel());

        if (codec == null)
        {
            return false;
        }

        RegistryFriendlyByteBuf buf = PaperNetwork.wrap(Unpooled.buffer());

        try
        {
            codec.encode(buf, payload);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            PaperNetwork.send(player, this.getPayloadChannel(), data);
            return true;
        }
        finally
        {
            buf.release();
        }
    }

    /**
     * Max Failures
     * @return -
     */
    default int maxFailures() { return MAX_FAILURES; }

    /**
     * Tick the Failure Counter
     * @param player -
     */
    void tickFailures(ServerPlayer player);

    /**
     * Return if it is safe to proceed processing packets.
     * @param player -
     * @return True for safe; False for unsafe.
     */
    boolean checkFailures(ServerPlayer player);
}
