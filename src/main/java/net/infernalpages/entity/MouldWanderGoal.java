package net.infernalpages.entity;

import net.minecraft.entity.ai.goal.WanderAroundGoal;

/**
 * Wandering that only runs in {@link GuardMode#HUNT} mode. In ACTIVE mode the mould holds its post
 * (handled by {@link MouldReturnHomeGoal}) and in PASSIVE mode the AI is disabled entirely.
 */
public class MouldWanderGoal extends WanderAroundGoal {
	private final MouldOfSoulsEntity mould;

	public MouldWanderGoal(MouldOfSoulsEntity mould) {
		super(mould, 0.8);
		this.mould = mould;
	}

	@Override
	public boolean canStart() {
		return mould.getMode() == GuardMode.HUNT && super.canStart();
	}

	@Override
	public boolean shouldContinue() {
		return mould.getMode() == GuardMode.HUNT && super.shouldContinue();
	}
}
