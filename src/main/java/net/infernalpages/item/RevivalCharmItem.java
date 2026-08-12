package net.infernalpages.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.infernalpages.registry.ModItems;

import java.util.function.Consumer;

/**
 * The Revival Charm. While this is held in either hand, typing a banished player's name in chat
 * removes them from the mod's ban file (revives them). It is single use.
 *
 * <p>It can also be <b>smashed</b>: right-clicking on a block with a blast resistance greater than
 * stone's shatters the charm into a {@link ModItems#PURITY_SEAL}.
 *
 * <p>The chat behaviour is implemented in {@link net.infernalpages.revive.ReviveChatHandler}.
 */
public class RevivalCharmItem extends Item {
	public RevivalCharmItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		Block block = context.getWorld().getBlockState(context.getBlockPos()).getBlock();
		// Only blocks tougher than stone shatter the charm.
		if (block.getBlastResistance() <= Blocks.STONE.getBlastResistance()) {
			return ActionResult.PASS;
		}

		if (context.getPlayer() instanceof ServerPlayerEntity player) {
			ItemStack stack = context.getStack();
			stack.decrement(1);
			// Play the glass-breaking sound as the charm shatters.
			player.getEntityWorld().playSound(null,
					player.getX(), player.getY(), player.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK,
					net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
			player.giveItemStack(new ItemStack(ModItems.PURITY_SEAL));
			player.sendMessage(Text.literal("The charm shatters, leaving a Purity Seal.")
					.formatted(Formatting.LIGHT_PURPLE), false);
			return ActionResult.SUCCESS_SERVER;
		}
		return ActionResult.PASS;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.translatable("item.infernalpages.revival_charm.tooltip").formatted(Formatting.GOLD));
	}
}
