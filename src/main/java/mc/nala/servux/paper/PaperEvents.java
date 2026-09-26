package mc.nala.servux.paper;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.netty.buffer.Unpooled;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.SpawnChangeEvent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.WeatherData;

import mc.nala.servux.dataproviders.DataProviderManager;
import mc.nala.servux.dataproviders.HudDataProvider;
import mc.nala.servux.dataproviders.StructureDataProvider;
import mc.nala.servux.event.PlayerHandler;
import mc.nala.servux.event.ServerHandler;
import mc.nala.servux.jei.recipesync.ClientboundRecipeSyncPayload;
import mc.nala.servux.jei.recipesync.RecipeSync;
import mc.nala.servux.jei.recipesync.RecipeSyncJoinOrder;
import mc.nala.servux.scheduler.TaskScheduler;
import mc.nala.servux.syncmatica.Context;
import mc.nala.servux.syncmatica.Syncmatica;
import mc.nala.servux.syncmatica.communication.ServerCommunicationManager;
import mc.nala.servux.syncmatica.data.FileStorage;
import mc.nala.servux.syncmatica.data.SyncmaticManager;
import mc.nala.servux.syncmatica.network.PacketType;
import mc.nala.servux.syncmatica.network.SyncmaticaPacket;
import mc.nala.servux.syncmatica.network.actor.ServerConnection;
import mc.nala.servux.syncmatica.network.handler.ServerPlayHandler;

/**
 * Paper events that replace the upstream lifecycle, player and world mixins.
 */
public class PaperEvents implements Listener
{
    private static ServerHandler server() { return (ServerHandler) ServerHandler.getInstance(); }

    private static PlayerHandler players() { return (PlayerHandler) PlayerHandler.getInstance(); }

    // Upstream: MinecraftServer.runServer at buildServerStatus()
    @EventHandler
    public void onServerLoad(ServerLoadEvent event)
    {
        server().onServerStarted(MinecraftServer.getServer());

        // Upstream Syncmatica: MixinMinecraftServer.runServer at buildServerStatus()
        Syncmatica.initServer(new ServerCommunicationManager(), new FileStorage(), new SyncmaticManager(), false,
                              NalaServuxPlugin.getInstance().getDataFolder().toPath()).startup();
    }

    // Upstream: MinecraftServer.reloadResources HEAD and TAIL
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event)
    {
        MinecraftServer mc = MinecraftServer.getServer();
        server().onServerResourceReloadPre(mc, mc.getResourceManager());
        server().onServerResourceReloadPost(mc, mc.getResourceManager(), true);

        // Fabric API: ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS after a reload
        for (ServerPlayer player : mc.getPlayerList().getPlayers())
        {
            if (RecipeSync.canSend(player))
            {
                RecipeSync.sendRecipes(player);
            }
        }
    }

    // Fabric API: SYNC_DATA_PACK_CONTENTS at join, gated on canSend(fabric:recipe_sync)
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event)
    {
        if (event.getChannel().equals(ClientboundRecipeSyncPayload.CHANNEL.toString()))
        {
            RecipeSyncJoinOrder.sendAndRelease(((CraftPlayer) event.getPlayer()).getHandle());
        }
    }

    // Upstream: MinecraftServer.tickServer RETURN, and ServerLevel.advanceWeatherCycle for the weather timers
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event)
    {
        MinecraftServer mc = MinecraftServer.getServer();
        ProfilerFiller profiler = Profiler.get();

        if (HudDataProvider.INSTANCE.isEnabled())
        {
            WeatherData weather = mc.overworld().getWeatherData();
            HudDataProvider.INSTANCE.tickWeather(weather.getClearWeatherTime(), weather.getRainTime(), weather.getThunderTime(),
                                                 weather.isRaining(), weather.isThundering());
        }

        profiler.push("servux_tick");
        TaskScheduler.getInstance().runTasks(profiler);
        DataProviderManager.INSTANCE.tickProviders(mc, event.getTickNumber(), profiler);
        profiler.pop();
    }

    // Upstream: ServerLevel.setRespawnData TAIL
    @EventHandler
    public void onSpawnChange(SpawnChangeEvent event)
    {
        if (HudDataProvider.INSTANCE.isEnabled())
        {
            ServerLevel level = ((CraftWorld) event.getWorld()).getHandle();
            HudDataProvider.INSTANCE.setSpawnPos(level.getRespawnData().globalPos());
        }
    }

    // Upstream: ChunkMap.markChunkPendingToSend HEAD
    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent event)
    {
        if (StructureDataProvider.INSTANCE.isEnabled())
        {
            ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
            LevelChunk chunk = (LevelChunk) ((CraftChunk) event.getChunk()).getHandle(net.minecraft.world.level.chunk.status.ChunkStatus.FULL);
            StructureDataProvider.INSTANCE.onStartedWatchingChunk(player, chunk);
        }
    }

    // Upstream: PlayerList.placeNewPlayer TAIL
    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        PacketInterceptor.inject(player);

        // The client may have declared fabric:recipe_sync during configuration, then no register event follows.
        if (RecipeSync.canSend(player))
        {
            RecipeSyncJoinOrder.sendAndRelease(player);
        }
        players().onPlayerJoin(player.connection.getRemoteAddress(), player.getGameProfile(), player);

        // Upstream Syncmatica: ServerGamePacketListenerImpl.<init> TAIL, then PlayerList.placeNewPlayer TAIL
        ServerConnection.onConnect(player);
        Context syncmatica = Syncmatica.getContext(Syncmatica.SERVER_CONTEXT);

        if (syncmatica != null && syncmatica.isStarted())
        {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeUtf(mc.nala.servux.syncmatica.Reference.MOD_VERSION);
            ServerPlayHandler.encodeSyncData(new SyncmaticaPacket(PacketType.REGISTER_VERSION.getId(), buf), player);
        }
    }

    // Upstream: PlayerList.remove HEAD
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event)
    {
        ServerPlayer player = ((CraftPlayer) event.getPlayer()).getHandle();
        ServerConnection.onDisconnect(player);
        RecipeSyncJoinOrder.onQuit(player.getUUID());
        players().onPlayerLeave(player);
        PaperNetwork.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
