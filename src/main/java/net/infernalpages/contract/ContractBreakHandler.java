package net.infernalpages.contract;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.health.HealthPenaltyManager;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;
import net.infernalpages.ritual.RitualHandler;
import net.infernalpages.util.EffectUtil;

import java.util.UUID;

/**
 * Allows a contract to be broken with a Purity Seal.
 *
 * <p>Player A must right-click player B with a Purity Seal while B stands in the centre of a
 * ritual. If valid, a lightning bolt strikes, both players permanently lose 1 max health, and the
 * pact is severed: A's Unholy Charm (targeting B) and B's Sealer of Fates (forbidden against A)
 * are removed. The Purity Seal is consumed.
 */
public final class ContractBreakHandler {
	private ContractBreakHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(ContractBreakHandler::onInteractEntity);
	}

	private static ActionResult onInteractEntity(net.minecraft.entity.player.PlayerEntity player,
			net.minecraft.world.World world, net.minecraft.util.Hand hand,
			Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		// Only a right-click from player A on another player (B).
		if (!(entity instanceof ServerPlayerEntity target)) {
			return ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		ItemStack held = serverPlayer.getStackInHand(hand);
		if (!held.isOf(ModItems.PURITY_SEAL)) {
			return ActionResult.PASS;
		}
		if (!RitualHandler.isPlayerInRitual(target)) {
			serverPlayer.sendMessage(Text.literal("The target must stand in the centre of a ritual.")
					.formatted(Formatting.RED), false);
			return ActionResult.PASS;
		}

		ServerWorld world2 = (ServerWorld) serverPlayer.getEntityWorld();

		// Find A's Unholy Charm targeting B (in A's inventory).
		UUID pactId = null;
		UUID targetUuid = target.getUuid();
		PlayerInventory aInv = serverPlayer.getInventory();
		for (int i = 0; i < aInv.size(); i++) {
			ItemStack s = aInv.getStack(i);
			if (s.isOf(ModItems.UNHOLY_CHARM)
					&& serverPlayer.getUuid().equals(s.get(ModComponents.UNHOLY_OWNER))
					&& targetUuid.equals(s.get(ModComponents.UNHOLY_TARGET))) {
				pactId = s.get(ModComponents.CONTRACT_ID);
				s.set(ModComponents.CONTRACT_BROKEN, true);
				aInv.setStack(i, ItemStack.EMPTY);
				break;
			}
		}

		// Remove B's Sealer of Fates (forbidden against A), matching the pact if known.
		PlayerInventory bInv = target.getInventory();
		for (int i = 0; i < bInv.size(); i++) {
			ItemStack s = bInv.getStack(i);
			if (s.isOf(ModItems.CONTRACT_SWORD)
					&& targetUuid.equals(s.get(ModComponents.SWORD_OWNER))
					&& serverPlayer.getUuid().equals(s.get(ModComponents.SWORD_FORBIDDEN))) {
				UUID swordPact = s.get(ModComponents.CONTRACT_ID);
				// If we don't yet know the pact id (charm was stashed elsewhere), take it from the sword.
				if (pactId == null) {
					pactId = swordPact;
				}
				if (pactId == null || pactId.equals(swordPact)) {
					s.set(ModComponents.CONTRACT_BROKEN, true);
					bInv.setStack(i, ItemStack.EMPTY);
					break;
				}
			}
		}

		// Globally invalidate this pact so any stashed charm/sword (e.g. in a chest) can't be used.
		InfernalPagesMod.BROKEN.breakContract(pactId);

		// Lightning strike.
		EffectUtil.summonLightning(world2,
				new net.minecraft.util.math.Vec3d(target.getX(), target.getY(), target.getZ()));

		// Both players permanently lose 1 max health.
		HealthPenaltyManager.applyPenalty(serverPlayer);
		HealthPenaltyManager.applyPenalty(target);

		// Consume the Purity Seal.
		held.decrement(1);

		serverPlayer.sendMessage(Text.literal("The pact is broken. The bond is severed.")
				.formatted(Formatting.LIGHT_PURPLE), false);
		target.sendMessage(Text.literal("Your contract has been broken.")
				.formatted(Formatting.LIGHT_PURPLE), false);
		return ActionResult.SUCCESS;
	}
}
