package net.infernalpages.death;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.effect.ScriptureCalamity;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;
import net.infernalpages.util.EffectUtil;
import net.infernalpages.util.ItemUtil;

/**
 * Implements The Scripture's kill effect.
 *
 * <p>Whenever a living entity dies, if the killer is a {@link ServerPlayerEntity} holding The
 * Scripture in either hand, a 5-second calamity (lightning + escalating explosions) begins at the
 * victim's location and every copy of The Scripture is removed from the killer's inventory
 * (single use).
 *
 * <p>If the victim is also a player, they are additionally banished via {@link
 * net.infernalpages.ban.BanManager} and kicked from the server. Killing non-players triggers the
 * calamity but no ban.
 */
public class KillHandler {
	private KillHandler() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(KillHandler::onDeath);
	}

	private static void onDeath(net.minecraft.entity.LivingEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof ServerPlayerEntity killer)) {
			return;
		}

		ServerWorld world = (ServerWorld) victim.getEntityWorld();
		ItemStack heldMain = killer.getMainHandStack();
		ItemStack heldOff = killer.getOffHandStack();

		// The Contract Sword: reusable, lightning-only banishment, owner-bound, cannot harm the other signer.
		ItemStack sword = heldMain.isOf(ModItems.CONTRACT_SWORD) ? heldMain
				: heldOff.isOf(ModItems.CONTRACT_SWORD) ? heldOff : null;
		if (sword != null && isOwner(sword, killer) && !isBroken(sword)) {
			if (victim instanceof ServerPlayerEntity victimPlayer) {
				// The sword cannot be used against the other signer (A).
				if (!isForbidden(sword, victimPlayer)) {
					EffectUtil.summonLightning(world,
							new net.minecraft.util.math.Vec3d(victim.getX(), victim.getY(), victim.getZ()));
					banish(world, victimPlayer, "got their soul taken.", "Killed with the Contract Sword");
				}
			} else {
				// Lightning still fires for non-player kills, but no ban.
				EffectUtil.summonLightning(world,
						new net.minecraft.util.math.Vec3d(victim.getX(), victim.getY(), victim.getZ()));
			}
			return;
		}

		boolean holdingScripture = heldMain.isOf(ModItems.SCRIPTURE)
				|| heldOff.isOf(ModItems.SCRIPTURE);
		if (!holdingScripture) {
			return;
		}

		// Begin the 5-second calamity (lightning + escalating explosions) at the victim's location.
		ScriptureCalamity.start(world, new net.minecraft.util.math.Vec3d(victim.getX(), victim.getY(), victim.getZ()));

		// Consume one held copy of The Scripture (main hand has priority if both are held).
		ItemUtil.removeHeldItem(killer, ModItems.SCRIPTURE);

		// Only players get banished. Non-player kills only explode.
		if (victim instanceof ServerPlayerEntity victimPlayer) {
			banish(world, victimPlayer, "got their soul taken.", "Permanently killed with The Scripture");
		}
	}

	private static boolean isOwner(ItemStack sword, ServerPlayerEntity killer) {
		java.util.UUID owner = sword.get(ModComponents.SWORD_OWNER);
		return owner != null && owner.equals(killer.getUuid());
	}

	private static boolean isForbidden(ItemStack sword, ServerPlayerEntity victim) {
		java.util.UUID forbidden = sword.get(ModComponents.SWORD_FORBIDDEN);
		return forbidden != null && forbidden.equals(victim.getUuid());
	}

	/** True if this sword's pact was severed (so it no longer banishes). */
	private static boolean isBroken(ItemStack sword) {
		return Boolean.TRUE.equals(sword.get(ModComponents.CONTRACT_BROKEN))
				|| net.infernalpages.InfernalPagesMod.BROKEN.isBroken(sword.get(ModComponents.CONTRACT_ID));
	}

	/**
	 * Banishes a player: adds them to the mod's ban file, broadcasts a death message, and kicks
	 * them from the server.
	 *
	 * @param world     the world the banishment happens in
	 * @param victim    the player being banished
	 * @param suffix    the message tail, e.g. {@code "got their soul taken."} — the resulting line
	 *                  is {@code "<name> <suffix>"}
	 * @param reason    the ban-file reason
	 */
	public static void banish(ServerWorld world, ServerPlayerEntity victim, String suffix, String reason) {
		InfernalPagesMod.BANS.ban(victim.getUuid(), victim.getName().getString(), reason);

		world.getServer().getPlayerManager().broadcast(
				Text.literal(victim.getName().getString() + " " + suffix)
						.formatted(Formatting.DARK_RED),
				false);

		victim.networkHandler.disconnect(
				Text.literal("You have been banished by The Scripture. This is a permanent death."));
	}
}
