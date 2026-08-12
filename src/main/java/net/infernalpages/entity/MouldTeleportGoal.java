package net.infernalpages.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * The ender-pearl inspired teleport attack. When the mould has a target that is beyond melee range
 * but still within range, it vanishes and reappears next to the target and strikes it.
 */
public class MouldTeleportGoal extends Goal {
	private final MouldOfSoulsEntity mould;
	private int cooldown;

	public MouldTeleportGoal(MouldOfSoulsEntity mould) {
		this.mould = mould;
		this.setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (!mould.isActiveAI() || mould.getAbility() != GuardAbility.TELEPORT) {
			return false;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}
		LivingEntity t = mould.getTarget();
		if (t == null || !t.isAlive()) {
			return false;
		}
		double dist = mould.distanceTo(t);
		return dist > 3.5 && dist <= 24.0;
	}

	@Override
	public void start() {
		if (!(mould.getEntityWorld() instanceof ServerWorld sw)) {
			return;
		}
		LivingEntity t = mould.getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}

		// Disappear particles at the current location.
		Vec3d oldPos = mould.getEntityPos();
		sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
				oldPos.x, oldPos.y + 0.5, oldPos.z, 24, 0.3, 0.3, 0.3, 0.1);

		// Land just in front of the target, using the ground height there.
		Vec3d fromMould = oldPos.subtract(t.getEntityPos()).normalize();
		int bx = (int) Math.floor(t.getX() + fromMould.x * 1.6);
		int bz = (int) Math.floor(t.getZ() + fromMould.z * 1.6);
		double topY = sw.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, bx, bz);
		double landY = Math.max(topY + 1.0, t.getY());
		mould.refreshPositionAndAngles(bx + 0.5, landY, bz + 0.5, mould.getYaw(), mould.getPitch());
		mould.getNavigation().stop();

		// Reappear particles at the new location.
		Vec3d newPos = mould.getEntityPos();
		sw.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL,
				newPos.x, newPos.y + 0.5, newPos.z, 24, 0.3, 0.3, 0.3, 0.1);
		sw.playSound(null, newPos.x, newPos.y, newPos.z,
				SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 1.0f);

		// Strike the target if we've landed next to it.
		if (mould.distanceTo(t) <= 3.0) {
			t.damage(sw, mould.getDamageSources().mobAttack(mould), 9.0f);
		}
		mould.triggerAbilityAnimation();
		this.cooldown = 50 + mould.getEntityWorld().random.nextInt(30);
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}
}
