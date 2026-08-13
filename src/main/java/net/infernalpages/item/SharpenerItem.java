package net.infernalpages.item;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * The Sharpening Stone. Right-click it (with a weapon in your other hand) to spend 3 experience
 * levels and apply a random {@link Sharpening} to the weapon. Reapplying rerolls the effect.
 */
public class SharpenerItem extends Item {
	/** The number of experience levels a sharpening costs. */
	public static final int COST_LEVELS = 3;

	public SharpenerItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(user instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		// The weapon is in the other hand from the sharpener.
		Hand otherHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
		ItemStack weapon = user.getStackInHand(otherHand);

		// Report a helpful message if there's no valid weapon.
		if (weapon.isEmpty() || !Sharpening.isWeapon(weapon)) {
			serverPlayer.sendMessage(Text.literal("Hold the weapon to sharpen in your other hand.")
					.formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}
		if (serverPlayer.experienceLevel < COST_LEVELS) {
			serverPlayer.sendMessage(Text.literal("You need " + COST_LEVELS
					+ " experience levels to sharpen.").formatted(Formatting.RED), true);
			return ActionResult.FAIL;
		}

		// Spend the cost and apply a random sharpening (rerolls any existing effect).
		serverPlayer.addExperienceLevels(-COST_LEVELS);
		Sharpening roll = Sharpening.roll(serverPlayer.getRandom());
		roll.applyToStack(weapon);
		serverPlayer.sendMessage(Text.literal("Sharpened: " + roll.displayName())
				.formatted(Formatting.DARK_PURPLE), true);
		return ActionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.translatable("item.infernalpages.sharpener.tooltip").formatted(Formatting.GRAY));
		tooltip.accept(Text.literal("Costs " + COST_LEVELS + " experience levels per use.")
				.formatted(Formatting.DARK_GRAY));
	}
}
