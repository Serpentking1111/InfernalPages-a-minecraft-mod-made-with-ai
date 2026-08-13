package net.infernalpages.util;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Small helpers for world effects (lightning strikes etc.).
 */
public final class EffectUtil {
	private EffectUtil() {
	}

	/** Summons a decorative lightning bolt at the given world position. */
	public static void summonLightning(ServerWorld world, Vec3d pos) {
		LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
		bolt.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		world.spawnEntity(bolt);
	}
}
