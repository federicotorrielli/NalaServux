package mc.nala.servux.commands;

import mc.nala.servux.interfaces.IServerCommand;

public interface ICommandProvider
{
    void registerCommand(IServerCommand command);
    void unregisterCommand(IServerCommand command);
}
