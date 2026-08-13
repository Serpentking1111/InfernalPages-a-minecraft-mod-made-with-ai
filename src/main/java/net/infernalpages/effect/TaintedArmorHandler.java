package net.infernalpages.effect;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.infernalpages.registry.ModComponents;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the "Tainted" armour effect. Wearing any piece of armour reinforced with a Tainted
 * Shard grants a one-hit shield that blocks the next incoming damage entirely (regardless of size).
 * After blocking a hit, the effect goes on cooldown for 15 seconds before it can block again.
 *
 * <p>Because it checks the {@link ModComponents#TAINTED} component on equipped armour (rather than
 * any specific item), it works with armour from any mod, not just netherite.
 */
public final class TaintedArmorHandler {
	/** Base cooldown between blocks, in ticks (15 seconds). */
	private static final long BASE_COOLDOWN_TICKS = 300L;
	/** Cooldown reduction per reinforced piece equipped, in ticks (3 seconds each). */
	private static final long COOLDOWN_REDUCTION_PER_PIECE = 60L;
	/** Minimum possible cooldown, in ticks. */
	private static final long MIN_COOLDOWN_TICKS = 20L;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Maps a wearer's UUID to the world tick when its shield last blocked a hit. */
	private static final Map<UUID, Long> LAST_BLOCKED = new HashMap<>();

	private TaintedArmorHandler() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(TaintedArmorHandler::onDamage);
	}

	private static boolean onDamage(LivingEntity victim, DamageSource source, float amount) {
		// Only matters when there's a server world to track time.
		if (!(victim.getEntityWorld() instanceof ServerWorld world)) {
			return true;
		}
		// The wearer must have at least one Tainted-reinforced armour piece equipped.
		int pieces = countTaintedArmor(victim);
		if (pieces == 0) {
			return true;
		}
		UUID id = victim.getUuid();
		long now = world.getTimeOfDay();
		// Cooldown is reduced by 3 seconds per reinforced piece equipped.
		long cooldown = Math.max(MIN_COOLDOWN_TICKS, BASE_COOLDOWN_TICKS - COOLDOWN_REDUCTION_PER_PIECE * pieces);
		Long last = LAST_BLOCKED.get(id);
		if (last != null && (now - last) < cooldown) {
			// Still cooling down — let the damage through.
			return true;
		}

		// Block the hit and start the cooldown.
		LAST_BLOCKED.put(id, now);
		world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.0f);
		return false;
	}

	/** Counts how many equipped armour pieces have the Tainted component. */
	private static int countTaintedArmor(LivingEntity entity) {
		int count = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack stack = entity.getEquippedStack(slot);
			if (!stack.isEmpty() && Boolean.TRUE.equals(stack.get(ModComponents.TAINTED))) {
				count++;
			}
		}
		return count;
	}
}
