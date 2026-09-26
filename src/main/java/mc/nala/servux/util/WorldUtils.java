package mc.nala.servux.util;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Upstream uses Level/LevelChunk mixins to skip {@code onPlace} while it pastes or fills.
 * Vanilla has the same switch as a setBlock flag, so the suppressed sections add
 * {@link Block#UPDATE_SKIP_ON_PLACE} to their flags through {@link #flags(Level, int)}.
 */
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
