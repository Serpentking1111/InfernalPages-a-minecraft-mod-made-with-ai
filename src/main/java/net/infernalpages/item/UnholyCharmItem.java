package net.infernalpages.item;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * The Unholy Charm. Works like the Revival Charm, but it is bound to a <b>doomed</b> player (B).
 * While the holder types that player's name in chat, the doomed player is killed permanently by a
 * single lightning bolt (no explosion) and the charm is consumed.
 *
 * <p>The chat behaviour is implemented in {@link net.infernalpages.revive.ReviveChatHandler}; the
 * target player is stored in {@link net.infernalpages.registry.ModComponents#UNHOLY_TARGET}.
 */
public class UnholyCharmItem extends Item {
	public UnholyCharmItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		String owner = stack.get(net.infernalpages.registry.ModComponents.UNHOLY_OWNER_NAME);
		String target = stack.get(net.infernalpages.registry.ModComponents.UNHOLY_TARGET_NAME);
		if (owner != null) {
			tooltip.accept(Text.literal("Bound to: " + owner).formatted(Formatting.GOLD));
		}
		if (target != null) {
			tooltip.accept(Text.literal("Doomed soul: " + target).formatted(Formatting.DARK_PURPLE));
		} else {
			tooltip.accept(Text.translatable("item.infernalpages.unholy_charm.tooltip").formatted(Formatting.DARK_PURPLE));
		}
	}
}
