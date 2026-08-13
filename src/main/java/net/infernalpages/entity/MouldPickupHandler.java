package net.infernalpages.entity;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.infernalpages.registry.ModItems;

import org.jspecify.annotations.Nullable;

/**
 * Lets the owner pick up a Mould of Souls by shift-punching it, returning it to an item.
 */
public final class MouldPickupHandler {
	private MouldPickupHandler() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register(MouldPickupHandler::onAttack);
	}

	private static ActionResult onAttack(PlayerEntity player, World world, Hand hand,
			Entity entity, @Nullable EntityHitResult hitResult) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		if (!(entity instanceof MouldOfSoulsEntity mould)) {
			return ActionResult.PASS;
		}
		// Only the owner can interact this way, and only with shift (sneak).
		if (!serverPlayer.isSneaking() || !mould.isOwner(serverPlayer)) {
			return ActionResult.PASS;
		}

		// Shift-punch priority 1: if an ability is equipped, remove it and return its item to the owner.
		GuardAbility ability = mould.getAbility();
		if (ability != GuardAbility.NONE) {
			mould.setAbility(GuardAbility.NONE);
			net.minecraft.item.Item abilityItem = ability.item();
			if (abilityItem != null) {
				serverPlayer.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(abilityItem));
			}
			serverPlayer.sendMessage(Text.literal("Mould of Souls: ability removed.")
					.formatted(Formatting.DARK_PURPLE), true);
			return ActionResult.SUCCESS;
		}

		// Shift-punch priority 2: a blank (no ability) mould is picked up and returned to an item.
		serverPlayer.giveItemStack(new ItemStack(ModItems.MOULD_OF_SOULS));
		mould.discard();
		serverPlayer.sendMessage(Text.literal("You collect the Mould of Souls.")
				.formatted(Formatting.DARK_PURPLE), false);
		return ActionResult.SUCCESS;
	}
}
