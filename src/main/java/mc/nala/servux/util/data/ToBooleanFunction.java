package mc.nala.servux.util.data;

@FunctionalInterface
public interface ToBooleanFunction<R>
{
    boolean applyAsBoolean(R value);
}
