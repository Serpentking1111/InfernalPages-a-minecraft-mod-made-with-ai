package net.infernalpages.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.registry.ModComponents;

import java.util.ArrayList;
import java.util.List;

/**
 * A random effect applied to a weapon by the Sharpening Stone.
 *
 * <p>Each sharpening has a weight (rarity). {@link #roll} picks one weighted at random. Effects are
 * stored on the weapon's {@link ModComponents#SHARPENING} component. Static effects
 * (RANGE/WIND/PERFECT/BLUNT) are applied as item attribute modifiers; the conditional effects
 * (CLOSE/SPEED) are applied at damage time in {@code SharpeningDamageMixin}.
 */
public enum Sharpening {
	NONE("none", "None", 0),
	RANGE("range", "Sharp at Range", 12.5),
	CLOSE("close", "Sharp When Close", 12.5),
	SPEED("speed", "Sharp With Speed", 12.5),
	WIND("wind", "Sharp as the Wind", 12.5),
	PERFECT("perfect", "Perfectly Sharp", 5),
	BLUNT("blunt", "Blunt", 10);

	private static final List<Sharpening> ROLLABLE = List.of(RANGE, CLOSE, SPEED, WIND, PERFECT, BLUNT);
	private static final double TOTAL_WEIGHT = ROLLABLE.stream().mapToDouble(s -> s.weight).sum();

	private final String id;
	private final String displayName;
	private final double weight;

	Sharpening(String id, String displayName, double weight) {
		this.id = id;
		this.displayName = displayName;
		this.weight = weight;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	/** Returns the sharpening stored on the stack, or NONE if unsharpened. */
	public static Sharpening fromStack(ItemStack stack) {
		String id = stack.get(ModComponents.SHARPENING);
		return id == null ? NONE : fromId(id);
	}

	public static Sharpening fromId(String id) {
		for (Sharpening s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return NONE;
	}

	/** Picks a random sharpening, weighted by each effect's rarity. */
	public static Sharpening roll(Random random) {
		double r = random.nextDouble() * TOTAL_WEIGHT;
		double acc = 0;
		for (Sharpening s : ROLLABLE) {
			acc += s.weight;
			if (r < acc) {
				return s;
			}
		}
		return ROLLABLE.get(0);
	}

	/** Applies this sharpening to the weapon's attribute modifiers and stores it on the stack. */
	public void applyToStack(ItemStack stack) {
		AttributeModifiersComponent base = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (base == null) {
			AttributeModifiersComponent itemComp =
					stack.getItem().getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
			base = itemComp != null ? itemComp : AttributeModifiersComponent.DEFAULT;
		}

		// Remove any previously-applied sharpening modifier.
		AttributeModifiersComponent result = withoutSharpening(base);

		EntityAttributeModifier modifier = modifier();
		if (modifier != null) {
			result = result.with(attribute(), modifier, AttributeModifierSlot.MAINHAND);
		}

		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, result);
		stack.set(ModComponents.SHARPENING, id);
	}

	/** Removes all sharpening attribute modifiers from the given component (keeps everything else). */
	private static AttributeModifiersComponent withoutSharpening(AttributeModifiersComponent comp) {
		List<AttributeModifiersComponent.Entry> kept = new ArrayList<>();
		for (AttributeModifiersComponent.Entry entry : comp.modifiers()) {
			if (isSharpeningModifier(entry.modifier().id())) {
				continue;
			}
			kept.add(entry);
		}
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
		for (AttributeModifiersComponent.Entry entry : kept) {
			builder.add(entry.attribute(), entry.modifier(), entry.slot());
		}
		return builder.build();
	}

	private static boolean isSharpeningModifier(Identifier id) {
		for (Sharpening s : values()) {
			Identifier sid = s.modifierId();
			if (sid != null && sid.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** The attribute a static sharpening modifies, or null for conditional/none. */
	public RegistryEntry<EntityAttribute> attribute() {
		return switch (this) {
			case RANGE -> EntityAttributes.ENTITY_INTERACTION_RANGE;
			case WIND -> EntityAttributes.ATTACK_SPEED;
			case PERFECT, BLUNT -> EntityAttributes.ATTACK_DAMAGE;
			default -> null;
		};
	}

	/** The attribute modifier for a static sharpening, or null for conditional/none. */
	public EntityAttributeModifier modifier() {
		return switch (this) {
			case RANGE -> new EntityAttributeModifier(modifierId(), 1.5, EntityAttributeModifier.Operation.ADD_VALUE);
			case WIND -> new EntityAttributeModifier(modifierId(), 0.2, EntityAttributeModifier.Operation.ADD_VALUE);
			// Adds +100% of the total → doubles the final attack damage.
			case PERFECT -> new EntityAttributeModifier(modifierId(), 1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			// Adds -100% of the total → zeroes the final attack damage regardless of the weapon's
			// base value. Mathematically the exact inverse of PERFECT, using the same
			// ADD_MULTIPLIED_TOTAL machinery so the result goes through the normal attribute
			// pipeline (tooltip, attack-strength, etc.). ≤1.13.5 patched this in at damage time in
			// SharpeningDamageMixin, which made the tooltip lie (still showed the weapon's full
			// attack damage) and bypassed reroll-stripping; both are fixed in 1.13.6.
			case BLUNT -> new EntityAttributeModifier(modifierId(), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			default -> null;
		};
	}

	/** A unique identifier for this sharpening's modifier (used to strip it when rerolling). */
	public Identifier modifierId() {
		return switch (this) {
			case RANGE -> Identifier.of(InfernalPagesMod.MOD_ID, "sharp_range");
			case WIND -> Identifier.of(InfernalPagesMod.MOD_ID, "sharp_wind");
			case PERFECT -> Identifier.of(InfernalPagesMod.MOD_ID, "sharp_perfect");
			case BLUNT -> Identifier.of(InfernalPagesMod.MOD_ID, "sharp_blunt");
			default -> null;
		};
	}

	/** True if the stack is a weapon eligible for sharpening (grants attack damage). */
	public static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		AttributeModifiersComponent comp = stack.getItem().getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		if (comp == null) {
			comp = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		}
		if (comp == null) {
			return false;
		}
		for (AttributeModifiersComponent.Entry entry : comp.modifiers()) {
			if (entry.attribute() == EntityAttributes.ATTACK_DAMAGE) {
				return true;
			}
		}
		return false;
	}
}
