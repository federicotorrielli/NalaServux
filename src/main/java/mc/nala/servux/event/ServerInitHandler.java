package mc.nala.servux.event;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import mc.nala.servux.interfaces.IServerInitDispatcher;
import mc.nala.servux.interfaces.IServerInitHandler;

public class ServerInitHandler implements IServerInitDispatcher
{
    private static final ServerInitHandler INSTANCE = new ServerInitHandler();
    public static IServerInitDispatcher getInstance() { return INSTANCE; }
    private final List<IServerInitHandler> handlers = new ArrayList<>();

    @Override
    public void registerServerInitHandler(IServerInitHandler handler)
    {
        if (!this.handlers.contains(handler))
        {
            this.handlers.add(handler);
        }
    }

    @ApiStatus.Internal
    public void onServerInit()
    {
        if (!this.handlers.isEmpty())
        {
            for (IServerInitHandler handler : this.handlers)
            {
                handler.onServerInit();
            }
        }
    }
}
