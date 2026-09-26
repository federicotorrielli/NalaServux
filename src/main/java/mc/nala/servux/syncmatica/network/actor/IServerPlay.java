package mc.nala.servux.syncmatica.network.actor;

import mc.nala.servux.syncmatica.communication.ExchangeTarget;
import mc.nala.servux.syncmatica.communication.ServerCommunicationManager;

import java.util.function.Consumer;

public interface IServerPlay
{
    void syncmatica$operateComms(final Consumer<ServerCommunicationManager> operation);

    ExchangeTarget syncmatica$getExchangeTarget();
}
