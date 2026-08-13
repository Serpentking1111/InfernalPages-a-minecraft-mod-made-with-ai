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
 * Shard grants a shield that blocks an incoming hit entirely (regardless of size). The shield is
 * <b>repeatable</b>: after blocking, it goes on cooldown and then recharges automatically, so it can
 * block again and again for as long as the reinforced armour is worn.
 *
 * <p>The cooldown starts at 15 seconds with a single reinforced piece equipped and is reduced by
 * 3 seconds for every <i>additional</i> reinforced piece, down to a floor of 1 second:
 *
 * <table border="1">
 *   <caption>Cooldown by reinforced pieces equipped</caption>
 *   <tr><th>Pieces</th><th>Cooldown</th></tr>
 *   <tr><td>1</td><td>15s (300 ticks)</td></tr>
 *   <tr><td>2</td><td>12s (240 ticks)</td></tr>
 *   <tr><td>3</td><td>9s (180 ticks)</td></tr>
 *   <tr><td>4</td><td>6s (120 ticks)</td></tr>
 * </table>
 *
 * <p>Because it checks the {@link ModComponents#TAINTED} component on equipped armour (rather than
 * any specific item), it works with armour from any mod, not just netherite.
 */
public final class TaintedArmorHandler {
	/** Base cooldown with a single reinforced piece equipped, in ticks (15 seconds). */
	private static final long BASE_COOLDOWN_TICKS = 300L;
	/**
	 * Cooldown reduction per reinforced piece beyond the first, in ticks (3 seconds each). The
	 * reduction is applied to the <i>extra</i> pieces so that the base 15 second cooldown is what a
	 * player actually experiences with one reinforced piece equipped.
	 */
	private static final long COOLDOWN_REDUCTION_PER_PIECE = 60L;
	/** Minimum possible cooldown, in ticks (1 second). */
	private static final long MIN_COOLDOWN_TICKS = 20L;
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** Maps a wearer's UUID to the world tick when its shield last blocked a hit. */
	private static final Map<UUID, Long> LAST_BLOCKED = new HashMap<>();
	/** How often (in blocked hits) to prune fully-recharged entries from {@link #LAST_BLOCKED}. */
	private static final int PRUNE_INTERVAL = 64;
	private static int blocksSincePrune;

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
		// Use the monotonic world age, NOT getTimeOfDay(): the day-time clock is frozen by the
		// advance_time / doDaylightCycle gamerule and rewritten by /time set, either of which would
		// stop the shield ever recharging (or make it block forever if the clock jumped backwards).
		long now = world.getTime();
		long cooldown = cooldownTicks(pieces);
		Long last = LAST_BLOCKED.get(id);
		if (last != null && now - last < cooldown && now >= last) {
			// Still cooling down — let the damage through.
			return true;
		}

		// Block the hit and start the cooldown. The entry is overwritten every time, so the shield
		// recharges and can block again indefinitely.
		LAST_BLOCKED.put(id, now);
		prune(now);
		world.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.0f);
		return false;
	}

	/**
	 * The cooldown, in ticks, for a wearer with the given number of reinforced pieces equipped.
	 * One piece yields the full {@link #BASE_COOLDOWN_TICKS}; each additional piece removes
	 * {@link #COOLDOWN_REDUCTION_PER_PIECE}, never dropping below {@link #MIN_COOLDOWN_TICKS}.
	 */
	public static long cooldownTicks(int pieces) {
		long extraPieces = Math.max(0, pieces - 1);
		return Math.max(MIN_COOLDOWN_TICKS,
				BASE_COOLDOWN_TICKS - COOLDOWN_REDUCTION_PER_PIECE * extraPieces);
	}

	/**
	 * Drops entries whose shield has fully recharged. Without this the map grows for the lifetime of
	 * the server, since entries were never removed when a player logged out or died.
	 */
	private static void prune(long now) {
		if (++blocksSincePrune < PRUNE_INTERVAL) {
			return;
		}
		blocksSincePrune = 0;
		LAST_BLOCKED.entrySet().removeIf(entry -> now - entry.getValue() >= BASE_COOLDOWN_TICKS);
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
