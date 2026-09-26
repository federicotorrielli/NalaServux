package mc.nala.servux.servux;

import mc.nala.servux.dataproviders.*;
import mc.nala.servux.event.PlayerHandler;
import mc.nala.servux.event.ServerHandler;
import mc.nala.servux.interfaces.IServerInitHandler;

public class ServuxInitHandler implements IServerInitHandler
{
    @Override
    public void onServerInit()
    {
        DataProviderManager.INSTANCE.registerDataProvider(ServuxConfigProvider.INSTANCE);
        DataProviderManager.INSTANCE.registerDataProvider(StructureDataProvider.INSTANCE);
        DataProviderManager.INSTANCE.registerDataProvider(HudDataProvider.INSTANCE);
        DataProviderManager.INSTANCE.registerDataProvider(LitematicsDataProvider.INSTANCE);
        DataProviderManager.INSTANCE.registerDataProvider(EntitiesDataProvider.INSTANCE);
        DataProviderManager.INSTANCE.registerDataProvider(TweaksDataProvider.INSTANCE);

        ServerHandler.getInstance().registerServerHandler(new ServerListener());
        PlayerHandler.getInstance().registerPlayerHandler(new PlayerListener());
    }
}
