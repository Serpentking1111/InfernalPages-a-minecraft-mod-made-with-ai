package net.infernalpages.item;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * The Scripture. This item is purely a "talisman": it does not change the damage it deals.
 * The kill behaviour is implemented in {@link net.infernalpages.death.KillHandler}, which
 * checks whether the killer is holding this in either hand.
 */
public class ScriptureItem extends Item {
	public ScriptureItem(Settings settings) {
		super(settings);
	}

	/** Renders the item's name in gold. */
	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(this.getTranslationKey()).formatted(Formatting.GOLD);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.translatable("item.infernalpages.scripture.tooltip").formatted(Formatting.DARK_RED));
		tooltip.accept(Text.translatable("item.infernalpages.scripture.tooltip2").formatted(Formatting.GRAY));
	}
}
