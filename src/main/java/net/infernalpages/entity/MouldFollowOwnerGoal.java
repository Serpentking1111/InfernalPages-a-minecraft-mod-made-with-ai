package net.infernalpages.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Makes the Mould of Souls follow its owner when it is guarding and has no enemy to fight, staying
 * a short distance from them instead of wandering off or standing still far away.
 */
public class MouldFollowOwnerGoal extends Goal {
	private static final double FOLLOW_DIST = 3.0;
	private static final double FOLLOW_MAX = 28.0;

	private final MouldOfSoulsEntity mould;
	private PlayerEntity owner;
	private int updateTimer;

	public MouldFollowOwnerGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (mould.getMode() != GuardMode.HUNT || mould.getTarget() != null) {
			return false;
		}
		this.owner = mould.getOwnerPlayer();
		if (this.owner == null || !this.owner.isAlive() || this.owner.isSpectator()) {
			return false;
		}
		return mould.squaredDistanceTo(this.owner) > FOLLOW_DIST * FOLLOW_DIST
				&& mould.squaredDistanceTo(this.owner) < FOLLOW_MAX * FOLLOW_MAX;
	}

	@Override
	public boolean shouldContinue() {
		if (mould.getMode() != GuardMode.HUNT || this.owner == null || !this.owner.isAlive() || mould.getTarget() != null) {
			return false;
		}
		// Stop once we've closed the gap.
		return mould.squaredDistanceTo(this.owner) > 1.5 * 1.5;
	}

	@Override
	public void start() {
		this.updateTimer = 0;
	}

	@Override
	public void stop() {
		this.owner = null;
		this.mould.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (mould.getMode() != GuardMode.HUNT || this.owner == null) {
			return;
		}
		this.mould.getLookControl().lookAt(this.owner, 10.0f, 10.0f);
		if (--this.updateTimer <= 0) {
			this.updateTimer = 10;
			// Path to a point just beside the owner so the mould stops near them rather than on top.
			BlockPos targetPos = BlockPos.ofFloored(this.owner.getX(), this.owner.getY(), this.owner.getZ())
					.offset(this.owner.getHorizontalFacing().getOpposite());
			if (mould.squaredDistanceTo(this.owner) > FOLLOW_DIST * FOLLOW_DIST) {
				this.mould.getNavigation().startMovingTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
			}
		}
	}
}
