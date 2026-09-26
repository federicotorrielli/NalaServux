package mc.nala.servux.interfaces;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import java.net.SocketAddress;
import java.util.UUID;
import com.mojang.authlib.GameProfile;

public interface IPlayerListener
{
    default void onPlayerJoin(SocketAddress addr, GameProfile profile, ServerPlayer player) {}
    default void onPlayerLeave(ServerPlayer player) {}
}
