package mc.nala.servux.jei.network;

import java.util.function.BiConsumer;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.NonNull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import mc.nala.servux.jei.config.IServerConfig;
import mc.nala.servux.jei.network.packets.*;
import mc.nala.servux.jei.network.packets.legacy.PacketRecipeTransfer;
import mc.nala.servux.jei.network.packets.legacy.PacketRecipeTransferCounted;
import mc.nala.servux.paper.PaperNetwork;

/** JEI's Fabric ServerNetworkHandler and ConnectionToClient on plugin channels. */
public final class JeiServerNetwork implements IConnectionToClient {
	private static final JeiServerNetwork CONNECTION = new JeiServerNetwork();

	private JeiServerNetwork() {
	}

	public static void register(IServerConfig serverConfig) {
		register(PacketDeletePlayerItem.TYPE, PacketDeletePlayerItem.STREAM_CODEC, serverConfig, PacketDeletePlayerItem::process);
		register(PacketGiveItemStack.TYPE, PacketGiveItemStack.STREAM_CODEC, serverConfig, PacketGiveItemStack::process);
		register(PacketRecipeTransfer.TYPE, PacketRecipeTransfer.STREAM_CODEC, serverConfig, PacketRecipeTransfer::process);
		register(PacketRecipeTransferCounted.TYPE, PacketRecipeTransferCounted.STREAM_CODEC, serverConfig, PacketRecipeTransferCounted::process);
		register(PacketRecipeTransferWithResult.TYPE, PacketRecipeTransferWithResult.STREAM_CODEC, serverConfig, PacketRecipeTransferWithResult::process);
		register(PacketRecipeTransferCountedWithResult.TYPE, PacketRecipeTransferCountedWithResult.STREAM_CODEC, serverConfig, PacketRecipeTransferCountedWithResult::process);
		register(PacketSetHotbarItemStack.TYPE, PacketSetHotbarItemStack.STREAM_CODEC, serverConfig, PacketSetHotbarItemStack::process);
		register(PacketRequestCheatPermission.TYPE, PacketRequestCheatPermission.STREAM_CODEC, serverConfig, PacketRequestCheatPermission::process);
	}

	private static <T extends PlayToServerPacket<T>> void register(
		CustomPacketPayload.Type<T> type,
		StreamCodec<RegistryFriendlyByteBuf, T> codec,
		IServerConfig serverConfig,
		BiConsumer<T, ServerPacketContext> consumer
	) {
		PaperNetwork.registerChannel(type.id(), (player, buf) -> {
			T packet = codec.decode(buf);
			consumer.accept(packet, new ServerPacketContext(player, serverConfig, CONNECTION));
		});
	}

	@Override
	public <T extends PlayToClientPacket<T>> void sendPacketToClient(@NonNull T packet, @NonNull ServerPlayer player) {
		RegistryFriendlyByteBuf buf = PaperNetwork.wrap(Unpooled.buffer());

		try {
			packet.streamCodec().encode(buf, packet);
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			PaperNetwork.send(player, packet.type().id(), bytes);
		} finally {
			buf.release();
		}
	}
}
