package net.infernalpages.item;

import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.infernalpages.entity.TaintedMouldEntity;
import net.infernalpages.registry.ModEntities;

/**
 * The Tainted Mould item. Right-clicking a block deploys the mining automaton at that spot, owned
 * by the player who placed it. Single use.
 */
public class TaintedMouldItem extends Item {
	public TaintedMouldItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (context.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
			return ActionResult.PASS;
		}
		ServerWorld world = (ServerWorld) context.getWorld();
		BlockPos pos = context.getBlockPos();
		Direction side = context.getSide();
		BlockPos spawnPos = pos.offset(side).up();

		TaintedMouldEntity mould = ModEntities.TAINTED_MOULD.create(world, SpawnReason.MOB_SUMMONED);
		if (mould == null) {
			return ActionResult.FAIL;
		}
		mould.setOwnerUuid(player.getUuid());
		mould.refreshPositionAndAngles(
				spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYaw(), 0.0f);
		world.spawnEntity(mould);

		context.getStack().decrement(1);
		player.sendMessage(Text.literal("A Tainted Mould forms, ready to mine for you. Feed it an ore to set it to work.")
				.formatted(Formatting.DARK_PURPLE), false);
		return ActionResult.SUCCESS_SERVER;
	}
}
