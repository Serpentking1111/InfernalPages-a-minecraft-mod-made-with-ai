package net.infernalpages.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

/**
 * Gives the Mould of Souls flight while it carries the dragon-egg ability. It hovers and steers
 * toward a point a short distance from its target, letting it harry enemies from the air while
 * its ranged dragon-fireball goal fires at them.
 */
public class MouldFlightGoal extends Goal {
	private final MouldOfSoulsEntity mould;

	public MouldFlightGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		return mould.isActiveAI() && mould.getAbility() == GuardAbility.DRAGON;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public void tick() {
		LivingEntity t = mould.getTarget();
		Vec3d current = mould.getEntityPos();
		Vec3d targetPos = t != null ? t.getEntityPos() : current.add(new Vec3d(0, 3, 0));

		// Hover target: offset horizontally from the target by 8 blocks, up at its head height +3.
		Vec3d away = new Vec3d(current.x - targetPos.x, 0, current.z - targetPos.z);
		double horiz = away.length();
		Vec3d dir = horiz > 0.001 ? away.multiply(1.0 / horiz) : new Vec3d(0, 0, 0);
		Vec3d desired = targetPos.add(dir.multiply(8.0)).add(new Vec3d(0, 3.0, 0));

		Vec3d delta = desired.subtract(current);
		double len = delta.length();
		if (len < 0.5) {
			mould.setVelocity(0, 0, 0);
			return;
		}
		Vec3d vel = delta.normalize().multiply(Math.min(0.6, len * 0.12));
		mould.setVelocity(vel.x, vel.y * 0.85, vel.z);
		if (t != null) {
			mould.getLookControl().lookAt(t, 30.0f, 30.0f);
		}
	}
}
