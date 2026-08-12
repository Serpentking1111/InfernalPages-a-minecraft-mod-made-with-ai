package net.infernalpages.effect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.infernalpages.InfernalPagesMod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Scripture's "calamity" sequence: a 5-second storm of escalating explosions and lightning.
 *
 * <p>Schedule (server ticks; 20 ticks = 1 second):
 * <ul>
 *   <li>Every {@value #TICKS_PER_QUARTER} ticks (0.25s): several lightning bolts strike around the
 *       origin.</li>
 *   <li>Every {@value #TICKS_PER_HALF} ticks (0.5s): an explosion fires. The power escalates by
 *       {@value #ESCALATION}x each explosion, and <em>every other</em> explosion can destroy
 *       blocks.</li>
 * </ul>
 *
 * <p>Sequences are processed on the server tick and clean themselves up after
 * {@value #TOTAL_TICKS} ticks (5 seconds) or when their world unloads.
 */
public final class ScriptureCalamity {
	private static final List<Active> ACTIVE = new ArrayList<>();

	/** Ticks between lightning bursts (5 ticks = 0.25s). */
	private static final int TICKS_PER_QUARTER = 5;
	/** Ticks between explosions (10 ticks = 0.5s). */
	private static final int TICKS_PER_HALF = 10;
	/** Total duration in ticks (100 = 5s). */
	private static final int TOTAL_TICKS = 100;
	/** Number of lightning bolts per burst. */
	private static final int BOLTS_PER_BURST = 5;
	/** Power multiplier per explosion step. */
	private static final float ESCALATION = 1.25f;

	private ScriptureCalamity() {
	}

	/** Registers the server-tick handler that advances all active calamities. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ScriptureCalamity::onServerTick);
	}

	/** Begins a calamity at the given position. */
	public static void start(ServerWorld world, Vec3d origin) {
		ACTIVE.add(new Active(world, origin, InfernalPagesMod.CONFIG.calamityBasePower));
	}

	private static void onServerTick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Active> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Active a = it.next();
			if (a.world == null || a.world.getServer() != server) {
				it.remove();
				continue;
			}
			if (a.tick()) {
				it.remove();
			}
		}
	}

	private static void summonBolt(ServerWorld world, Vec3d pos) {
		LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
		bolt.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		world.spawnEntity(bolt);
	}

	private static final class Active {
		final ServerWorld world;
		final Vec3d origin;
		final float basePower;
		int elapsed = 0;

		Active(ServerWorld world, Vec3d origin, float basePower) {
			this.world = world;
			this.origin = origin;
			this.basePower = basePower;
		}

		/** Advances one tick. Returns true when the calamity is finished. */
		boolean tick() {
			if (elapsed > TOTAL_TICKS) {
				return true;
			}

			// Lightning: several bolts every 0.25s.
			if (elapsed % TICKS_PER_QUARTER == 0) {
				for (int i = 0; i < BOLTS_PER_BURST; i++) {
					double dx = world.random.nextGaussian() * 2.0;
					double dz = world.random.nextGaussian() * 2.0;
					double dy = world.random.nextInt(3) - 1;
					summonBolt(world, origin.add(dx, dy, dz));
				}
			}

			// Explosions: every 0.5s, escalating, alternating block destruction.
			if (elapsed % TICKS_PER_HALF == 0) {
				int index = elapsed / TICKS_PER_HALF;
				// Every other explosion (odd index) can destroy blocks.
				World.ExplosionSourceType type = (index % 2 == 1)
						? World.ExplosionSourceType.TNT
						: World.ExplosionSourceType.NONE;
				float power = (float) (basePower * Math.pow(ESCALATION, index));
				world.createExplosion(null, origin.x, origin.y, origin.z, power, false, type);
			}

			elapsed++;
			return elapsed > TOTAL_TICKS;
		}
	}
}
