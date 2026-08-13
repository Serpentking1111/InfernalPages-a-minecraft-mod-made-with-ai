package net.infernalpages.ritual;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.block.Block;
import net.minecraft.block.CandleBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.death.KillHandler;
import net.infernalpages.health.HealthPenaltyManager;
import net.infernalpages.registry.ModItems;
import net.infernalpages.util.EffectUtil;

/**
 * Implements the ritual: place four candles in a cross (one each north, south, east and west of
 * an empty middle block) and throw (drop) an item into the middle.
 *
 * <p>Any item thrown into the middle permanently costs the thrower 1 max health, regardless of
 * the outcome. Depending on the item (see {@link net.infernalpages.ModConfig#ritualIngredients}),
 * the thrower then receives a reward item. By default throwing a dirt block grants The Scripture
 * and throwing a golden apple grants a Revival Charm.
 *
 * <p>The item must be <em>dropped</em> (press Q), not placed. The ritual is detected the moment
 * the dropped {@link ItemEntity} spawns in the centre of the cross.
 */
public class RitualHandler {
	private static final double MAX_THROWER_DISTANCE_SQ = 16.0; // ~4 blocks

	private RitualHandler() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(RitualHandler::onEntityLoad);
	}

	private static void onEntityLoad(Entity entity, ServerWorld world) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}

		BlockPos center = findCenter(world, item.getBlockPos());
		if (center == null) {
			return;
		}

		ServerPlayerEntity thrower = findThrower(world, item);
		if (thrower == null) {
			return;
		}

		ItemStack stack = item.getStack();
		Item ingredient = stack.getItem();

		// 1) The thrower permanently loses 1 max health, regardless of the outcome.
		boolean hpLost = HealthPenaltyManager.applyPenalty(thrower);
		if (!hpLost) {
			// At half a heart (1 max health) the ritual cannot be paid, and it backfires:
			// the thrower is banished with a single non-block-destroying explosion.
			item.discard();
			world.createExplosion(null,
					thrower.getX(), thrower.getY(), thrower.getZ(),
					InfernalPagesMod.CONFIG.calamityBasePower, false, World.ExplosionSourceType.NONE);
			KillHandler.banish(world, thrower, "got to greedy", "Tried the ritual at half a heart");
			return;
		}

		// 2) The offering is consumed by the ritual.
		item.discard();

		// 3) Resolve the outcome based on the item used.
		String ingredientId = Registries.ITEM.getId(ingredient).toString();
		String outcomeId = InfernalPagesMod.CONFIG.ritualIngredients.get(ingredientId);
		if (outcomeId == null) {
			thrower.sendMessage(Text.literal("The ritual accepts your offering, but grants nothing in return.")
					.formatted(Formatting.GRAY), false);
			return;
		}

		Item outcome = Registries.ITEM.get(Identifier.of(outcomeId));
		if (outcome == null || outcome == Items.AIR) {
			return;
		}

		InfernalPagesMod.LOGGER.info("[Ritual] {} performed a ritual, losing 1 max health, rewarded with {}",
				thrower.getName().getString(), outcomeId);

		// Lightning strikes at the ritual site to mark its success.
		EffectUtil.summonLightning(world,
				center.toCenterPos().add(0, -0.5, 0));

		thrower.giveItemStack(new ItemStack(outcome));
		if (outcome == ModItems.SCRIPTURE) {
			thrower.sendMessage(Text.literal("The Scripture materialises in your hands.")
					.formatted(Formatting.DARK_RED), false);
		} else if (outcome == ModItems.REVIVAL_CHARM) {
			thrower.sendMessage(Text.literal("A Revival Charm materialises in your hands. "
					+ "Hold it and type a banished player's name in chat to revive them.")
					.formatted(Formatting.GOLD), false);
		} else if (outcome == ModItems.CONTRACT) {
			thrower.sendMessage(Text.literal("An Unsigned Contract materialises in your hands. "
					+ "Right-click to sign it, then pass it to another player.")
					.formatted(Formatting.DARK_PURPLE), false);
		}
	}

	/**
	 * Returns the block that is the centre of a valid candle cross, searching near pos.
	 *
	 * <p>A dropped item spawns at the thrower's body height, so its block position is usually one
	 * block ABOVE the floor where the candles sit. We therefore search vertically as well as
	 * horizontally, so the ritual is detected whether the item's position is at floor level, one
	 * block up, or slightly off-centre.
	 */
	/** Returns true if the player is standing at the centre of a valid candle cross ritual. */
	public static boolean isPlayerInRitual(ServerPlayerEntity player) {
		return findCenter((ServerWorld) player.getEntityWorld(), player.getBlockPos()) != null;
	}

	private static BlockPos findCenter(ServerWorld world, BlockPos around) {
		for (int dy = -2; dy <= 0; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos candidate = around.add(dx, dy, dz);
					if (isValidRitual(world, candidate)) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	/** A valid ritual: the four cardinal neighbours of the centre are all candles. */
	private static boolean isValidRitual(ServerWorld world, BlockPos center) {
		Block east = world.getBlockState(center.east()).getBlock();
		Block west = world.getBlockState(center.west()).getBlock();
		Block north = world.getBlockState(center.north()).getBlock();
		Block south = world.getBlockState(center.south()).getBlock();
		return east instanceof CandleBlock
				&& west instanceof CandleBlock
				&& north instanceof CandleBlock
				&& south instanceof CandleBlock;
	}

	/** Figures out who dropped the item: prefers the recorded owner, else the nearest player. */
	private static ServerPlayerEntity findThrower(ServerWorld world, ItemEntity item) {
		Entity owner = item.getOwner();
		if (owner instanceof ServerPlayerEntity ownerPlayer) {
			return ownerPlayer;
		}

		ServerPlayerEntity nearest = null;
		double best = MAX_THROWER_DISTANCE_SQ;
		for (ServerPlayerEntity player : world.getPlayers()) {
			double distSq = player.squaredDistanceTo(item);
			if (distSq < best) {
				best = distSq;
				nearest = player;
			}
		}
		return nearest;
	}
}
