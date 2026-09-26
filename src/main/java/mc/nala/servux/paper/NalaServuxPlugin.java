package mc.nala.servux.paper;

import com.mojang.brigadier.CommandDispatcher;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

import mc.nala.servux.Reference;
import mc.nala.servux.Servux;
import mc.nala.servux.commands.CommandProvider;
import mc.nala.servux.commands.ServuxCommand;
import mc.nala.servux.dataproviders.DataProviderManager;
import mc.nala.servux.dataproviders.HudDataProvider;
import mc.nala.servux.event.ServerHandler;
import mc.nala.servux.scheduler.TaskScheduler;
import mc.nala.servux.servux.ServuxInitHandler;

/**
 * Plugin entry point. Replaces the upstream ModInitializer and the server lifecycle mixins.
 */
public class NalaServuxPlugin extends JavaPlugin
{
    private static NalaServuxPlugin instance;

    public static NalaServuxPlugin getInstance()
    {
        return instance;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onEnable()
    {
        instance = this;
        MinecraftServer server = MinecraftServer.getServer();

        DataProviderManager.INSTANCE.setDataDir(this.getDataFolder().toPath());
        DataProviderManager.INSTANCE.onCaptureImmutable(server.registryAccess());

        // Upstream: Servux.onInitialize() and MixinDedicatedServer.<init>
        new ServuxInitHandler().onServerInit();
        CommandProvider.getInstance().registerCommand(ServuxCommand.INSTANCE);

        // Upstream: MixinCommands.<init>
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
        {
            CommandDispatcher<CommandSourceStack> dispatcher = (CommandDispatcher) event.registrar().getDispatcher();
            CommandBuildContext context = CommandBuildContext.simple(server.registryAccess(), server.getWorldData().enabledFeatures());
            ((CommandProvider) CommandProvider.getInstance()).registerCommands(dispatcher, context, Commands.CommandSelection.DEDICATED);
        });

        // Upstream: MinecraftServer.runServer before initServer()
        ((ServerHandler) ServerHandler.getInstance()).onServerStarting(server);

        // Upstream: MinecraftServer.prepareLevels
        if (HudDataProvider.INSTANCE.isEnabled())
        {
            HudDataProvider.INSTANCE.setSpawnPos(server.getWorldData().overworldData().getRespawnData().globalPos());
        }

        this.getServer().getPluginManager().registerEvents(new PaperEvents(), this);
        Servux.LOGGER.info("{} enabled ({})", this.getName(), Reference.MOD_STRING);
    }

    @Override
    public void onDisable()
    {
        MinecraftServer server = MinecraftServer.getServer();

        TaskScheduler.getInstance().clearTasks();
        ((ServerHandler) ServerHandler.getInstance()).onServerStopping(server);
        ((ServerHandler) ServerHandler.getInstance()).onServerStopped(server);
        instance = null;
    }
}
