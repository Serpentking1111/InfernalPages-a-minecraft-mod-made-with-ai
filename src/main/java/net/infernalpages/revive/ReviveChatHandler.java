package net.infernalpages.revive;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.ban.BanManager;
import net.infernalpages.death.KillHandler;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;
import net.infernalpages.util.EffectUtil;
import net.infernalpages.util.ItemUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements both the Revival Charm and the Unholy Charm chat actions.
 *
 * <p><b>Revival Charm:</b> typing a banished player's name revives them (removes the ban) and
 * consumes the charm.
 *
 * <p><b>Unholy Charm:</b> typing the name of its <b>doomed</b> target permanently kills that player
 * with a single lightning bolt (no explosion) and consumes the charm.
 */
public class ReviveChatHandler {
	private ReviveChatHandler() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			ItemStack main = sender.getMainHandStack();
			ItemStack off = sender.getOffHandStack();

			// Unholy Charm: kill the doomed player.
			ItemStack unholy = main.isOf(ModItems.UNHOLY_CHARM) ? main
					: off.isOf(ModItems.UNHOLY_CHARM) ? off : null;
			if (unholy != null) {
				return handleUnholy(sender, unholy, message.getSignedContent().trim());
			}

			// Revival Charm: revive a banished player.
			ItemStack revival = main.isOf(ModItems.REVIVAL_CHARM) ? main
					: off.isOf(ModItems.REVIVAL_CHARM) ? off : null;
			if (revival != null) {
				return handleRevive(sender, message.getSignedContent().trim());
			}

			return true;
		});
	}

	private static boolean handleUnholy(ServerPlayerEntity sender, ItemStack unholy, String typedName) {
		UUID ownerUuid = unholy.get(ModComponents.UNHOLY_OWNER);
		UUID targetUuid = unholy.get(ModComponents.UNHOLY_TARGET);
		String targetName = unholy.get(ModComponents.UNHOLY_TARGET_NAME);

		// Only the pact's maker (player A) may use the charm.
		if (ownerUuid == null || !ownerUuid.equals(sender.getUuid())) {
			sender.sendMessage(Text.literal("This charm is not bound to you. You cannot wield it.")
					.formatted(Formatting.RED), false);
			return true;
		}

		if (targetUuid == null) {
			return true;
		}

		// A severed pact's charm is inert, even if it was stashed away when the contract was broken.
		if (Boolean.TRUE.equals(unholy.get(ModComponents.CONTRACT_BROKEN))
				|| InfernalPagesMod.BROKEN.isBroken(unholy.get(ModComponents.CONTRACT_ID))) {
			sender.sendMessage(Text.literal("This charm is broken — its pact has been severed.")
					.formatted(Formatting.RED), false);
			return true;
		}

		if (!typedName.equalsIgnoreCase(targetName)) {
			sender.sendMessage(Text.literal("That name does not match your pact's target.")
					.formatted(Formatting.GRAY), false);
			return true;
		}

		// Find the doomed player (may be offline; only kills if online).
		ServerPlayerEntity target = ((ServerWorld) sender.getEntityWorld()).getServer().getPlayerManager().getPlayer(targetUuid);
		if (target == null) {
			sender.sendMessage(Text.literal(targetName + " is not online to be claimed.")
					.formatted(Formatting.RED), false);
			return false;
		}

		// Before the kill, remove THIS pact's specific sword from B's inventory.
		UUID pactId = unholy.get(ModComponents.CONTRACT_ID);
		if (pactId != null) {
			removeSwordByPact(target, pactId);
		}

		ServerWorld world = (ServerWorld) target.getEntityWorld();
		EffectUtil.summonLightning(world,
				new net.minecraft.util.math.Vec3d(target.getX(), target.getY(), target.getZ()));
		// A single explosion roughly the size of an Ender Crystal (power 6) accompanies the strike.
		world.createExplosion(null, target.getX(), target.getY(), target.getZ(),
				6.0f, false, net.minecraft.world.World.ExplosionSourceType.TNT);
		KillHandler.banish(world, target, "got their soul taken.", "Claimed by an Unholy Charm");

		ItemUtil.removeHeldItem(sender, ModItems.UNHOLY_CHARM);
		sender.sendMessage(Text.literal("The pact is fulfilled. " + targetName + "'s soul is claimed.")
				.formatted(Formatting.DARK_PURPLE), false);
		return false;
	}

	private static boolean handleRevive(ServerPlayerEntity sender, String typedName) {
		Optional<BanManager.Entry> entry = InfernalPagesMod.BANS.findByName(typedName);
		if (entry.isEmpty()) {
			sender.sendMessage(Text.literal("No banished player named \"" + typedName + "\" was found.")
					.formatted(Formatting.GRAY), false);
			return true;
		}

		String revivedName = entry.get().name;
		InfernalPagesMod.BANS.unban(revivedName);

		ItemUtil.removeHeldItem(sender, ModItems.REVIVAL_CHARM);

		sender.sendMessage(Text.literal(revivedName + " has been revived from permanent death.")
				.formatted(Formatting.GOLD), false);

		// Suppress the raw chat line so the revived player's name isn't broadcast.
		return false;
	}

	/**
	 * Removes the Contract Sword belonging to the given pact (matched by its shared contract id)
	 * from the player's entire inventory. Only that specific iteration of the sword is removed.
	 */
	private static void removeSwordByPact(ServerPlayerEntity player, UUID pactId) {
		net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (stack.isOf(ModItems.CONTRACT_SWORD) && pactId.equals(stack.get(ModComponents.CONTRACT_ID))) {
				inv.setStack(i, ItemStack.EMPTY);
			}
		}
	}
}
