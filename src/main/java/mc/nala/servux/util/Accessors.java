package mc.nala.servux.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInputContextHelper;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;

/**
 * Replaces the upstream {@code @Accessor} mixins with private field handles.
 */
public class Accessors
{
    private static final VarHandle REMAINING_SPRINT_TICKS = field(ServerTickRateManager.class, "remainingSprintTicks", long.class);
    private static final VarHandle ALL_CONTAINERS = field(LevelTicks.class, "allContainers", Long2ObjectMap.class);
    private static final VarHandle INPUT_CONTEXT = field(TagValueInput.class, "context", ValueInputContextHelper.class);
    private static final VarHandle OUTPUT_OPS = field(TagValueOutput.class, "ops", DynamicOps.class);
    private static final VarHandle OUTPUT_NBT = field(TagValueOutput.class, "output", CompoundTag.class);
    private static final int SPAWNER_MAGIC_NUMBER = (int) staticField(NaturalSpawner.class, "MAGIC_NUMBER", int.class).get();

    private static VarHandle field(Class<?> owner, String name, Class<?> type)
    {
        try
        {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, name, type);
        }
        catch (ReflectiveOperationException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static VarHandle staticField(Class<?> owner, String name, Class<?> type)
    {
        try
        {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findStaticVarHandle(owner, name, type);
        }
        catch (ReflectiveOperationException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static int getSpawnerMagicNumber()
    {
        return SPAWNER_MAGIC_NUMBER;
    }

    public static long getRemainingSprintTicks(ServerTickRateManager manager)
    {
        return (long) REMAINING_SPRINT_TICKS.get(manager);
    }

    @SuppressWarnings("unchecked")
    public static <T> Long2ObjectMap<LevelChunkTicks<T>> getChunkTickSchedulers(LevelTicks<T> ticks)
    {
        return (Long2ObjectMap<LevelChunkTicks<T>>) ALL_CONTAINERS.get(ticks);
    }

    public static ValueInputContextHelper getContext(TagValueInput input)
    {
        return (ValueInputContextHelper) INPUT_CONTEXT.get(input);
    }

    public static CompoundTag getNbt(TagValueInput input)
    {
        return input.input;
    }

    @SuppressWarnings("unchecked")
    public static DynamicOps<Tag> getOps(TagValueOutput output)
    {
        return (DynamicOps<Tag>) OUTPUT_OPS.get(output);
    }

    public static CompoundTag getNbt(TagValueOutput output)
    {
        return (CompoundTag) OUTPUT_NBT.get(output);
    }
}
