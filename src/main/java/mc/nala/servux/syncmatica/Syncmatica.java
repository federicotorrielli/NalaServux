package mc.nala.servux.syncmatica;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mc.nala.servux.syncmatica.command.SyncmaticaCommand;
import mc.nala.servux.syncmatica.communication.CommunicationManager;
import mc.nala.servux.syncmatica.data.IFileStorage;
import mc.nala.servux.syncmatica.data.SyncmaticManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.resources.Identifier;

// could probably turn this into a singleton
public class Syncmatica
{
    public static Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

//    protected static final String SERVER_PATH = "." + File.separator + "syncmatics";
//    protected static final String CLIENT_PATH = "." + File.separator + "schematics" + File.separator + "sync";
    protected static final Path SERVER_PATH = Reference.GAME_ROOT.resolve("syncmatics");
    public static final Identifier SERVER_CONTEXT = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "server_context");
    public static final Identifier NETWORK_ID = Identifier.fromNamespaceAndPath(Reference.MOD_ID, "main");
    public static final UUID syncmaticaId = UUID.fromString("4c1b738f-56fa-4011-8273-498c972424ea");
    protected static Map<Identifier, Context> contexts = null;
    protected static boolean context_init = false;

    /**
     * Streamlined debugging tool via MOD_DEBUG boolean
     * @param msg (message content)
     * @param args (variable args)
     */
    public static void debug(String msg, Object... args)
    {
        if (Reference.MOD_DEBUG)
        {
            LOGGER.info(msg, args);
        }
    }

    public static Context getContext(final Identifier id)
    {
        if (context_init)
            return contexts.get(id);
        else return null;
    }

    static void init(final Context con, final Identifier contextId) {
        Syncmatica.debug("Syncmatica#init()");

        if (contexts == null) {
            contexts = new HashMap<>();
        }
        // Always update the context to ensure we have the latest instance (e.g. after re-login)
        contexts.put(contextId, con);
        context_init = true;
    }

    public static void shutdown() {
        Syncmatica.debug("Syncmatica#shutdown()");

        if (contexts != null) {
            for (final Context con : contexts.values()) {
                if (con.isStarted()) {
                    con.shutdown();
                }
            }
        }
        deinit();
    }

    private static void deinit() {
        Syncmatica.debug("Syncmatica#deinit()");

        contexts = null;
        context_init = false;
    }

    public static Context initServer(final CommunicationManager comms, final IFileStorage fileStorage, final SyncmaticManager schematics,
                                     final boolean isIntegratedServer, final Path worldPath)
    {
        Syncmatica.debug("Syncmatica#initServer()");

        final Context serverContext = new Context(
                fileStorage,
                comms,
                schematics,
                true,
//                new File(SERVER_PATH),
                SERVER_PATH,
                isIntegratedServer,
                worldPath
        );
        Syncmatica.debug("INIT:Server Context; world path: '{}'", worldPath.toAbsolutePath().toString());
        Syncmatica.init(serverContext, SERVER_CONTEXT);
        SyncmaticaCommand.INSTANCE.updateSyncmaticDir(serverContext);
        return serverContext;
    }

    protected Syncmatica() {}
}
