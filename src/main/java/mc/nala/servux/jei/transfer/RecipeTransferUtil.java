package mc.nala.servux.jei.transfer;

import mc.nala.servux.jei.util.StringUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RecipeTransferUtil {
	private static final Logger LOGGER = LogManager.getLogger();

	private RecipeTransferUtil() {
	}

	public static boolean validateSlots(
		Player player,
		Collection<TransferOperation> transferOperations,
		Collection<Slot> craftingSlots,
		Collection<Slot> inventorySlots
	) {
		AbstractContainerMenu container = player.containerMenu;
		List<Integer> invalidOperationSlotIndexes = transferOperations.stream()
			.flatMap(op -> Stream.of(op.inventorySlotId(), op.craftingSlotId()))
			.distinct()
			.filter(slotId -> !isValidSlotId(container, slotId))
			.toList();
		if (!invalidOperationSlotIndexes.isEmpty()) {
			LOGGER.error(
				"Transfer request has invalid slot ids in its transfer operations: {}",
				StringUtil.intsToString(invalidOperationSlotIndexes)
			);
			return false;
		}

		Set<Integer> inventorySlotIndexes = inventorySlots.stream()
			.map(s -> s.index)
			.collect(Collectors.toSet());
		Set<Integer> craftingSlotIndexes = craftingSlots.stream()
			.map(s -> s.index)
			.collect(Collectors.toSet());

		// check that all craftingTargetSlots are included in craftingSlots
		{
			List<Integer> invalidRecipeIndexes = transferOperations.stream()
				.map(op -> op.craftingSlot(player.containerMenu))
				.map(s -> s.index)
				.filter(s -> !craftingSlotIndexes.contains(s))
				.toList();
			if (!invalidRecipeIndexes.isEmpty()) {
				LOGGER.error(
					"Transfer request has invalid slots for the destination of the recipe,  the slots are not included in the list of crafting slots. {}",
					StringUtil.intsToString(invalidRecipeIndexes)
				);
				return false;
			}
		}

		// check that all ingredientTargetSlots are included in inventorySlots or recipeSlots
		{
			List<Integer> invalidInventorySlotIndexes = transferOperations.stream()
				.map(op -> op.inventorySlot(player.containerMenu))
				.map(s -> s.index)
				.filter(s -> !inventorySlotIndexes.contains(s) && !craftingSlotIndexes.contains(s))
				.toList();
			if (!invalidInventorySlotIndexes.isEmpty()) {
				LOGGER.error(
					"Transfer request has invalid source slots for the inventory stacks for the recipe, the slots are not included in the list of inventory slots or recipe slots. {}\n inventory slots: {}\n crafting slots: {}",
					StringUtil.intsToString(invalidInventorySlotIndexes),
					StringUtil.intsToString(inventorySlotIndexes),
					StringUtil.intsToString(craftingSlotIndexes)
				);
				return false;
			}
		}

		// check that crafting slots and inventory slots do not overlap
		{
			Set<Integer> overlappingSlots = inventorySlotIndexes.stream()
				.filter(craftingSlotIndexes::contains)
				.collect(Collectors.toSet());
			if (!overlappingSlots.isEmpty()) {
				LOGGER.error(
					"Transfer request has invalid slots, inventorySlots and craftingSlots should not share any slot, but both have: {}",
					StringUtil.intsToString(overlappingSlots)
				);
				return false;
			}
		}

		// check that all slots are real (not output slots)
		{
			List<Integer> invalidFakeSlots = Stream.concat(
					craftingSlots.stream(),
					inventorySlots.stream()
				)
				.filter(Slot::isFake)
				.map(slot -> slot.index)
				.toList();
			if (!invalidFakeSlots.isEmpty()) {
				LOGGER.error(
					"Transfer request has invalid slots, they are fake slots (recipe outputs): {}",
					StringUtil.intsToString(invalidFakeSlots)
				);
				return false;
			}
		}

		return true;
	}

	private static boolean isValidSlotId(AbstractContainerMenu container, int slotId) {
		return slotId >= 0 && slotId < container.slots.size();
	}

	/**
	 * Returns a list of items in slots that complete the recipe defined by requiredStacksList.
	 * Returns a result that contains missingItems if there are not enough items in availableItemStacks.
	 */
}
