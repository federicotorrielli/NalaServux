package mc.nala.servux.scheduler.tasks;

import java.util.ArrayList;
import java.util.Collection;
import com.google.common.collect.ArrayListMultimap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;

import mc.nala.servux.scheduler.TaskContext;
import mc.nala.servux.schematic.placement.SchematicPlacement;
import mc.nala.servux.util.PasteLayerBehavior;
import mc.nala.servux.util.ReplaceBehavior;
import mc.nala.servux.util.SchematicPlacingUtils;
import mc.nala.servux.util.StringUtils;
import mc.nala.servux.util.position.LayerRange;

public class TaskPasteSchematicPerChunkDirect extends TaskPasteSchematicPerChunkBase
{
	private final ArrayListMultimap<ChunkPos, SchematicPlacement> placementsPerChunk = ArrayListMultimap.create();

	public TaskPasteSchematicPerChunkDirect(TaskContext context,
	                                        final Collection<SchematicPlacement> placements,
	                                        final LayerRange layerRange,
	                                        final ReplaceBehavior replaceBehavior,
	                                        final PasteLayerBehavior layerBehavior,
	                                        final boolean changedBlockOnly,
	                                        final boolean ignoreBlocks,
	                                        final boolean ignoreEntities)
	{
		super(context, placements, layerRange, replaceBehavior, layerBehavior, changedBlockOnly, ignoreBlocks, ignoreEntities);
	}

	@Override
	public boolean canExecute()
	{
		return super.canExecute() && this.context.level() != null;
	}

	@Override
	protected void onChunkAddedForHandling(ChunkPos pos, SchematicPlacement placement)
	{
		super.onChunkAddedForHandling(pos, placement);

		this.placementsPerChunk.put(pos, placement);
	}

	@Override
	public boolean execute(ProfilerFiller profiler)
	{
		// Nothing to do
		if (this.ignoreBlocks && this.ignoreEntities)
		{
			return true;
		}

		profiler.push("per_chunk_paste");

		MinecraftServer server = this.context.server();
		if (server == null) return true;
		final long vanillaTickTime = server.getTickTimesNanos()[server.getTickCount() % 100];
		final long timeStart = Util.getNanos();

		this.sortChunkList();

		for (int chunkIndex = 0; chunkIndex < this.pendingChunks.size(); ++chunkIndex)
		{
			long currentTime = Util.getNanos();
			long elapsedTickTime = vanillaTickTime + (currentTime - timeStart);

			if (elapsedTickTime >= 60000000L)
			{
				break;
			}

			profiler.push("process_chunk");

			ChunkPos pos = this.pendingChunks.get(chunkIndex);

			if (this.canProcessChunk(pos) && this.processChunk(pos))
			{
				this.pendingChunks.remove(chunkIndex);
				--chunkIndex;
			}

			profiler.pop();
		}

		if (this.pendingChunks.isEmpty())
		{
			this.finished = true;
			profiler.pop();
			return true;
		}

		this.updateInfoHudLines();

		profiler.pop();
		return false;
	}

	@Override
	protected boolean processChunk(ChunkPos pos)
	{
		// TODO ignoreBlocks and ignoreEntities

		// New list to avoid CME
		ArrayList<SchematicPlacement> placements = new ArrayList<>(this.placementsPerChunk.get(pos));

		for (SchematicPlacement placement : placements)
		{
			if (SchematicPlacingUtils.placeToWorldWithinChunk(this.context.level(), pos, placement, this.replaceBehavior, this.layerBehavior, this.layerRange, false))
			{
				this.placementsPerChunk.remove(pos, placement);
			}
		}

		return this.placementsPerChunk.containsKey(pos) == false;
	}

	@Override
	protected void onStop()
	{
		if (this.finished)
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.paste.successful"));
		}
		else
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.paste.failed"));
		}

//		InfoHud.getInstance().removeInfoHudRenderer(this, false);

		super.onStop();
	}
}
