package mc.nala.servux.settings;

public interface IServuxSettingCallback<T>
{
    void onValueChanged(IServuxSetting<T> setting, T oldValue, T value);
}
