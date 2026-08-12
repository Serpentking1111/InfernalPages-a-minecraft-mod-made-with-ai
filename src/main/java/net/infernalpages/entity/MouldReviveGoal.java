package net.infernalpages.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.infernalpages.registry.ModEntities;
import net.infernalpages.registry.ModItems;

/**
 * Makes a Mould of Souls pick up a dropped {@link ModItems#MOULD_OF_SOULS} item (left behind when
 * another mould died) and resummon a new mould in its place. The new mould inherits this mould's
 * owner. Ability items are deliberately not picked up.
 */
public class MouldReviveGoal extends Goal {
	private static final double SEARCH_RANGE = 12.0;
	private static final double PICKUP_DIST = 2.0;

	private final MouldOfSoulsEntity mould;
	private ItemEntity target;
	private int updateTimer;

	public MouldReviveGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE));
	}

	private ItemEntity findItem() {
		Box box = this.mould.getBoundingBox().expand(SEARCH_RANGE);
		for (ItemEntity item : this.mould.getEntityWorld().getEntitiesByType(
				TypeFilter.instanceOf(ItemEntity.class), box, Entity::isAlive)) {
			ItemStack stack = item.getStack();
			if (!stack.isEmpty() && stack.isOf(ModItems.MOULD_OF_SOULS)) {
				return item;
			}
		}
		return null;
	}

	@Override
	public boolean canStart() {
		if (!mould.isActiveAI() || mould.getTarget() != null) {
			return false;
		}
		this.target = findItem();
		return this.target != null;
	}

	@Override
	public boolean shouldContinue() {
		return this.target != null && this.target.isAlive()
				&& mould.isActiveAI() && mould.getTarget() == null;
	}

	@Override
	public void start() {
		this.updateTimer = 0;
	}

	@Override
	public void stop() {
		this.target = null;
		this.mould.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (this.target == null || !this.target.isAlive()) {
			this.target = findItem();
			if (this.target == null) {
				return;
			}
		}
		// Walk toward the dropped item.
		if (--this.updateTimer <= 0) {
			this.updateTimer = 10;
			this.mould.getNavigation().startMovingTo(this.target, 1.2);
		}
		// Once close enough, consume it and resummon a new mould with our owner.
		if (this.mould.squaredDistanceTo(this.target) <= PICKUP_DIST * PICKUP_DIST) {
			resummon();
		}
	}

	private void resummon() {
		if (!(this.mould.getEntityWorld() instanceof ServerWorld sw)) {
			return;
		}
		Vec3d pos = this.target.getEntityPos();
		this.target.discard();
		this.target = null;

		MouldOfSoulsEntity revived = ModEntities.MOULD_OF_SOULS.create(sw, SpawnReason.MOB_SUMMONED);
		if (revived == null) {
			return;
		}
		revived.setOwnerUuid(this.mould.getOwnerUuid());
		revived.refreshPositionAndAngles(pos.x, pos.y, pos.z, this.mould.getYaw(), 0.0f);
		revived.setHomePos(new net.minecraft.util.math.BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
		sw.spawnEntity(revived);
	}
}
