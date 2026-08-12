package net.infernalpages.recipe;

import com.mojang.datafixers.util.Pair;
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
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A custom {@link RecipeSerializer} for the Mould of Souls crafting recipe.
 *
 * <p>Some recipe-handling mods override the vanilla {@link Ingredient} codec so that standard
 * {@code {"item": "..."}} recipe JSON fails to parse ("No key fabric:type"). This serializer builds
 * its ingredients directly from item ids via {@link Ingredient#ofItem}, so it is immune to that
 * override. The network packet codec is reused from the vanilla shaped-recipe serializer so the
 * recipe syncs to clients correctly.
 */
public final class MouldCraftingSerializer implements RecipeSerializer<ShapedRecipe> {
	public static final MouldCraftingSerializer INSTANCE = new MouldCraftingSerializer();

	private MouldCraftingSerializer() {
	}

	@Override
	public MapCodec<ShapedRecipe> codec() {
		return new MapCodec<>() {
			@Override
			public <T> DataResult<ShapedRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
				try {
					// Read the pattern (list of row strings).
					T patternEl = input.get("pattern");
					if (patternEl == null) {
						return DataResult.error(() -> "missing 'pattern'");
					}
					List<String> pattern = new ArrayList<>();
					for (T row : ops.getStream(patternEl)
							.result().orElseThrow(() -> new RuntimeException("'pattern' must be a list")).toList()) {
						pattern.add(ops.getStringValue(row).result()
								.orElseThrow(() -> new RuntimeException("'pattern' row must be a string")));
					}

					// Read the keys (character -> item id) and build ingredients directly.
					Map<Character, Ingredient> keys = new LinkedHashMap<>();
					T keysEl = input.get("keys");
					if (keysEl == null) {
						return DataResult.error(() -> "missing 'keys'");
					}
					for (Pair<T, T> entry : ops.getMapValues(keysEl)
							.result().orElseThrow(() -> new RuntimeException("'keys' must be an object")).toList()) {
						String charStr = ops.getStringValue(entry.getFirst())
								.result().orElseThrow(() -> new RuntimeException("key must be a single character"));
						if (charStr.length() != 1) {
							return DataResult.error(() -> "key must be a single character: " + charStr);
						}
						String itemId = ops.getStringValue(entry.getSecond())
								.result().orElseThrow(() -> new RuntimeException("key value must be an item id"));
						Item item = Registries.ITEM.get(Identifier.of(itemId));
						keys.put(charStr.charAt(0), Ingredient.ofItem(item));
					}

					// Read the result item id.
					T resultEl = input.get("result");
					if (resultEl == null) {
						return DataResult.error(() -> "missing 'result'");
					}
					String resultId = ops.getStringValue(resultEl)
							.result().orElseThrow(() -> new RuntimeException("'result' must be an item id"));
					Item resultItem = Registries.ITEM.get(Identifier.of(resultId));

					RawShapedRecipe raw = RawShapedRecipe.create(keys, pattern.toArray(new String[0]));
					ShapedRecipe recipe = new ShapedRecipe("", CraftingRecipeCategory.MISC, raw,
							new ItemStack(resultItem), true);
					return DataResult.success(recipe);
				} catch (Exception e) {
					return DataResult.error(() -> "Invalid mould crafting recipe: " + e.getMessage());
				}
			}

			@Override
			public <T> RecordBuilder<T> encode(ShapedRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				// Not used for runtime loading; leave the record empty.
				return prefix;
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.of(ops.createString("pattern"), ops.createString("keys"), ops.createString("result"));
			}
		};
	}

	@Override
	public PacketCodec<RegistryByteBuf, ShapedRecipe> packetCodec() {
		// Reuse the vanilla shaped-recipe packet codec so the recipe syncs correctly to clients.
		return ShapedRecipe.Serializer.PACKET_CODEC;
	}
}
