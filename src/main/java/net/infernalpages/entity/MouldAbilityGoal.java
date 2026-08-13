package net.infernalpages.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * The ranged attack goal for the Mould of Souls. Depending on the equipped ability it fires a
 * wind charge (breeze-style), a fireball (ghast-style), a dragon fireball, or fires a guardian
 * style hitscan beam at its current target.
 */
public class MouldAbilityGoal extends Goal {
	private final MouldOfSoulsEntity mould;
	private LivingEntity target;
	private int cooldown;

	public MouldAbilityGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
	}

	private boolean isRanged() {
		GuardAbility a = mould.getAbility();
		return a == GuardAbility.WIND || a == GuardAbility.FIRE
				|| a == GuardAbility.GUARDIAN || a == GuardAbility.DRAGON;
	}

	private double attackRange() {
		return switch (mould.getAbility()) {
			case WIND -> 18.0;
			case FIRE -> 18.0;
			case GUARDIAN -> 15.0;
			case DRAGON -> 26.0;
			default -> 0.0;
		};
	}

	@Override
	public boolean canStart() {
		if (!mould.isActiveAI() || !isRanged()) {
			return false;
		}
		LivingEntity t = mould.getTarget();
		if (t == null || !t.isAlive()) {
			return false;
		}
		this.target = t;
		double range = attackRange();
		return mould.squaredDistanceTo(t) <= range * range && mould.canSee(t);
	}

	@Override
	public boolean shouldContinue() {
		return this.target != null && this.target.isAlive() && mould.canSee(this.target);
	}

	@Override
	public void start() {
		this.cooldown = 10;
	}

	@Override
	public void stop() {
		this.target = null;
	}

	@Override
	public void tick() {
		if (this.target == null || !this.target.isAlive()) {
			return;
		}
		mould.getLookControl().lookAt(this.target, 30.0f, 30.0f);
		mould.getNavigation().stop();
		if (this.cooldown > 0) {
			this.cooldown--;
			return;
		}
		fire();
		this.cooldown = 26 + mould.getEntityWorld().random.nextInt(14);
	}

	private void fire() {
		switch (mould.getAbility()) {
			case WIND -> fireWindCharge();
			case FIRE -> fireFireball();
			case GUARDIAN -> fireBeam();
			case DRAGON -> fireDragonFireball();
			default -> {}
		}
	}

	/** Normalised aim direction from the mould's eye to the target's centre. */
	private Vec3d aim() {
		Vec3d origin = mould.getEyePos();
		Vec3d targetVec = this.target.getBoundingBox().getCenter();
		return targetVec.subtract(origin).normalize();
	}

	private void fireWindCharge() {
		Vec3d dir = aim();
		WindChargeEntity charge = new WindChargeEntity(
				net.minecraft.entity.EntityType.WIND_CHARGE, mould.getEntityWorld());
		charge.setOwner(mould);
		charge.setPosition(mould.getX(), mould.getEyeY(), mould.getZ());
		charge.setVelocity(dir.x, dir.y, dir.z, 0.9f, 3.0f);
		if (mould.getEntityWorld() instanceof ServerWorld sw) {
			sw.spawnEntity(charge);
			sw.playSound(null, mould.getX(), mould.getEyeY(), mould.getZ(),
					SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.0f);
		}
		mould.triggerAbilityAnimation();
	}

	private void fireFireball() {
		Vec3d vel = aim().multiply(1.15);
		SmallFireballEntity ball = new SmallFireballEntity(mould.getEntityWorld(), mould, vel);
		ball.setPosition(mould.getX(), mould.getEyeY(), mould.getZ());
		if (mould.getEntityWorld() instanceof ServerWorld sw) {
			sw.spawnEntity(ball);
			sw.playSound(null, mould.getX(), mould.getEyeY(), mould.getZ(),
					SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.0f);
		}
		mould.triggerAbilityAnimation();
	}

	private void fireDragonFireball() {
		Vec3d vel = aim().multiply(1.0);
		DragonFireballEntity ball = new DragonFireballEntity(mould.getEntityWorld(), mould, vel);
		ball.setPosition(mould.getX(), mould.getEyeY(), mould.getZ());
		if (mould.getEntityWorld() instanceof ServerWorld sw) {
			sw.spawnEntity(ball);
			sw.playSound(null, mould.getX(), mould.getEyeY(), mould.getZ(),
					SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.0f, 1.0f);
		}
		mould.triggerAbilityAnimation();
	}

	/** Guardian-style beam: a hitscan strike against the target if it is in line of sight. */
	private void fireBeam() {
		if (!(mould.getEntityWorld() instanceof ServerWorld sw)) {
			return;
		}
		this.target.damage(sw, mould.getDamageSources().indirectMagic(mould, mould), 8.0f);
		// Spawn a trail of particles from the mould to the target to hint at the beam.
		Vec3d from = mould.getEyePos();
		Vec3d to = this.target.getEyePos();
		Vec3d step = to.subtract(from).multiply(0.1);
		for (int i = 0; i < 10; i++) {
			Vec3d p = from.add(step.multiply(i));
			sw.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
					p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		sw.playSound(null, this.target.getX(), this.target.getY(), this.target.getZ(),
				SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.0f, 1.0f);
		mould.triggerAbilityAnimation();
	}
}
