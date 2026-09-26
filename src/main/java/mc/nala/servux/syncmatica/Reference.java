package mc.nala.servux.syncmatica;

import java.nio.file.Path;

import net.minecraft.SharedConstants;

import mc.nala.servux.paper.NalaServuxPlugin;

/**
 * Upstream reads these from Fabric Loader. This port is a dedicated server only.
 */
public class Reference
{
    public static final String MOD_ID = "syncmatica";
    public static final String MOD_NAME = "Syncmatica";
    // Upstream Syncmatica version that this port follows. The client builds its FeatureSet from it.
    public static final String MOD_VERSION = "0.3.20";
    public static final String MC_VERSION = SharedConstants.getCurrentVersion().id();
    public static final boolean MOD_DEBUG = false;

    // Both roots are the plugin data folder: plugins/NalaServux/syncmatics and plugins/NalaServux/syncmatica.
    public static final Path GAME_ROOT = NalaServuxPlugin.getInstance().getDataFolder().toPath();
    public static final Path CONFIG_ROOT = GAME_ROOT;

    public static boolean isClient() { return false; }

    public static boolean isServer() { return true; }

    public static boolean isDedicatedServer() { return true; }

    public static boolean isIntegratedServer() { return false; }

    public static boolean isOpenToLan() { return false; }
}
