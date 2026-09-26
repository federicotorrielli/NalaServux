package mc.nala.servux.network;

import net.minecraft.server.level.ServerPlayer;

/** Replaces Fabric's ServerPlayNetworking.PlayPayloadHandler. */
@FunctionalInterface
public interface PlayPayloadHandler<T>
{
    void receive(T payload, Context context);

    /** Replaces Fabric's ServerPlayNetworking.Context. */
    record Context(ServerPlayer player) {}
}
