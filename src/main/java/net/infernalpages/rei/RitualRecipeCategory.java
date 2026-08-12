package net.infernalpages.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The ritual recipe category. Layout:
 *
 * <pre>
 *          [Candle]
 *  [Candle] [Input] [Candle]  -->  [Output]
 *          [Candle]
 * </pre>
 *
 * Four candles surround the single thrown ingredient, and an arrow points to the reward — like a
 * furnace with one input slot and candles around it.
 */
public class RitualRecipeCategory implements DisplayCategory<RitualRecipeDisplay> {
	/** All candle variants used as the visual "requirement" around the input. */
	private static final EntryIngredient CANDLES = me.shedaniel.rei.api.common.util.EntryIngredients.ofItems(
			List.of(Items.CANDLE, Items.WHITE_CANDLE, Items.ORANGE_CANDLE, Items.MAGENTA_CANDLE,
					Items.LIGHT_BLUE_CANDLE, Items.YELLOW_CANDLE, Items.LIME_CANDLE, Items.PINK_CANDLE,
					Items.GRAY_CANDLE, Items.LIGHT_GRAY_CANDLE, Items.CYAN_CANDLE, Items.PURPLE_CANDLE,
					Items.BLUE_CANDLE, Items.BROWN_CANDLE, Items.GREEN_CANDLE, Items.RED_CANDLE, Items.BLACK_CANDLE));

	private static final int SLOT = 18;
	private static final int CANDLE_OFFSET = 20;                  // distance from input to a candle (spaced out)
	private static final int INPUT_X = 20, INPUT_Y = 30;          // centre of the cross (moved left)
	private static final int ARROW_X = 46, ARROW_Y = 26;          // arrow, shifted left
	private static final int OUTPUT_X = 70, OUTPUT_Y = 22;        // output, fixed (right)

	@Override
	public CategoryIdentifier<? extends RitualRecipeDisplay> getCategoryIdentifier() {
		return RitualRecipeDisplay.CATEGORY;
	}

	@Override
	public Text getTitle() {
		return Text.translatable("category.infernalpages.ritual");
	}

	@Override
	public Renderer getIcon() {
		return EntryStacks.of(new ItemStack(Items.CANDLE));
	}

	@Override
	public int getDisplayWidth(RitualRecipeDisplay display) {
		return OUTPUT_X + SLOT + 4;
	}

	@Override
	public int getDisplayHeight() {
		// Room for the whole cross: top candle .. bottom candle (incl. its slot).
		return (INPUT_Y + CANDLE_OFFSET + SLOT + 4);
	}

	@Override
	public List<Widget> setupDisplay(RitualRecipeDisplay display, Rectangle bounds) {
		List<Widget> widgets = new ArrayList<>();
		Point start = new Point(bounds.getX(), bounds.getY());

		// Four candles in a cross around the input — rendered as plain icons, not item slots.
		widgets.add(candle(start, INPUT_X, INPUT_Y - CANDLE_OFFSET));
		widgets.add(candle(start, INPUT_X - CANDLE_OFFSET, INPUT_Y));
		widgets.add(candle(start, INPUT_X + CANDLE_OFFSET, INPUT_Y));
		widgets.add(candle(start, INPUT_X, INPUT_Y + CANDLE_OFFSET));

		// The single thrown ingredient (input) — only one real slot, at the centre of the cross.
		if (!display.getInputEntries().isEmpty()) {
			widgets.add(Widgets.createSlot(new Point(start.x + INPUT_X, start.y + INPUT_Y))
					.entries(display.getInputEntries().get(0)));
		}

		// Arrow pointing from the ritual to the output (fixed position, like a furnace).
		widgets.add(Widgets.createArrow(new Point(start.x + ARROW_X, start.y + ARROW_Y)));

		// The reward (output) — the only other real slot.
		if (!display.getOutputEntries().isEmpty()) {
			widgets.add(Widgets.createSlot(new Point(start.x + OUTPUT_X, start.y + OUTPUT_Y))
					.entries(display.getOutputEntries().get(0)));
		}

		return widgets;
	}

	/** Renders a candle as a decorative icon (no slot background). */
	private static Widget candle(Point start, int x, int y) {
		return Widgets.createSlot(new Point(start.x + x, start.y + y))
				.entries(CANDLES)
				.disableBackground()
				.notInteractable();
	}
}
