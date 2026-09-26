package mc.nala.servux.util.thread;

import mc.nala.servux.interfaces.IThreadDaemonExecutor;
import mc.nala.servux.interfaces.IThreadTaskBase;

public record ThreadExecutorPair<T extends IThreadTaskBase>(Thread thread, IThreadDaemonExecutor<T> executor)
{
}
