package net.infernalpages.entity;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

/**
 * In {@link GuardMode#ACTIVE} mode, when the mould has no enemy to fight it returns to its original
 * (home) position and waits there, so it keeps guarding its post rather than wandering or following.
 */
public class MouldReturnHomeGoal extends Goal {
	// Matches MouldOfSoulsEntity.isAtHome() so there is no gap where the mould neither walks home
	// nor counts as settled at home.
	private static final double RETURN_DIST = 2.0;

	private final MouldOfSoulsEntity mould;
	private int updateTimer;

	public MouldReturnHomeGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (mould.getMode() != GuardMode.ACTIVE || mould.getTarget() != null) {
			return false;
		}
		BlockPos home = mould.getHomePos();
		return home != null && mould.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5)
				> RETURN_DIST * RETURN_DIST;
	}

	@Override
	public boolean shouldContinue() {
		if (mould.getMode() != GuardMode.ACTIVE || mould.getTarget() != null) {
			return false;
		}
		BlockPos home = mould.getHomePos();
		return home != null && mould.squaredDistanceTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 1.5;
	}

	@Override
	public void start() {
		this.updateTimer = 0;
	}

	@Override
	public void stop() {
		this.mould.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (mould.getTarget() != null) {
			return;
		}
		BlockPos home = mould.getHomePos();
		if (home == null) {
			return;
		}
		if (--this.updateTimer <= 0) {
			this.updateTimer = 10;
			// Walk back to the home position.
			this.mould.getNavigation().startMovingTo(home.getX(), home.getY(), home.getZ(), 1.0);
		}
	}
}
