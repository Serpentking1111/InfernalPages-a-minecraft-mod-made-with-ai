package net.infernalpages.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;

import java.util.List;
import java.util.Map;

/**
 * REI (Roughly Enough Items) integration for Infernal Pages' ritual.
 *
 * <p>This class is only loaded by REI via the {@code rei_client} entrypoint, so it is safe to ship
 * inside the main mod even when REI is not installed. Each {@code ritualIngredients} entry in the
 * config becomes a ritual recipe display: the thrown ingredient on one side, the reward on the
 * other, with four candles shown around the input.
 */
public class InfernalPagesREIPlugin implements REIClientPlugin {
	@Override
	public void registerCategories(CategoryRegistry registry) {
		registry.add(new RitualRecipeCategory());
	}

	@Override
	public void registerDisplays(DisplayRegistry registry) {
		Map<String, String> ingredients = InfernalPagesMod.CONFIG.ritualIngredients;
		for (Map.Entry<String, String> entry : ingredients.entrySet()) {
			Item input = Registries.ITEM.get(Identifier.of(entry.getKey()));
			Item output = Registries.ITEM.get(Identifier.of(entry.getValue()));
			if (input == Items.AIR || output == Items.AIR) {
				continue;
			}
			EntryIngredient in = EntryIngredients.ofItemStacks(List.of(new ItemStack(input)));
			EntryIngredient out = EntryIngredients.ofItemStacks(List.of(new ItemStack(output)));
			registry.add(new RitualRecipeDisplay(List.of(in), List.of(out)));
		}
	}
}
