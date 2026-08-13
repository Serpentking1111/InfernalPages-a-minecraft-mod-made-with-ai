package net.infernalpages.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Small helper for consuming the mod's single-use items.
 */
public final class ItemUtil {
	private ItemUtil() {
	}

	/**
	 * Removes a single held copy of the given item. The main hand takes priority over the
	 * off-hand when both are held, so the item is consumed from the main hand in that case.
	 * Copies stored elsewhere in the inventory are left untouched.
	 *
	 * @return true if a copy was removed (i.e. the item was held)
	 */
	public static boolean removeHeldItem(ServerPlayerEntity player, Item item) {
		ItemStack main = player.getMainHandStack();
		ItemStack off = player.getOffHandStack();

		// Main hand first, then off-hand.
		if (main.isOf(item)) {
			main.decrement(1);
			return true;
		}
		if (off.isOf(item)) {
			off.decrement(1);
			return true;
		}
		return false;
	}
}
