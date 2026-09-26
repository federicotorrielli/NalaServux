package mc.nala.servux.syncmatica.command;

import java.util.function.Predicate;
import javax.annotation.Nonnull;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

import mc.nala.servux.util.PermissionsUtil;

public class PermsWrap
{
	public static Predicate<CommandSourceStack> check(@Nonnull String node, PermissionLevel level)
	{
		return PermissionsUtil.require(node, level.id());
	}

	public static Predicate<CommandSourceStack> check(@Nonnull String node, int level)
	{
		return PermissionsUtil.require(node, level);
	}
}
