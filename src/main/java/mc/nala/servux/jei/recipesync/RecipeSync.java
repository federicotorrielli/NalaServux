package mc.nala.servux.jei.recipesync;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import io.netty.buffer.Unpooled;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;

import mc.nala.servux.jei.ModIds;
import mc.nala.servux.paper.PaperNetwork;

/** Fabric API RecipeSyncImpl.sendRecipes for the minecraft serializers (the set JEI registers). */
public final class RecipeSync {
	private RecipeSync() {
	}

	public static boolean canSend(ServerPlayer player) {
		return player.getBukkitEntity().getListeningPluginChannels().contains(ClientboundRecipeSyncPayload.CHANNEL.toString());
	}

	public static void sendRecipes(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Map<RecipeSerializer<?>, List<RecipeHolder<?>>> bySerializer = new IdentityHashMap<>();

		for (RecipeHolder<?> recipe : server.getRecipeManager().recipes.values()) {
			RecipeSerializer<?> serializer = recipe.value().getSerializer();
			Identifier key = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);

			if (key != null && key.getNamespace().equals(ModIds.MINECRAFT_ID)) {
				bySerializer.computeIfAbsent(serializer, k -> new ArrayList<>()).add(recipe);
			}
		}

		if (bySerializer.isEmpty()) {
			return;
		}

		List<ClientboundRecipeSyncPayload.Entry> entries = new ArrayList<>();
		bySerializer.forEach((serializer, recipes) -> entries.add(new ClientboundRecipeSyncPayload.Entry(serializer, recipes)));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());

		try {
			ClientboundRecipeSyncPayload.CODEC.encode(buf, new ClientboundRecipeSyncPayload(entries));
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			PaperNetwork.send(player, ClientboundRecipeSyncPayload.CHANNEL, bytes);
		} finally {
			buf.release();
		}
	}
}
