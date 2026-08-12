package net.infernalpages.recipe;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A custom {@link RecipeSerializer} for shapeless recipes (like the mould-of-souls shaped one), built
 * directly from item ids so it is immune to other mods overriding the vanilla {@link Ingredient}
 * codec. The packet codec is reused from the vanilla shapeless serializer so the recipe syncs to
 * clients correctly.
 */
public final class MouldShapelessCraftingSerializer implements RecipeSerializer<ShapelessRecipe> {
	public static final MouldShapelessCraftingSerializer INSTANCE = new MouldShapelessCraftingSerializer();

	private MouldShapelessCraftingSerializer() {
	}

	@Override
	public MapCodec<ShapelessRecipe> codec() {
		return new MapCodec<>() {
			@Override
			public <T> DataResult<ShapelessRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
				try {
					// Read the ingredient item ids (list of strings).
					T ingEl = input.get("ingredients");
					if (ingEl == null) {
						return DataResult.error(() -> "missing 'ingredients'");
					}
					List<Ingredient> ingredients = new ArrayList<>();
					for (T row : ops.getStream(ingEl)
							.result().orElseThrow(() -> new RuntimeException("'ingredients' must be a list")).toList()) {
						String itemId = ops.getStringValue(row)
								.result().orElseThrow(() -> new RuntimeException("ingredient must be an item id"));
						Item item = Registries.ITEM.get(Identifier.of(itemId));
						ingredients.add(Ingredient.ofItem(item));
					}

					// Read the result item id.
					T resultEl = input.get("result");
					if (resultEl == null) {
						return DataResult.error(() -> "missing 'result'");
					}
					String resultId = ops.getStringValue(resultEl)
							.result().orElseThrow(() -> new RuntimeException("'result' must be an item id"));
					Item resultItem = Registries.ITEM.get(Identifier.of(resultId));

					// Optional result count (default 1).
					int count = 1;
					T countEl = input.get("count");
					if (countEl != null) {
						count = ops.getNumberValue(countEl)
								.result().orElseThrow(() -> new RuntimeException("'count' must be a number"))
								.intValue();
					}

					ShapelessRecipe recipe = new ShapelessRecipe("", CraftingRecipeCategory.MISC,
							new ItemStack(resultItem, count), ingredients);
					return DataResult.success(recipe);
				} catch (Exception e) {
					return DataResult.error(() -> "Invalid mould shapeless recipe: " + e.getMessage());
				}
			}

			@Override
			public <T> RecordBuilder<T> encode(ShapelessRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				return prefix;
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.of(ops.createString("ingredients"), ops.createString("result"));
			}
		};
	}

	@Override
	public PacketCodec<RegistryByteBuf, ShapelessRecipe> packetCodec() {
		return net.minecraft.recipe.ShapelessRecipe.Serializer.PACKET_CODEC;
	}
}
