package mc.nala.servux.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Replaces the Fabric {@code ServerPlayNetworking.PlayPayloadHandler}.
 */
@FunctionalInterface
public interface PlayPayloadHandler<T>
{
    void receive(T payload, Context context);

    /**
     * Replaces the Fabric {@code ServerPlayNetworking.Context}.
     */
    record Context(ServerPlayer player) {}
}
