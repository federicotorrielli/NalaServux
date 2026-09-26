package mc.nala.servux.syncmatica.data;

import java.nio.file.Path;

import mc.nala.servux.syncmatica.Context;

public interface IFileStorage
{
    LocalLitematicState getLocalState(ServerPlacement placement);

    Path createLocalLitematic(ServerPlacement placement);

    Path getLocalLitematic(ServerPlacement placement);

    void setContext(Context con);
}
