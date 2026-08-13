package net.infernalpages.item;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Lets a player sharpen a weapon by holding the Sharpening Stone in their <b>off hand</b> and
 * right-clicking with the weapon in their main hand. (The sharpener's own {@code use} handles the
 * reverse arrangement: sharpener in the main hand, weapon in the off hand.)
 */
public final class SharpeningHandler {
	private SharpeningHandler() {
	}

	public static void register() {
		UseItemCallback.EVENT.register(SharpeningHandler::onUse);
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		// Only intercept when the sharpener is in the off hand and the player right-clicks with the
		// weapon in the main hand.
		if (!(serverPlayer.getOffHandStack().getItem() instanceof SharpenerItem)) {
			return ActionResult.PASS;
		}
		ItemStack weapon = serverPlayer.getMainHandStack();

		if (weapon.isEmpty() || !Sharpening.isWeapon(weapon)) {
			serverPlayer.sendMessage(Text.literal("Hold the weapon to sharpen in your main hand.")
					.formatted(Formatting.RED), true);
			return ActionResult.PASS;
		}
		if (serverPlayer.experienceLevel < SharpenerItem.COST_LEVELS) {
			serverPlayer.sendMessage(Text.literal("You need " + SharpenerItem.COST_LEVELS
					+ " experience levels to sharpen.").formatted(Formatting.RED), true);
			return ActionResult.PASS;
		}

		serverPlayer.addExperienceLevels(-SharpenerItem.COST_LEVELS);
		Sharpening roll = Sharpening.roll(serverPlayer.getRandom());
		roll.applyToStack(weapon);
		serverPlayer.sendMessage(Text.literal("Sharpened: " + roll.displayName())
				.formatted(Formatting.DARK_PURPLE), true);
		return ActionResult.SUCCESS;
	}
}
