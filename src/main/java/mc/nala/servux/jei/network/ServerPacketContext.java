package mc.nala.servux.jei.network;

import mc.nala.servux.jei.config.IServerConfig;
import net.minecraft.server.level.ServerPlayer;

public record ServerPacketContext(ServerPlayer player,
	IServerConfig serverConfig,
	IConnectionToClient connection
) {
}
