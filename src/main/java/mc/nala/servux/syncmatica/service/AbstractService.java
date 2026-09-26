package mc.nala.servux.syncmatica.service;

import mc.nala.servux.syncmatica.Context;

abstract class AbstractService implements IService {

    Context context;

    @Override
    public void setContext(final Context context) {
        this.context = context;
    }

    @Override
    public Context getContext() {
        return context;
    }
}
