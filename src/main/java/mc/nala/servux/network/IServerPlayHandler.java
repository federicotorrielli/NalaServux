package mc.nala.servux.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface IServerPlayHandler
{
    <P extends CustomPacketPayload> void registerServerPlayHandler(IPluginServerPlayHandler<P> handler);
    <P extends CustomPacketPayload> void unregisterServerPlayHandler(IPluginServerPlayHandler<P> handler);
}
