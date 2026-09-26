package mc.nala.servux.interfaces;

public interface IPlayerManager
{
    void registerPlayerHandler(IPlayerListener handler);
    void unregisterPlayerHandler(IPlayerListener handler);
}
