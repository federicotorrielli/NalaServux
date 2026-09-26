package mc.nala.servux.util;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Upstream skips onPlace with mixins; here suppressed sections add {@link Block#UPDATE_SKIP_ON_PLACE}. */
public class WorldUtils
{
    private static final Set<Level> PREVENT_UPDATES = Collections.newSetFromMap(new WeakHashMap<>());

    public static boolean shouldPreventBlockUpdates(Level world)
    {
        return PREVENT_UPDATES.contains(world);
    }

    public static void setShouldPreventBlockUpdates(Level world, boolean preventUpdates)
    {
        if (preventUpdates)
        {
            PREVENT_UPDATES.add(world);
        }
        else
        {
            PREVENT_UPDATES.remove(world);
        }
    }

    public static int flags(Level world, int flags)
    {
        return shouldPreventBlockUpdates(world) ? flags | Block.UPDATE_SKIP_ON_PLACE : flags;
    }
}
