package mc.nala.servux.syncmatica;

import java.nio.file.Path;

import net.minecraft.SharedConstants;

import mc.nala.servux.paper.NalaServuxPlugin;

/** Dedicated server only; upstream reads these from Fabric Loader. */
public class Reference
{
    public static final String MOD_ID = "syncmatica";
    public static final String MOD_NAME = "Syncmatica";
    // Sent in the handshake; the client derives its FeatureSet from it.
    public static final String MOD_VERSION = "0.3.20";
    public static final String MC_VERSION = SharedConstants.getCurrentVersion().id();
    public static final boolean MOD_DEBUG = false;

    public static final Path GAME_ROOT = NalaServuxPlugin.getInstance().getDataFolder().toPath();
    // Only used by a client migration; never exists.
    public static final Path CONFIG_ROOT = GAME_ROOT.resolve("config");

    public static boolean isClient() { return false; }

    public static boolean isServer() { return true; }

    public static boolean isDedicatedServer() { return true; }

    public static boolean isIntegratedServer() { return false; }

    public static boolean isOpenToLan() { return false; }
}
