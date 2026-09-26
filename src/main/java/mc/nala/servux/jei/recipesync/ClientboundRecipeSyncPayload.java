package mc.nala.servux.jei.recipesync;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Wire format of Fabric API's net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload (write side only).
 */
public record ClientboundRecipeSyncPayload(List<Entry> entries) {
	public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("fabric", "recipe_sync");
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRecipeSyncPayload> CODEC =
		Entry.CODEC.apply(ByteBufCodecs.list()).map(ClientboundRecipeSyncPayload::new, ClientboundRecipeSyncPayload::entries);

	public record Entry(RecipeSerializer<?> serializer, List<RecipeHolder<?>> recipes) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.ofMember(Entry::write, buf -> {
			throw new UnsupportedOperationException("The server never reads recipe sync payloads");
		});

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeIdentifier(BuiltInRegistries.RECIPE_SERIALIZER.getKey(this.serializer));
			buf.writeVarInt(this.recipes.size());
			@SuppressWarnings({"unchecked", "deprecation"})
			StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> serializer = (StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) this.serializer.streamCodec();

			for (RecipeHolder<?> recipe : this.recipes) {
				buf.writeResourceKey(recipe.id());
				serializer.encode(buf, recipe.value());
			}
		}
	}
}
