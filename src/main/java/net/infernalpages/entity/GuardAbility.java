package net.infernalpages.entity;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;

/**
 * The abilities a Mould of Souls can hold. Each ability maps to an item that grants it and a
 * texture variant applied to the guard's model.
 */
public enum GuardAbility {
	NONE("none", null, "soulbound_neut"),
	WIND("wind", Items.WIND_CHARGE, "soulbound_white"),
	GUARDIAN("guardian", Items.HEART_OF_THE_SEA, "soulbound_blue"),
	FIRE("fire", Items.FIRE_CHARGE, "soulbound_yellow"),
	STRENGTH("strength", Items.DIRT, "soulbound_brown"),
	TELEPORT("teleport", Items.ENDER_PEARL, "soulbound_purple"),
	DRAGON("dragon", Items.DRAGON_EGG, "soulbound_purpleandblack");

	private final String id;
	private final Item item;
	private final String textureName;

	GuardAbility(String id, Item item, String textureName) {
		this.id = id;
		this.item = item;
		this.textureName = textureName;
	}

	public String id() {
		return id;
	}

	public Item item() {
		return item;
	}

	/** Returns the texture identifier used when this ability is equipped. */
	public Identifier texture() {
		return Identifier.of(InfernalPagesMod.MOD_ID, "textures/entity/" + textureName + ".png");
	}

	/** Finds the ability granted by an item, or NONE if the item grants nothing. */
	public static GuardAbility fromItem(Item item) {
		for (GuardAbility a : values()) {
			if (a.item != null && a.item == item) {
				return a;
			}
		}
		return NONE;
	}

	/** Finds the ability by its string id (used for NBT storage). */
	public static GuardAbility fromId(String id) {
		for (GuardAbility a : values()) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return NONE;
	}
}
