package net.infernalpages.item;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.infernalpages.entity.MouldOfSoulsEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * "The Calling Horn" — crafted shapeless with a goat horn + a Mould of Souls item.
 * When used, it summons all of the user's soul moulds to them.
 */
public class CallingHornItem extends Item {
	private static final int COOLDOWN_TICKS = 200; // 10 seconds
	private static final double SEARCH_RANGE = 256.0;

	public CallingHornItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(world instanceof ServerWorld serverWorld) || !(user instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		ItemStack stack = user.getStackInHand(hand);
		if (serverPlayer.getItemCooldownManager().isCoolingDown(stack)) {
			return ActionResult.PASS;
		}

		// Find all moulds owned by this player within range.
		Box searchBox = new Box(user.getBlockPos()).expand(SEARCH_RANGE);
		List<MouldOfSoulsEntity> moulds = serverWorld.getEntitiesByType(
				TypeFilter.instanceOf(MouldOfSoulsEntity.class), searchBox,
				mould -> mould.isOwner(serverPlayer) && mould.isAlive());

		if (moulds.isEmpty()) {
			serverPlayer.sendMessage(Text.literal("No Soul Moulds found nearby to call.")
					.formatted(Formatting.GRAY), true);
			serverPlayer.getItemCooldownManager().set(stack, 40);
			return ActionResult.SUCCESS_SERVER;
		}

		int summoned = 0;
		for (MouldOfSoulsEntity mould : moulds) {
			// Skip moulds already very close.
			if (mould.squaredDistanceTo(serverPlayer) < 9) {
				continue;
			}

			double offsetX = (world.random.nextDouble() - 0.5) * 4.0;
			double offsetZ = (world.random.nextDouble() - 0.5) * 4.0;
			double targetX = serverPlayer.getX() + offsetX;
			double targetZ = serverPlayer.getZ() + offsetZ;
			double targetY = serverPlayer.getY();
			try {
				double topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
						(int) Math.floor(targetX), (int) Math.floor(targetZ));
				if (Math.abs(topY - serverPlayer.getY()) < 10) {
					targetY = topY;
				}
			} catch (Exception ignored) {
				// fall back to the player's Y
			}

			mould.refreshPositionAndAngles(targetX, targetY, targetZ, mould.getYaw(), mould.getPitch());
			mould.getNavigation().stop();
			mould.setTarget(null);
			// Set the new home to near the player so ACTIVE moulds guard the new location.
			mould.setHomePos(serverPlayer.getBlockPos());

			Vec3d pos = mould.getEntityPos();
			serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
					pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.5, 0.3, 0.1);
			serverWorld.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL,
					targetX, targetY + 0.5, targetZ, 15, 0.3, 0.5, 0.3, 0.05);

			summoned++;
		}

		if (summoned > 0) {
			world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
					SoundEvents.ENTITY_GOAT_HORN_BREAK, SoundCategory.PLAYERS, 1.2f, 0.9f);
			world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
					SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.2f);
			serverPlayer.sendMessage(Text.literal("Called " + summoned + " Soul Mould" + (summoned == 1 ? "" : "s") + " to you!")
					.formatted(Formatting.DARK_PURPLE), true);
			serverPlayer.getItemCooldownManager().set(stack, COOLDOWN_TICKS);
		} else {
			serverPlayer.sendMessage(Text.literal("Your Soul Moulds are already nearby.")
					.formatted(Formatting.GRAY), true);
			serverPlayer.getItemCooldownManager().set(stack, 40);
		}

		return ActionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		tooltip.accept(Text.literal("Summons all your Soul Moulds to you").formatted(Formatting.DARK_PURPLE));
		tooltip.accept(Text.literal("Range: " + (int) SEARCH_RANGE + " blocks").formatted(Formatting.GRAY));
		tooltip.accept(Text.translatable("item.infernalpages.calling_horn.tooltip").formatted(Formatting.DARK_GRAY));
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable(this.getTranslationKey()).formatted(Formatting.LIGHT_PURPLE);
	}
}
