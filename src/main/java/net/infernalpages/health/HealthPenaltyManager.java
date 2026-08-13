package net.infernalpages.health;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;

/**
 * Applies the permanent max-health cost of performing rituals.
 *
 * <p>Each ritual the player performs costs them 1 point of max health, forever. Rather than
 * tracking this in a custom file, this uses a single <b>persistent attribute modifier</b>
 * ({@code infernalpages:max_health_penalty}) on {@code MAX_HEALTH}. Persistent attribute
 * modifiers are stored directly in the player's saved data by the game, so:
 * <ul>
 *   <li>the penalty survives death, respawn, and server restarts automatically;</li>
 *   <li>no separate bookkeeping file or "reapply on join" logic is needed;</li>
 *   <li>the current penalty is read straight from the attribute modifier itself.</li>
 * </ul>
 */
public class HealthPenaltyManager {
	/** Fixed id for the max-health penalty modifier. */
	public static final Identifier MODIFIER_ID = Identifier.of(InfernalPagesMod.MOD_ID, "max_health_penalty");

	/** The penalty is applied as a plain additive modifier to the base max health (20). */
	private static final EntityAttributeModifier.Operation OPERATION = EntityAttributeModifier.Operation.ADD_VALUE;

	/** How many max-health points one ritual costs. */
	private static final int PENALTY_PER_RITUAL = 1;

	public HealthPenaltyManager() {
	}

	/** Returns how many points of max health this player has permanently lost (0 if none). */
	public static int getLostHealth(ServerPlayerEntity player) {
		EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (instance == null) {
			return 0;
		}
		EntityAttributeModifier modifier = instance.getModifier(MODIFIER_ID);
		if (modifier == null) {
			return 0;
		}
		return (int) -modifier.value();
	}

	/**
	 * Permanently reduces the player's max health by {@value #PENALTY_PER_RITUAL} point by
	 * overwriting the persistent modifier. Returns false (and does nothing) if the player is
	 * already at the absolute minimum health and cannot pay.
	 */
	public static boolean applyPenalty(ServerPlayerEntity player) {
		if (player.getMaxHealth() <= 1.0f) {
			return false;
		}
		int lost = getLostHealth(player);
		EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (instance == null) {
			return false;
		}

		// Overwrite the single persistent modifier with the new (higher) penalty.
		EntityAttributeModifier modifier = new EntityAttributeModifier(
				MODIFIER_ID, -(lost + PENALTY_PER_RITUAL), OPERATION);
		instance.overwritePersistentModifier(modifier);

		// Clamp current health to the new max.
		float max = player.getMaxHealth();
		if (player.getHealth() > max) {
			player.setHealth(max);
		}
		return true;
	}

	/** Removes the max-health penalty entirely, restoring the player's hearts to normal. */
	public static void resetPenalty(ServerPlayerEntity player) {
		EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (instance == null) {
			return;
		}
		instance.removeModifier(MODIFIER_ID);
		float max = player.getMaxHealth();
		if (player.getHealth() > max) {
			player.setHealth(max);
		}
	}
}
