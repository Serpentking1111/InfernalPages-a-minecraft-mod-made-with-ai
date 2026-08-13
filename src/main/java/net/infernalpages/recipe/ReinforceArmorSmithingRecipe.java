package net.infernalpages.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.IngredientPlacement;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmithingRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A smithing recipe that reinforces any piece of armour with a Tainted Shard. Put any armour piece
 * (helmet/chestplate/leggings/boots) in the <b>base</b> slot and a Tainted Shard in the <b>addition</b>
 * slot (no template needed). The output is the same armour with the {@link ModComponents#TAINTED}
 * component applied, granting the one-hit shield.
 *
 * <p>This is implemented as a custom {@link SmithingRecipe} (not the vanilla transform type) because
 * the result must preserve whatever armour item was used as the base — something a fixed-result
 * {@code smithing_transform} recipe cannot do.
 */
public class ReinforceArmorSmithingRecipe implements SmithingRecipe {
	public static final ReinforceArmorSmithingRecipe INSTANCE = new ReinforceArmorSmithingRecipe();
	private static final Ingredient ADDITION = Ingredient.ofItem(ModItems.TAINTED_SHARD);

	// Built lazily so modded armour registered after datapack load is included, and to avoid the
	// static-initializer tag lookup that crashed the old recipe.
	private static Ingredient armorIngredient;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private ReinforceArmorSmithingRecipe() {
	}

	@Override
	public boolean matches(SmithingRecipeInput input, World world) {
		return isArmor(input.base()) && ADDITION.test(input.addition());
	}

	@Override
	public ItemStack craft(SmithingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
		ItemStack result = input.base().copy();
		result.set(ModComponents.TAINTED, true);
		return result;
	}

	@Override
	public RecipeSerializer<? extends SmithingRecipe> getSerializer() {
		return ReinforceArmorSmithingRecipeSerializer.INSTANCE;
	}

	@Override
	public Optional<Ingredient> template() {
		return Optional.empty();
	}

	@Override
	public Ingredient base() {
		return buildArmorIngredient();
	}

	@Override
	public Optional<Ingredient> addition() {
		return Optional.of(ADDITION);
	}

	@Override
	public IngredientPlacement getIngredientPlacement() {
		// Template (empty), addition (shard), base (armour).
		return IngredientPlacement.forMultipleSlots(List.of(
				Optional.empty(),     // template slot
				Optional.of(ADDITION), // addition slot
				Optional.of(base()))); // base slot
	}

	/** True if the stack is a wearable armour piece. */
	public static boolean isArmor(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable == null) {
			return false;
		}
		EquipmentSlot slot = equippable.slot();
		for (EquipmentSlot armour : ARMOR_SLOTS) {
			if (slot == armour) {
				return true;
			}
		}
		return false;
	}

	/** Builds an Ingredient matching every currently-registered armour item. */
	private static Ingredient buildArmorIngredient() {
		if (armorIngredient == null) {
			Stream<Item> armourItems = Registries.ITEM.stream()
					.filter(item -> isArmor(new ItemStack(item)));
			armorIngredient = Ingredient.ofItems(armourItems);
		}
		return armorIngredient;
	}
}
