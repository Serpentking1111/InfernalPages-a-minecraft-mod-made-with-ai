package net.infernalpages.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.Set;

/**
 * The resources a Tainted Mould can be sent to mine. Each type maps the <b>item you feed it</b>
 * to the <b>ore blocks</b> it will break and the <b>item it collects</b> (a stack of which makes it
 * teleport back to its owner).
 *
 * <p>For example, feeding an <em>iron ingot</em> sends it to break iron ore (stone or deepslate
 * variants) and collect raw iron.
 */
public enum TaintedOreType {
	IRON("Raw Iron", Items.IRON_INGOT, Items.RAW_IRON,
			Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)),
	GOLD("Raw Gold", Items.GOLD_INGOT, Items.RAW_GOLD,
			Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE)),
	DIAMOND("Diamond", Items.DIAMOND, Items.DIAMOND,
			Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)),
	NETHERITE("Ancient Debris", Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS,
			Set.of(Blocks.ANCIENT_DEBRIS)),
	LAPIS("Lapis Lazuli", Items.LAPIS_LAZULI, Items.LAPIS_LAZULI,
			Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)),
	REDSTONE("Redstone", Items.REDSTONE, Items.REDSTONE,
			Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)),
	EMERALD("Emerald", Items.EMERALD, Items.EMERALD,
			Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)),
	COAL("Coal", Items.COAL, Items.COAL,
			Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE));

	private final String displayName;
	private final Item feedItem;
	private final Item result;
	private final Set<Block> ores;

	TaintedOreType(String displayName, Item feedItem, Item result, Set<Block> ores) {
		this.displayName = displayName;
		this.feedItem = feedItem;
		this.result = result;
		this.ores = ores;
	}

	public String displayName() {
		return displayName;
	}

	public Item feedItem() {
		return feedItem;
	}

	/** The item the mould collects from the ore (the "stack" it returns with). */
	public Item result() {
		return result;
	}

	/** True if the given block state is one of this type's target ores. */
	public boolean isOre(BlockState state) {
		return ores.contains(state.getBlock());
	}

	/** Finds the ore type granted by a fed item, or null if the item grants nothing. */
	public static TaintedOreType fromItem(Item item) {
		for (TaintedOreType t : values()) {
			if (t.feedItem == item) {
				return t;
			}
		}
		return null;
	}

	/** Finds an ore type by its enum name (used for NBT storage). */
	public static TaintedOreType fromName(String name) {
		for (TaintedOreType t : values()) {
			if (t.name().equals(name)) {
				return t;
			}
		}
		return null;
	}
}
