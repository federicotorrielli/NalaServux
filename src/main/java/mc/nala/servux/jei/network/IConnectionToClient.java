package mc.nala.servux.jei.network;

import mc.nala.servux.jei.network.packets.PlayToClientPacket;
import net.minecraft.server.level.ServerPlayer;

public interface IConnectionToClient {
	<T extends PlayToClientPacket<T>> void sendPacketToClient(T packet, ServerPlayer player);
}
