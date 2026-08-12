package net.infernalpages.rei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.List;
import java.util.Optional;

/**
 * REI display for an Infernal Pages ritual: one thrown ingredient converted into one reward.
 * The four candles are shown as a visual hint by the category (not as separate recipe slots).
 */
public class RitualRecipeDisplay extends BasicDisplay {
	public static final CategoryIdentifier<RitualRecipeDisplay> CATEGORY =
			CategoryIdentifier.of("infernalpages", "ritual");

	public RitualRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs) {
		super(inputs, outputs);
	}

	public RitualRecipeDisplay(List<EntryIngredient> inputs, List<EntryIngredient> outputs, Optional<net.minecraft.util.Identifier> location) {
		super(inputs, outputs, location);
	}

	@Override
	public CategoryIdentifier<?> getCategoryIdentifier() {
		return CATEGORY;
	}

	@Override
	public DisplaySerializer<? extends RitualRecipeDisplay> getSerializer() {
		return SERIALIZER;
	}

	private static final DisplaySerializer<RitualRecipeDisplay> SERIALIZER = DisplaySerializer.of(
			RecordCodecBuilder.mapCodec(instance ->
					instance.group(
							EntryIngredient.codec().listOf().fieldOf("inputs")
									.forGetter(RitualRecipeDisplay::getInputEntries),
							EntryIngredient.codec().listOf().fieldOf("outputs")
									.forGetter(RitualRecipeDisplay::getOutputEntries)
					).apply(instance, RitualRecipeDisplay::new)),
			PacketCodec.tuple(
					EntryIngredient.streamCodec().collect(PacketCodecs.toList()), RitualRecipeDisplay::getInputEntries,
					EntryIngredient.streamCodec().collect(PacketCodecs.toList()), RitualRecipeDisplay::getOutputEntries,
					RitualRecipeDisplay::new
			)
	);
}
