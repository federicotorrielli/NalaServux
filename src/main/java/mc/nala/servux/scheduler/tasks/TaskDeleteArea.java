package mc.nala.servux.scheduler.tasks;

import java.util.List;

import net.minecraft.world.level.block.Blocks;

import mc.nala.servux.scheduler.TaskContext;
import mc.nala.servux.schematic.selection.Box;
import mc.nala.servux.util.StringUtils;

public class TaskDeleteArea extends TaskFillArea
{
	public TaskDeleteArea(TaskContext ctx, List<Box> boxes, boolean removeEntities)
	{
		super(ctx, boxes, Blocks.AIR.defaultBlockState(), null, removeEntities);
	}

	@Override
	protected void printCompletionMessage()
	{
		if (this.finished)
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.delete_area.successful"));
		}
		else
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.delete_area.interrupted"));
		}
	}
}
