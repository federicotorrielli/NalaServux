package mc.nala.servux.paper;

import java.util.UUID;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mc.nala.servux.dataproviders.EntitiesDataProvider;

/** Replaces the upstream easy place and NBT query mixins on ServerGamePacketListenerImpl. */
public class PacketInterceptor extends ChannelDuplexHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger("servux");
    private static final String NAME = "nalaservux";
    private final UUID uuid;

    private PacketInterceptor(UUID uuid)
    {
        this.uuid = uuid;
    }

    public static void inject(ServerPlayer player)
    {
        Channel channel = player.connection.connection.channel;

        channel.eventLoop().execute(() ->
        {
            if (channel.pipeline().get(NAME) == null && channel.pipeline().get("packet_handler") != null)
            {
                channel.pipeline().addBefore("packet_handler", NAME, new PacketInterceptor(player.getUUID()));
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof ServerboundUseItemOnPacket packet)
        {
            msg = EasyPlace.onUseItemOn(this.uuid, packet);
        }
        else if (msg instanceof ServerboundBlockEntityTagQueryPacket packet && EntitiesDataProvider.INSTANCE.hasNbtQueryOverride())
        {
            MinecraftServer.getServer().execute(() -> this.queryBlockEntity(packet));
            return;
        }
        else if (msg instanceof ServerboundEntityTagQueryPacket packet && EntitiesDataProvider.INSTANCE.hasNbtQueryOverride())
        {
            MinecraftServer.getServer().execute(() -> this.queryEntity(packet));
            return;
        }

        super.channelRead(ctx, msg);
    }

    // Vanilla handleBlockEntityTagQuery with the upstream permission override.
    private void queryBlockEntity(ServerboundBlockEntityTagQueryPacket packet)
    {
        ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

        if (player != null && EntitiesDataProvider.INSTANCE.hasNbtQueryPermission(player))
        {
            BlockEntity blockEntity = player.level().getBlockEntity(packet.getPos());
            CompoundTag tag = blockEntity != null ? blockEntity.saveWithoutMetadata(player.registryAccess()) : null;
            player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), tag));
        }
    }

    // Vanilla handleEntityTagQuery with the upstream permission override.
    private void queryEntity(ServerboundEntityTagQueryPacket packet)
    {
        ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

        if (player != null && EntitiesDataProvider.INSTANCE.hasNbtQueryPermission(player))
        {
            Entity entity = player.level().getEntity(packet.getEntityId());

            if (entity != null)
            {
                try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER))
                {
                    TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
                    entity.saveWithoutId(output);
                    player.connection.send(new ClientboundTagQueryPacket(packet.getTransactionId(), output.buildResult()));
                }
            }
        }
    }
}
