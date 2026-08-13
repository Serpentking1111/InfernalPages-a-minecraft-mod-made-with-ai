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
import net.infernalpages.health.HealthPenaltyManager;

import java.util.function.Consumer;

/**
 * The Purity Seal. Right-clicking while holding it resets the player's max health to normal,
 * removing the permanent max-health penalty accumulated from rituals. Single use.
 */
public class PuritySealItem extends Item {
	public PuritySealItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) user;
		ItemStack stack = player.getStackInHand(hand);

		HealthPenaltyManager.resetPenalty(player);

		// Consume the seal.
		stack.decrement(1);

		player.sendMessage(Text.literal("Your soul is cleansed — your hearts are restored.")
				.formatted(Formatting.LIGHT_PURPLE), false);
		return ActionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.translatable("item.infernalpages.purity_seal.tooltip").formatted(Formatting.LIGHT_PURPLE));
	}
}
