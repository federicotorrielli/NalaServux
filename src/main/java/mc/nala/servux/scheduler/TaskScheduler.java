package mc.nala.servux.scheduler;

import java.util.ArrayList;
import java.util.List;
import com.google.common.collect.ImmutableList;

import net.minecraft.util.profiling.ProfilerFiller;

import mc.nala.servux.Servux;

public class TaskScheduler
{
	private static final TaskScheduler INSTANCE = new TaskScheduler();
	public static TaskScheduler getInstance() { return INSTANCE; }

	private final List<ITask> tasks = new ArrayList<>();
	private final List<ITask> tasksToAdd = new ArrayList<>();

	public void scheduleTask(ITask task, int interval)
	{
		synchronized (this)
		{
			task.createTimer(interval);
			task.getTimer().setNextDelay(0);

			if (this.tasks.isEmpty())
			{
				this.initAndAdd(task);
			}
			else
			{
				// Adds Delay
				this.tasksToAdd.add(task);
			}
		}
	}

	public void runTasks(ProfilerFiller profiler)
	{
		profiler.push("run_tasks");
		synchronized (this)
		{
			if (this.tasks.isEmpty() == false)
			{
				for (int i = 0; i < this.tasks.size(); ++i)
				{
					boolean finished = false;
					ITask task = this.tasks.get(i);

					// A throwing task is dropped; upstream rethrew it every tick and starved the rest.
					try
					{
						if (task.shouldRemove())
						{
							finished = true;
						}
						else if (task.canExecute() && task.getTimer().tick())
						{
							finished = task.execute(profiler);
						}
					}
					catch (Exception e)
					{
						Servux.LOGGER.error("TaskScheduler: task '{}' failed, removing it", task.getDisplayName(), e);
						finished = true;
					}

					if (finished)
					{
						task.stop();
						this.tasks.remove(i);
						--i;
					}
				}
			}

			if (this.tasksToAdd.isEmpty() == false)
			{
				this.addNewTasks();
			}
		}

		profiler.pop();
	}

	private void addNewTasks()
	{
		for (ITask task : this.tasksToAdd)
		{
			this.initAndAdd(task);
		}

		this.tasksToAdd.clear();
	}

	private void initAndAdd(ITask task)
	{
		try
		{
			task.init();
			this.tasks.add(task);
		}
		catch (Exception e)
		{
			Servux.LOGGER.error("TaskScheduler: task '{}' failed to start", task.getDisplayName(), e);
		}
	}

	public boolean hasTask(Class <? extends ITask> clazz)
	{
		synchronized (this)
		{
			for (ITask task : this.tasks)
			{
				if (clazz.equals(task.getClass()))
				{
					return true;
				}
			}

			for (ITask task : this.tasksToAdd)
			{
				if (clazz.equals(task.getClass()))
				{
					return true;
				}
			}

			return false;
		}
	}

	public ImmutableList<ITask> getAllTasks()
	{
		return ImmutableList.copyOf(this.tasks);
	}

	public boolean removeTask(ITask task)
	{
		synchronized (this)
		{
			task.stop();
			return this.tasks.remove(task);
		}
	}

	public void clearTasks()
	{
		synchronized (this)
		{
			for (int i = 0; i < this.tasks.size(); ++i)
			{
				ITask task = this.tasks.get(i);
				task.stop();
			}

			this.tasks.clear();
		}
	}
}
