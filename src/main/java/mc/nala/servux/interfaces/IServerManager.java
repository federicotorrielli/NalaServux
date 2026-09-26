package mc.nala.servux.interfaces;

public interface IServerManager
{
    void registerServerHandler(IServerListener handler);
    void unregisterServerHandler(IServerListener handler);
}
