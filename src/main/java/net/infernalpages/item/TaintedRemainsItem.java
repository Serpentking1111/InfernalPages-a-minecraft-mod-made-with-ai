package net.infernalpages.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.infernalpages.registry.ModComponents;

import java.util.function.Consumer;

/**
 * "The remains of a tainted past" — a hybrid sword-and-hoe weapon.
 *
 * <p>It can be swapped between <b>sword mode</b> and <b>hoe mode</b> by shift-right-clicking while
 * held. The active mode is stored in {@link ModComponents#TAINTED_MODE}.
 *
 * <ul>
 *   <li><b>Sword mode:</b> a right-click launches the wielder into the air in the direction they're
 *       looking (a riptide-style dash). Works both in and out of water. Has a short cooldown.</li>
 *   <li><b>Hoe mode:</b> tills dirt/grass like a hoe. If Farmers' Delight is loaded it can also act
 *       like a FD knife (harvests crops) — otherwise it is a plain hoe.</li>
 * </ul>
 */
public class TaintedRemainsItem extends Item {
	private static final int LAUNCH_COOLDOWN_TICKS = 20; // 1 second
	private static final double LAUNCH_POWER = 1.6;

	public TaintedRemainsItem(Settings settings) {
		super(settings);
	}

	/** Returns the weapon's current mode ("sword" or "hoe"), defaulting to "sword". */
	public static String getMode(ItemStack stack) {
		String mode = stack.get(ModComponents.TAINTED_MODE);
		return mode == null ? "sword" : mode;
	}

	public static boolean isSwordMode(ItemStack stack) {
		return "sword".equals(getMode(stack));
	}

	private static void setMode(ItemStack stack, String mode) {
		stack.set(ModComponents.TAINTED_MODE, mode);
		// Also set custom_model_data so the item model switches reliably (sword=0, hoe=1).
		float data = "hoe".equals(mode) ? 1.0f : 0.0f;
		stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA,
				new net.minecraft.component.type.CustomModelDataComponent(
						java.util.List.of(data), java.util.List.of(), java.util.List.of(), java.util.List.of()));
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		ItemStack stack = user.getStackInHand(hand);

		// Shift-right-click: toggle between sword and hoe mode.
		if (user.isSneaking()) {
			String newMode = isSwordMode(stack) ? "hoe" : "sword";
			setMode(stack, newMode);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
			user.sendMessage(Text.literal("Tainted Remains: " + newMode + " mode.")
					.formatted(Formatting.DARK_PURPLE), true);
			return ActionResult.SUCCESS_SERVER;
		}

		// Sword mode: riptide-style launch on right-click.
		if (isSwordMode(stack)) {
			if (user.getItemCooldownManager().isCoolingDown(stack)) {
				return ActionResult.PASS;
			}
			launch(world, user, stack, hand);
			user.getItemCooldownManager().set(stack, LAUNCH_COOLDOWN_TICKS);
			stack.damage(1, (ServerPlayerEntity) user, hand);
			world.playSound(null, user.getX(), user.getY(), user.getZ(),
					SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.0f, 1.0f);
			return ActionResult.SUCCESS_SERVER;
		}

		// Hoe mode: nothing happens on a plain right-click (tilling is useOnBlock).
		return ActionResult.PASS;
	}

	/**
	 * Launches the player riptide-style along their look direction, using the same mechanism as a
	 * Riptide trident: {@code useRiptide} sets the spin state (which plays the riptide animation and
	 * applies velocity every tick while active) and {@code addVelocity} gives the initial momentum.
	 */
	private void launch(World world, PlayerEntity player, ItemStack stack, Hand hand) {
		Vec3d look = player.getRotationVector();
		double len = look.length();
		if (len < 1.0e-4) {
			return;
		}
		// Normalised look direction scaled to the launch power.
		Vec3d dir = look.multiply(1.0 / len);
		Vec3d vel = dir.multiply(LAUNCH_POWER);

		// Riptide state for the spin animation (20 ticks, 8.0 attack damage).
		player.useRiptide(20, 8.0f, stack);

		// Apply the velocity on the server and set it on the player.
		player.setVelocity(vel.x, vel.y, vel.z);

		// Directly push the velocity to the client so it overrides movement prediction and the
		// launch actually moves the player (vanilla riptide normally needs water; this makes it
		// work in air too).
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
			sp.networkHandler.sendPacket(
					new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(sp));
		}

		// If on the ground, add a small hop so the launch can get airborne.
		if (player.isOnGround()) {
			player.move(net.minecraft.entity.MovementType.SELF, new Vec3d(0, 1.2, 0));
		}
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		// In hoe mode: till like a hoe, and (if Farmers' Delight is loaded) reap mature crops like a knife.
		if (isSwordMode(context.getStack())) {
			return ActionResult.PASS;
		}
		return handleHoeUse(context);
	}

	/** Handles the hoe/sickle-mode block use: tilling and (with FD) reaping crops. */
	private ActionResult handleHoeUse(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();

		// If Farmers' Delight is installed, the sickle acts like a FD knife: right-clicking a mature
		// crop reaps it (collects the drops) and replants it. Otherwise it is a plain hoe.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("farmersdelight")) {
			if (block instanceof net.minecraft.block.CropBlock crop && crop.isMature(state)) {
				return reap(context, state, pos);
			}
		}

		// Otherwise till like a hoe.
		PlayerEntity player = context.getPlayer();
		world.playSound(player, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
		if (!world.isClient()) {
			if (block == net.minecraft.block.Blocks.DIRT
					|| block == net.minecraft.block.Blocks.GRASS_BLOCK
					|| block == net.minecraft.block.Blocks.DIRT_PATH) {
				world.setBlockState(pos, net.minecraft.block.Blocks.FARMLAND.getDefaultState());
				context.getStack().damage(1, (ServerPlayerEntity) player, context.getHand());
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}
		return ActionResult.SUCCESS;
	}

	/** Reaps a mature crop: collects its drops into the player's inventory and replants it. */
	private ActionResult reap(ItemUsageContext context, BlockState state, BlockPos pos) {
		World world = context.getWorld();
		if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) {
			return ActionResult.SUCCESS;
		}
		if (context.getPlayer() instanceof ServerPlayerEntity player) {
			// Collect the crop's drops directly into the player's inventory.
			for (net.minecraft.item.ItemStack drop : net.minecraft.block.Block.getDroppedStacks(state, sw, pos, null)) {
				player.getInventory().offerOrDrop(drop);
			}
			// Replant the crop (reset to its default/young state).
			world.setBlockState(pos, state.getBlock().getDefaultState());
			world.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
					SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
			context.getStack().damage(1, player, context.getHand());
			return ActionResult.SUCCESS_SERVER;
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		String mode = getMode(stack);
		tooltip.accept(Text.literal("Mode: " + mode).formatted(Formatting.DARK_PURPLE));
		tooltip.accept(Text.translatable("item.infernalpages.tainted_remains.tooltip").formatted(Formatting.GRAY));
	}

	/** Shard item for "The remains of a tainted past" — obtained by smashing a broken sword. */
	public static class Shard extends Item {
		public Shard(Settings settings) {
			super(settings);
		}

		@Override
		public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
			tooltip.accept(Text.translatable("item.infernalpages.tainted_shard.tooltip").formatted(Formatting.DARK_PURPLE));
		}
	}
}
