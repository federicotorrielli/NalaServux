package mc.nala.servux;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import net.minecraft.SharedConstants;

public class Reference
{
    public static final String MOD_ID = "servux";
    public static final String MOD_NAME = "Servux";
    public static final String MOD_VERSION = readPluginVersion();
    public static final String MC_VERSION = SharedConstants.getCurrentVersion().id();
    // The clients only accept a handshake string that starts with "servux-fabric-<MC_VERSION>".
    public static final String MOD_TYPE = "fabric";
    public static final String MOD_STRING = MOD_ID+"-"+MOD_TYPE+"-"+MC_VERSION+"-"+MOD_VERSION;

    public static final boolean ANSI_MODE = false;
    // Use -Dservux.debug.mode=true or SERVUX_DEBUG_MODE=true to enable debug mode.
    public static final boolean DEBUG_MODE = isDebug();

    private static boolean isDebug()
    {
        final String override = System.getProperty(MOD_ID+".debug.mode");
        final String envOverride = System.getenv(MOD_ID.toUpperCase()+"_DEBUG_MODE");

        if (override != null) { return Boolean.parseBoolean(override); }
        if (envOverride != null) { return Boolean.parseBoolean(envOverride); }

        return false;
    }

    private static String readPluginVersion()
    {
        try (InputStream in = Reference.class.getResourceAsStream("/nalaservux.properties"))
        {
            Properties props = new Properties();

            if (in != null)
            {
                props.load(in);
            }

            return props.getProperty("version", "?");
        }
        catch (IOException e)
        {
            return "?";
        }
    }
}
