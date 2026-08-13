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
 * Lets the owner <b>stop</b> a Tainted Mould by shift-punching it. The mould is removed and it
 * drops its own item plus all the materials it has collected so far (in full stack-counts of the
 * ore it was mining) onto the ground.
 */
public final class TaintedMouldPickupHandler {
	private TaintedMouldPickupHandler() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register(TaintedMouldPickupHandler::onAttack);
	}

	private static ActionResult onAttack(PlayerEntity player, World world, Hand hand,
			Entity entity, @Nullable EntityHitResult hitResult) {
		if (world.isClient()) {
			return ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		if (!(entity instanceof TaintedMouldEntity mould)) {
			return ActionResult.PASS;
		}
		// Only the owner can stop it, and only with shift (sneak).
		if (!serverPlayer.isSneaking() || !mould.isOwner(serverPlayer)) {
			return ActionResult.PASS;
		}

		// Drop the mould's deploy item.
		world.spawnEntity(new net.minecraft.entity.ItemEntity(world,
				mould.getX(), mould.getY(), mould.getZ(), new ItemStack(ModItems.TAINTED_MOULD)));

		// Drop everything the mould has collected so far.
		int collected = mould.getCollectedCount();
		if (collected > 0 && mould.getOreType() != null) {
			ItemStack haul = new ItemStack(mould.getOreType().result(), collected);
			world.spawnEntity(new net.minecraft.entity.ItemEntity(world,
					mould.getX(), mould.getY(), mould.getZ(), haul));
		}

		// Remove the mould and let the owner know.
		mould.discard();
		serverPlayer.sendMessage(Text.literal("You stop the Tainted Mould. It drops its collected materials.")
				.formatted(Formatting.DARK_PURPLE), false);
		return ActionResult.SUCCESS;
	}
}
