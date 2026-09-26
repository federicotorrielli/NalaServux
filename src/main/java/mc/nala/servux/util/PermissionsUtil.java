package mc.nala.servux.util;

import java.util.function.Predicate;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Same semantics as fabric-permissions-api: an explicitly set node decides,
 * otherwise the vanilla permission level decides.
 */
public class PermissionsUtil
{
	public static @NotNull Predicate<CommandSourceStack> require(@NotNull String node, int level)
	{
		final PermissionLevel permissionLevel = toLevel(level);
		return source -> check(source.getBukkitSender(), source.permissions(), node, permissionLevel);
	}

	public static boolean check(Entity entity, @NotNull String node, int level)
	{
		PermissionSet permissions = entity instanceof Player player ? player.permissions() : PermissionSet.NO_PERMISSIONS;
		return check(entity.getBukkitEntity(), permissions, node, toLevel(level));
	}

	private static boolean check(@Nullable CommandSender sender, PermissionSet permissions, String node, PermissionLevel level)
	{
		if (sender != null && sender.isPermissionSet(node))
		{
			return sender.hasPermission(node);
		}

		return permissions.hasPermission(new Permission.HasCommandLevel(level));
	}

	private static PermissionLevel toLevel(int level)
	{
		return PermissionLevel.byId(Mth.clamp(level, 0, PermissionLevel.OWNERS.id()));
	}
}
