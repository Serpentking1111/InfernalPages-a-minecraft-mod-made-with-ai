package net.infernalpages.death;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;

/**
 * Enforces the contract's protection: once a pact is sealed, player B can never directly harm
 * player A. If B tries to attack A (punch or otherwise), the hit is nullified and the ender-pearl
 * teleport sound plays. No chat message is sent.
 *
 * <p>This is detected via B's Sealer of Fates, which records A as the forbidden target
 * ({@link ModComponents#SWORD_FORBIDDEN}). As long as B carries that sword, the protection is
 * active, so even an unarmed punch is nullified.
 */
public final class ContractProtectionHandler {
	private ContractProtectionHandler() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ContractProtectionHandler::onDamage);
	}

	private static boolean onDamage(LivingEntity victim, DamageSource source, float amount) {
		// Only direct attacks by a player.
		if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
			return true;
		}

		// A broken (severed) Sealer of Fates deals no damage at all.
		if (isHoldingBrokenSword(attacker)) {
			return false;
		}

		// The rest only applies to player-vs-player damage.
		if (!(victim instanceof ServerPlayerEntity target)) {
			return true;
		}
		// Cannot harm yourself.
		if (attacker.getUuid().equals(target.getUuid())) {
			return true;
		}

		// Check whether the attacker (B) carries a bound Sealer of Fates that forbids harming A.
		if (!isBoundAgainst(attacker, target)) {
			return true;
		}

		// Nullify the hit and play the contract-block sound (no chat message).
		attacker.getEntityWorld().playSound(null,
				target.getX(), target.getY(), target.getZ(),
				net.infernalpages.registry.ModSounds.CONTRACT_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.0f);
		return false;
	}

	/** True if the attacker is holding a Contract Sword whose pact has been severed. */
	private static boolean isHoldingBrokenSword(ServerPlayerEntity attacker) {
		return isBroken(attacker.getMainHandStack()) || isBroken(attacker.getOffHandStack());
	}

	private static boolean isBroken(net.minecraft.item.ItemStack stack) {
		if (!stack.isOf(ModItems.CONTRACT_SWORD)) {
			return false;
		}
		return Boolean.TRUE.equals(stack.get(ModComponents.CONTRACT_BROKEN))
				|| net.infernalpages.InfernalPagesMod.BROKEN.isBroken(stack.get(ModComponents.CONTRACT_ID));
	}

	/** True if the given attacker holds a Sealer of Fates bound to them that forbids harming the victim. */
	private static boolean isBoundAgainst(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
		PlayerInventory inv = attacker.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isOf(ModItems.CONTRACT_SWORD)) {
				continue;
			}
			// A severed pact's sword grants no protection.
			if (Boolean.TRUE.equals(stack.get(ModComponents.CONTRACT_BROKEN))
					|| net.infernalpages.InfernalPagesMod.BROKEN.isBroken(stack.get(ModComponents.CONTRACT_ID))) {
				continue;
			}
			java.util.UUID owner = stack.get(ModComponents.SWORD_OWNER);
			java.util.UUID forbidden = stack.get(ModComponents.SWORD_FORBIDDEN);
			if (owner != null && owner.equals(attacker.getUuid())
					&& forbidden != null && forbidden.equals(victim.getUuid())) {
				return true;
			}
		}
		return false;
	}
}
