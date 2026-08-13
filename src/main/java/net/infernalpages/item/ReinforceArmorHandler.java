package net.infernalpages.item;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;

/**
 * Re-enables the <b>Tainted-reinforced armour</b> mechanic. Hold a Tainted Shard in one hand and an
 * armour piece in the other and right-click: the armour is reinforced (gains the {@link
 * ModComponents#TAINTED} component, granting the one-hit shield via {@code TaintedArmorHandler}) and
 * the shard is consumed.
 *
 * <p>This is implemented at runtime (not via a smithing recipe) so it is immune to the datapack
 * tag-loading crash that forced the original recipe to be removed.
 */
public final class ReinforceArmorHandler {
	private ReinforceArmorHandler() {
	}

	public static void register() {
		UseItemCallback.EVENT.register(ReinforceArmorHandler::onUse);
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack used = player.getStackInHand(hand);
		ItemStack other = player.getStackInHand(otherHand);

		ItemStack shard;
		ItemStack armour;
		boolean shardInOther = false;
		if (used.getItem() == ModItems.TAINTED_SHARD && isArmor(other)) {
			shard = used;
			armour = other;
			shardInOther = false;
		} else if (other.getItem() == ModItems.TAINTED_SHARD && isArmor(used)) {
			shard = other;
			armour = used;
			shardInOther = true;
		} else {
			return ActionResult.PASS;
		}

		// Already reinforced: don't waste the shard.
		if (Boolean.TRUE.equals(armour.get(ModComponents.TAINTED))) {
			serverPlayer.sendMessage(Text.literal("This armour is already reinforced.")
					.formatted(Formatting.GRAY), true);
			return ActionResult.PASS;
		}

		armour.set(ModComponents.TAINTED, true);
		shard.decrement(1);
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
		serverPlayer.sendMessage(Text.literal("Armour reinforced with a Tainted Shard.")
				.formatted(Formatting.DARK_PURPLE), true);
		return ActionResult.SUCCESS;
	}

	/** True if the stack is a wearable piece of armour (helmet, chest, legs or boots). */
	private static boolean isArmor(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable == null) {
			return false;
		}
		EquipmentSlot slot = equippable.slot();
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
				|| slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}
}
