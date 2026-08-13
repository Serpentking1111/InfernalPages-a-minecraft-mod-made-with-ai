package net.infernalpages.recipe;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.RecipeSerializer;

import java.util.stream.Stream;

/**
 * {@link RecipeSerializer} for {@link ReinforceArmorSmithingRecipe}. The recipe has no parameters
 * (it always reinforces any armour with a Tainted Shard), so its codec simply returns the singleton
 * instance and the packet codec is a no-op (the recipe is deterministic and needs no synced data).
 */
public final class ReinforceArmorSmithingRecipeSerializer implements RecipeSerializer<ReinforceArmorSmithingRecipe> {
	public static final ReinforceArmorSmithingRecipeSerializer INSTANCE = new ReinforceArmorSmithingRecipeSerializer();

	private ReinforceArmorSmithingRecipeSerializer() {
	}

	@Override
	public MapCodec<ReinforceArmorSmithingRecipe> codec() {
		return new MapCodec<>() {
			@Override
			public <T> DataResult<ReinforceArmorSmithingRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
				return DataResult.success(ReinforceArmorSmithingRecipe.INSTANCE);
			}

			@Override
			public <T> RecordBuilder<T> encode(ReinforceArmorSmithingRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
				return prefix;
			}

			@Override
			public <T> Stream<T> keys(DynamicOps<T> ops) {
				return Stream.empty();
			}
		};
	}

	@Override
	public PacketCodec<RegistryByteBuf, ReinforceArmorSmithingRecipe> packetCodec() {
		// No synced state: encode nothing, decode the singleton.
		return PacketCodec.ofStatic((buf, recipe) -> {
		}, buf -> ReinforceArmorSmithingRecipe.INSTANCE);
	}
}
