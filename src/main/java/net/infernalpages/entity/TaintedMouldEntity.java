package net.infernalpages.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.infernalpages.registry.ModItems;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

/**
 * The Tainted Mould — a mining automaton forged from a Mould of Souls, a Tainted Shard and netherite.
 *
 * <p>Feed it an ore resource (iron, gold, diamond, netherite, lapis, redstone, emerald or coal)
 * and it will travel to the nearest matching ore and collect only that ore's drops.
 *
 * <p>Its mining is deliberately constrained so it behaves like something physically digging rather
 * than a remote block-deleter:
 * <ul>
 *   <li>it must have <b>line of sight</b> to an ore before it may break it;</li>
 *   <li>it may only break blocks within <b>{@value #BREAK_RADIUS_BLOCKS} blocks</b> of itself;</li>
 *   <li>if it cannot see its target it breaks precisely the blocks obstructing its view, and if the
 *       target is out of range it tunnels along the straight line towards it.</li>
 * </ul> Once it holds a full stack it teleports back to
 * its owner and deposits the haul. It is animated by the custom {@code taintedmould} model/animations
 * (static / scan / run / mine / teliport).
 */
public class TaintedMouldEntity extends PathAwareEntity implements GeoEntity {
	/** Horizontal search radius (blocks) used to find ores. */
	private static final int SEARCH_RADIUS = 32;
	/** Vertical search range (blocks) above/below the mould used to find ores. */
	private static final int SEARCH_HEIGHT = 12;
	/** Number of result items that makes a "full stack" and triggers the return home. */
	private static final int STACK_SIZE = 64;
	/**
	 * The block-reach distance (blocks) at which the mould can break a block. The mould will never
	 * break anything further away than this from its own position — it has to walk or tunnel
	 * closer first.
	 */
	private static final double BREAK_RADIUS = 5.0;
	/** Integer form of {@link #BREAK_RADIUS}, used to bound block scans. */
	private static final int BREAK_RADIUS_BLOCKS = (int) Math.ceil(BREAK_RADIUS);
	/** Distance (blocks) between samples along the line-of-sight ray. */
	private static final double SIGHT_STEP = 0.15;
	/** Ticks the mould stands still scanning (playing the "scan" animation) after each ore break. */
	private static final int SCAN_PAUSE_TICKS = 30;
	/** Safety valve: forget the "can't get there" list once it grows past this many entries. */
	private static final int MAX_SKIPPED_ORES = 128;

	private enum Mode { IDLE, MINING }

	// Client-synced animation flags (1 = play the one-shot animation).
	private static final TrackedData<Byte> MODE_DATA = DataTracker.registerData(TaintedMouldEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> MINE_DATA = DataTracker.registerData(TaintedMouldEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> TELEPORT_DATA = DataTracker.registerData(TaintedMouldEntity.class, TrackedDataHandlerRegistry.BYTE);

	private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

	// Server-side mining state.
	private Mode mode = Mode.IDLE;
	private TaintedOreType oreType;
	private BlockPos targetOre;
	private int collectedCount;
	private int thinkTimer;
	private int searchCooldown;
	private int mineAnimTimer;
	private int teleportAnimTimer;
	private java.util.UUID ownerUuid;
	/**
	 * Ores the mould has given up on because it could neither see them nor dig any closer. They
	 * are excluded from target selection so it moves on to the next ore instead of jamming against
	 * bedrock or a protected block forever. Cleared whenever it successfully mines something.
	 */
	private final java.util.Set<BlockPos> skippedOres = new java.util.HashSet<>();

	public TaintedMouldEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.setPersistent();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(MODE_DATA, (byte) 0);       // IDLE
		builder.add(MINE_DATA, (byte) 0);
		builder.add(TELEPORT_DATA, (byte) 0);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.MAX_HEALTH, 40.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.ARMOR, 4.0);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		// Main controller: run while moving to ore, scan while searching, static while idle.
		controllers.add(new AnimationController<TaintedMouldEntity>("movement", 4, state -> {
			// Returning home plays the teleport one-shot over everything.
			if (this.dataTracker.get(TELEPORT_DATA) == 1) {
				state.setAnimation(RawAnimation.begin().thenPlay("teliport"));
				return PlayState.CONTINUE;
			}
			if (this.dataTracker.get(MINE_DATA) == 1) {
				state.setAnimation(RawAnimation.begin().thenPlay("mine"));
				return PlayState.CONTINUE;
			}
			if (this.dataTracker.get(MODE_DATA) == 1) { // MINING
				if (state.isMoving()) {
					state.setAnimation(RawAnimation.begin().thenLoop("run"));
					return PlayState.CONTINUE;
				}
				// Standing still while mining = scanning/searching for the next ore.
				state.setAnimation(RawAnimation.begin().thenLoop("scan"));
				return PlayState.CONTINUE;
			}
			// IDLE: play the static pose once and hold.
			state.setAnimation(RawAnimation.begin().thenPlay("static"));
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	public net.minecraft.util.ActionResult interact(PlayerEntity player, Hand hand) {
		if (this.getEntityWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!isOwner(player)) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getStackInHand(hand);

		// Feeding an ore resource starts a mining run.
		TaintedOreType type = TaintedOreType.fromItem(stack.getItem());
		if (type != null) {
			this.oreType = type;
			// The fed item counts as the first item of the stack it will return with, so the mould
			// only needs to mine the remaining (STACK_SIZE - 1) items before returning home.
			this.collectedCount = 1;
			this.targetOre = null;
			this.skippedOres.clear();
			this.mode = Mode.MINING;
			this.searchCooldown = 0;
			this.dataTracker.set(MODE_DATA, (byte) 1);
			stack.decrement(1);
			player.sendMessage(Text.literal("Tainted Mould will mine " + type.displayName() + ".")
					.formatted(Formatting.DARK_PURPLE), true);
			return ActionResult.SUCCESS_SERVER;
		}

		// Empty hand (or non-ore): report status.
		if (oreType != null && mode == Mode.MINING) {
			player.sendMessage(Text.literal("Mining " + oreType.displayName() + ": "
					+ collectedCount + "/" + STACK_SIZE + " collected.")
					.formatted(Formatting.DARK_PURPLE), true);
		} else {
			player.sendMessage(Text.literal("Tainted Mould is idle. Feed it "
					+ TaintedOreType.feedItemList() + " to send it mining.")
					.formatted(Formatting.DARK_PURPLE), true);
		}
		return ActionResult.SUCCESS_SERVER;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getEntityWorld().isClient()) {
			return;
		}
		// Decrement one-shot animation timers and clear the synced flags when they finish.
		if (this.mineAnimTimer > 0) {
			if (--this.mineAnimTimer == 0) {
				this.dataTracker.set(MINE_DATA, (byte) 0);
			}
		}
		if (this.teleportAnimTimer > 0) {
			if (--this.teleportAnimTimer == 0) {
				this.dataTracker.set(TELEPORT_DATA, (byte) 0);
			}
		}
		if (this.thinkTimer-- > 0) {
			return;
		}
		this.thinkTimer = 4; // think a few times a second

		if (this.mode == Mode.MINING) {
			this.doMining();
		}
	}

	/** One step of the mining loop: validate target, mine, tunnel, or move toward the ore. */
	private void doMining() {
		if (this.oreType == null) {
			this.mode = Mode.IDLE;
			return;
		}
		// A full stack is collected: return home and deliver.
		if (this.collectedCount >= STACK_SIZE) {
			this.returnToOwner();
			return;
		}
		// After mining an ore the mould stands still and plays the "scan" animation. Hold off on
		// ALL mining work — including tunnelling — until that pause is over, otherwise the mould
		// keeps chewing through blocks while it visibly appears to be scanning.
		if (this.searchCooldown > 0) {
			this.searchCooldown--;
			this.getNavigation().stop();
			return;
		}

		// (Re)acquire a target ore if we don't have a valid one.
		if (this.targetOre == null || !isTargetOre(this.targetOre)) {
			this.reselectOre();
			if (this.targetOre == null) {
				// Everything reachable has been tried and skipped: clear the skip list and give
				// the remaining ores one more chance before reporting failure.
				if (!this.skippedOres.isEmpty()) {
					this.skippedOres.clear();
					this.reselectOre();
				}
				if (this.targetOre == null) {
					this.notifyOwner("No " + this.oreType.displayName() + " found within "
							+ SEARCH_RADIUS + " blocks.");
					this.mode = Mode.IDLE;
					this.dataTracker.set(MODE_DATA, (byte) 0);
					return;
				}
			}
		}

		// The mould must actually be able to SEE an ore before it may mine it, and it may only
		// break blocks within BREAK_RADIUS of itself. Anything it cannot see, it digs towards.

		// 1. Ores generate in veins, so once we are inside one there are usually more target ore
		//    blocks in range. Harvest any we can both reach and see, nearest to the target first.
		BlockPos visibleOre = findVisibleTargetOre();
		if (visibleOre != null) {
			this.breakOre(visibleOre);
			if (visibleOre.equals(this.targetOre)) {
				this.targetOre = null;
			}
			return;
		}

		// 2. The target is in break range but hidden behind something. Chew through whatever is
		//    obstructing the line of sight — that is exactly the block the mould "needs" to remove
		//    in order to see the ore.
		if (withinBreakRadius(this.targetOre)) {
			BlockPos obstruction = findSightObstruction(this.targetOre);
			if (obstruction != null) {
				this.breakBlockNoDrops(obstruction);
				return;
			}
			// In range, unobstructed, but findVisibleTargetOre rejected it (e.g. the ore is no
			// longer there). Drop the target and pick another next tick.
			this.targetOre = null;
			return;
		}

		// 3. The ore is out of break range. Walk towards it if a path exists, otherwise tunnel
		//    along the straight line to it, which is what lets the mould reach fully buried ores.
		if (this.tryMoveToward(this.targetOre)) {
			return;
		}
		BlockPos digStep = findDigStepToward(this.targetOre);
		if (digStep != null) {
			this.breakBlockNoDrops(digStep);
			return;
		}

		// 4. Cannot see it, cannot path to it, cannot dig towards it (bedrock, claim protection,
		//    a block entity we refuse to destroy). Give up on this ore and try the next one.
		if (this.skippedOres.size() < MAX_SKIPPED_ORES) {
			this.skippedOres.add(this.targetOre);
		} else {
			this.skippedOres.clear();
		}
		this.targetOre = null;
	}

	/**
	 * Starts (or continues) navigation towards {@code pos}. Returns true if the mould has a usable
	 * path, false if it is walled in and needs to dig instead.
	 */
	private boolean tryMoveToward(BlockPos pos) {
		if (!this.getNavigation().isIdle()) {
			return true; // already walking somewhere
		}
		return this.getNavigation().startMovingTo(
				pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
	}

	/** Picks the closest matching ore, regardless of whether it is exposed. */
	private void reselectOre() {
		this.targetOre = findNearestOre();
	}

	/**
	 * Scans a box around the mould for the closest matching ore.
	 *
	 * <p>The search walks outward in shells of increasing Chebyshev radius and stops the moment a
	 * shell contains an ore, returning the Euclidean-closest block in that shell (which is the
	 * global nearest). This makes target detection far cheaper: instead of always sweeping the
	 * whole 65&times;65&times;25 volume (~105k block reads) it usually stops after the first few
	 * hundred, so the mould "sees" where it needs to go and starts moving much sooner. Selection
	 * is unchanged — it still picks the nearest ore.
	 */
	private BlockPos findNearestOre() {
		if (this.oreType == null) {
			return null;
		}
		World world = this.getEntityWorld();
		BlockPos base = this.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		int maxR = Math.max(SEARCH_RADIUS, SEARCH_HEIGHT);
		for (int r = 0; r <= maxR; r++) {
			BlockPos bestInShell = null;
			double bestD = Double.MAX_VALUE;
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						// Only the surface of this shell (otherwise every block is visited at r=maxR).
						if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
							continue;
						}
						// Skip anything outside the search box.
						if (Math.abs(dx) > SEARCH_RADIUS || Math.abs(dy) > SEARCH_HEIGHT
								|| Math.abs(dz) > SEARCH_RADIUS) {
							continue;
						}
						mutable.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
						if (!this.oreType.isOre(world.getBlockState(mutable))) {
							continue;
						}
						if (this.skippedOres.contains(mutable)) {
							continue; // already proved unreachable
						}
						double d = squaredDistanceToCenter(mutable);
						if (d < bestD) {
							bestD = d;
							bestInShell = mutable.toImmutable();
						}
					}
				}
			}
			if (bestInShell != null) {
				return bestInShell;
			}
		}
		return null;
	}

	/**
	 * True if {@code pos} is inside the mould's 5-block break radius. This is a hard limit: the
	 * mould may never break a block further away than this, so distant ores have to be walked or
	 * tunnelled to first.
	 */
	private boolean withinBreakRadius(BlockPos pos) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dy = this.eyeY() - (pos.getY() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= BREAK_RADIUS * BREAK_RADIUS;
	}

	/** The height the mould "looks" from when testing line of sight. */
	private double eyeY() {
		return this.getY() + this.getStandingEyeHeight();
	}

	/** The point the mould looks from. */
	private Vec3d sightOrigin() {
		return new Vec3d(this.getX(), this.eyeY(), this.getZ());
	}

	/**
	 * True if the mould has clear line of sight to the block at {@code pos} — that is, the straight
	 * line from its eye to the block's centre passes through nothing but air, fluids and other
	 * non-occluding blocks. The target block itself does not count as blocking its own view.
	 */
	private boolean hasLineOfSight(BlockPos pos) {
		return findSightObstruction(pos) == null && isSightClearOfSolids(pos);
	}

	/**
	 * Walks the ray from the mould's eye to the centre of {@code pos} and returns the first
	 * <em>breakable</em> block obscuring the view, or null if the view is clear (or the only thing
	 * in the way is something the mould is not allowed to break).
	 *
	 * <p>This is what powers "if it can't see the ore, break the blocks it needs to": the returned
	 * position is precisely the next block to remove to open up the sightline.
	 */
	private BlockPos findSightObstruction(BlockPos pos) {
		World world = this.getEntityWorld();
		Vec3d from = sightOrigin();
		Vec3d to = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Vec3d delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-4) {
			return null;
		}
		Vec3d step = delta.multiply(SIGHT_STEP / length);
		int steps = MathHelper.ceil(length / SIGHT_STEP);
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos last = null;
		for (int i = 1; i < steps; i++) {
			double x = from.x + step.x * i;
			double y = from.y + step.y * i;
			double z = from.z + step.z * i;
			mutable.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
			if (mutable.equals(pos) || mutable.equals(last)) {
				continue;
			}
			last = mutable.toImmutable();
			BlockState state = world.getBlockState(mutable);
			if (!blocksSight(state, mutable)) {
				continue;
			}
			// Something is in the way. Only report it if we are actually allowed to remove it, and
			// only if it is close enough to break.
			BlockPos hit = mutable.toImmutable();
			if (isBreakable(state, hit) && withinBreakRadius(hit)) {
				return hit;
			}
			return null;
		}
		return null;
	}

	/**
	 * True if nothing unbreakable stands between the mould and {@code pos}. Used together with
	 * {@link #findSightObstruction} so that "no breakable obstruction" is not mistaken for "clear
	 * view" when the obstruction is bedrock.
	 */
	private boolean isSightClearOfSolids(BlockPos pos) {
		World world = this.getEntityWorld();
		Vec3d from = sightOrigin();
		Vec3d to = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Vec3d delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-4) {
			return true;
		}
		Vec3d step = delta.multiply(SIGHT_STEP / length);
		int steps = MathHelper.ceil(length / SIGHT_STEP);
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int i = 1; i < steps; i++) {
			mutable.set(MathHelper.floor(from.x + step.x * i),
					MathHelper.floor(from.y + step.y * i),
					MathHelper.floor(from.z + step.z * i));
			if (mutable.equals(pos)) {
				continue;
			}
			if (blocksSight(world.getBlockState(mutable), mutable)) {
				return false;
			}
		}
		return true;
	}

	/** True if this block state would hide an ore behind it. */
	private boolean blocksSight(BlockState state, BlockPos pos) {
		return !state.isAir() && state.isOpaqueFullCube();
	}

	/**
	 * The next block to break in order to tunnel towards {@code target}. Steps along the straight
	 * line to the ore and returns the first breakable block inside the break radius, so the mould
	 * digs a corridor straight at buried ores instead of wandering around them.
	 */
	private BlockPos findDigStepToward(BlockPos target) {
		World world = this.getEntityWorld();
		Vec3d from = sightOrigin();
		Vec3d to = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		Vec3d delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-4) {
			return null;
		}
		Vec3d step = delta.multiply(SIGHT_STEP / length);
		int steps = MathHelper.ceil(length / SIGHT_STEP);
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos last = null;
		for (int i = 1; i < steps; i++) {
			mutable.set(MathHelper.floor(from.x + step.x * i),
					MathHelper.floor(from.y + step.y * i),
					MathHelper.floor(from.z + step.z * i));
			if (mutable.equals(last)) {
				continue;
			}
			last = mutable.toImmutable();
			BlockPos candidate = mutable.toImmutable();
			if (!withinBreakRadius(candidate)) {
				return null; // reached the edge of our reach without finding anything to dig
			}
			BlockState state = world.getBlockState(candidate);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			if (!isBreakable(state, candidate)) {
				return null; // bedrock or a protected block bars this route
			}
			return candidate;
		}
		return null;
	}

	/**
	 * Finds a target-ore block within reach, nearest to the current ore target. Used so the mould
	 * harvests the rest of a vein it is standing in rather than tunnelling past it.
	 */
	private BlockPos findVisibleTargetOre() {
		if (this.oreType == null) {
			return null;
		}
		World world = this.getEntityWorld();
		BlockPos mouldPos = this.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -BREAK_RADIUS_BLOCKS; dx <= BREAK_RADIUS_BLOCKS; dx++) {
			for (int dy = -BREAK_RADIUS_BLOCKS; dy <= BREAK_RADIUS_BLOCKS; dy++) {
				for (int dz = -BREAK_RADIUS_BLOCKS; dz <= BREAK_RADIUS_BLOCKS; dz++) {
					mutable.set(mouldPos.getX() + dx, mouldPos.getY() + dy, mouldPos.getZ() + dz);
					if (!withinBreakRadius(mutable) || !this.oreType.isOre(world.getBlockState(mutable))) {
						continue;
					}
					double d = squaredDistanceToOre(mutable);
					if (d >= bestD) {
						continue;
					}
					// Line of sight is the expensive test, so it goes last.
					if (!hasLineOfSight(mutable)) {
						continue;
					}
					// An ore face-adjacent to the target (d == 1.0) is as close as a distinct block
					// can get, so stop scanning the rest of the box — this keeps the per-tick
					// detection cheap while the mould is standing in a vein.
					if (d <= 1.0) {
						return mutable.toImmutable();
					}
					bestD = d;
					best = mutable.toImmutable();
				}
			}
		}
		return best;
	}

	/**
	 * Whether the block state may be tunnelled through. Rejects unbreakable blocks (bedrock,
	 * barrier) and fluids, and additionally protects anything with a block entity — chests,
	 * shulker boxes, spawners, furnaces and the like — which the mould would otherwise silently
	 * destroy along with their contents while digging.
	 */
	private boolean isBreakable(BlockState state, BlockPos pos) {
		return state.getHardness(this.getEntityWorld(), pos) >= 0.0f
				&& state.getFluidState().isEmpty()
				&& !state.hasBlockEntity();
	}

	/** True if the block at {@code pos} is currently one of the target ore blocks. */
	private boolean isTargetOre(BlockPos pos) {
		if (this.oreType == null) {
			return false;
		}
		return this.oreType.isOre(this.getEntityWorld().getBlockState(pos));
	}

	/** Breaks an ore block and collects its drops into the mould's haul. */
	private void breakOre(BlockPos pos) {
		World world = this.getEntityWorld();
		BlockState state = world.getBlockState(pos);
		world.breakBlock(pos, false, this);
		if (world instanceof ServerWorld serverWorld) {
			for (ItemStack drop : Block.getDroppedStacks(state, serverWorld, pos, null)) {
				this.addCollected(drop);
			}
		}
		// Progress was made, so previously unreachable ores may now be reachable through the hole
		// we just opened. Give them all another chance.
		this.skippedOres.clear();
		// Play the mining swing animation once, then pause to scan before picking the next ore.
		this.mineAnimTimer = 6;
		this.dataTracker.set(MINE_DATA, (byte) 1);
		this.searchCooldown = SCAN_PAUSE_TICKS;
	}

	/** Breaks a non-ore block while tunnelling; its drops are intentionally discarded. */
	private void breakBlockNoDrops(BlockPos pos) {
		this.getEntityWorld().breakBlock(pos, false, this);
	}

	/** Adds an ore drop to the haul if it matches the current result item. */
	private void addCollected(ItemStack stack) {
		if (this.oreType != null && stack.getItem() == this.oreType.result()) {
			this.collectedCount += stack.getCount();
		}
	}

	/** Teleports home to the owner, delivers the collected items, then returns to idle. */
	private void returnToOwner() {
		PlayerEntity owner = getOwnerPlayer();
		if (owner == null) {
			this.mode = Mode.IDLE;
			return;
		}
		World world = this.getEntityWorld();
		double x = owner.getX() + (world.random.nextDouble() - 0.5) * 3.0;
		double z = owner.getZ() + (world.random.nextDouble() - 0.5) * 3.0;
		double y = owner.getY();
		try {
			y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
					(int) Math.floor(x), (int) Math.floor(z));
		} catch (Exception ignored) {
			// fall back to the owner's Y
		}
		this.refreshPositionAndAngles(x, y, z, this.getYaw(), this.getPitch());
		this.getNavigation().stop();

		// Play the teleport animation once.
		this.teleportAnimTimer = 15;
		this.dataTracker.set(TELEPORT_DATA, (byte) 1);

		// Deposit the haul into the owner's inventory (drop any overflow).
		//
		// The haul is split into properly-sized stacks rather than being handed over as one
		// oversized ItemStack. Ores that drop more than one item per block (copper yields 2-5 raw
		// copper, redstone 4-5, lapis 4-9) routinely push collectedCount past STACK_SIZE, and a
		// single ItemStack built with a count above the item's max would be silently clamped,
		// quietly destroying the excess.
		int amount = Math.max(0, this.collectedCount);
		if (amount > 0) {
			int remaining = amount;
			while (remaining > 0) {
				ItemStack result = new ItemStack(this.oreType.result());
				int give = Math.min(remaining, result.getMaxCount());
				result.setCount(give);
				remaining -= give;
				if (owner instanceof ServerPlayerEntity serverPlayer
						&& serverPlayer.getInventory().insertStack(result)) {
					continue;
				}
				this.dropStack((ServerWorld) world, result);
			}
			this.notifyOwner("Delivered " + amount + " " + this.oreType.displayName() + "!");
		}
		this.oreType = null;
		this.targetOre = null;
		this.collectedCount = 0;
		this.mode = Mode.IDLE;
		this.dataTracker.set(MODE_DATA, (byte) 0);
	}

	private double squaredDistanceToCenter(BlockPos pos) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dy = this.getY() - (pos.getY() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}

	private double squaredDistanceToOre(BlockPos pos) {
		if (this.targetOre == null) {
			return Double.MAX_VALUE;
		}
		double dx = pos.getX() - this.targetOre.getX();
		double dy = pos.getY() - this.targetOre.getY();
		double dz = pos.getZ() - this.targetOre.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	private void notifyOwner(String message) {
		PlayerEntity owner = getOwnerPlayer();
		if (owner != null) {
			owner.sendMessage(Text.literal("Tainted Mould: " + message)
					.formatted(Formatting.DARK_PURPLE), true);
		}
	}

	/** True if this mould was created by the given player. */
	public boolean isOwner(PlayerEntity player) {
		return this.ownerUuid != null && this.ownerUuid.equals(player.getUuid());
	}

	public void setOwnerUuid(java.util.UUID uuid) {
		this.ownerUuid = uuid;
	}

	/** Returns the number of ore items the mould has collected so far. */
	public int getCollectedCount() {
		return this.collectedCount;
	}

	/** Returns the ore type the mould is currently mining, or null if none. */
	public TaintedOreType getOreType() {
		return this.oreType;
	}

	public java.util.UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	/** Returns the online owner player if present, else null. */
	public PlayerEntity getOwnerPlayer() {
		if (this.ownerUuid == null || this.getEntityWorld().isClient()) {
			return null;
		}
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			return serverWorld.getServer().getPlayerManager().getPlayer(this.ownerUuid);
		}
		return null;
	}

	/** On death the mould drops the item used to summon it so it can be re-deployed. */
	@Override
	public void onDeath(net.minecraft.entity.damage.DamageSource source) {
		super.onDeath(source);
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			this.dropStack(serverWorld, new ItemStack(ModItems.TAINTED_MOULD));
		}
	}

	@Override
	public void writeCustomData(net.minecraft.storage.WriteView view) {
		super.writeCustomData(view);
		view.putNullable("TaintedOwner", net.minecraft.util.Uuids.CODEC, ownerUuid);
		view.put("TaintedMode", com.mojang.serialization.Codec.STRING, mode.name());
		if (this.oreType != null) {
			view.put("TaintedOre", com.mojang.serialization.Codec.STRING, this.oreType.name());
		}
		view.put("TaintedCollected", com.mojang.serialization.Codec.INT, this.collectedCount);
		if (this.targetOre != null) {
			view.put("TaintedOrePos", BlockPos.CODEC, this.targetOre);
		}
	}

	@Override
	public void readCustomData(net.minecraft.storage.ReadView view) {
		super.readCustomData(view);
		view.read("TaintedOwner", net.minecraft.util.Uuids.CODEC).ifPresent(uuid -> this.ownerUuid = uuid);
		view.read("TaintedMode", com.mojang.serialization.Codec.STRING)
				.map(Mode::valueOf).ifPresent(m -> this.mode = m);
		this.oreType = view.read("TaintedOre", com.mojang.serialization.Codec.STRING)
				.map(TaintedOreType::fromName).orElse(null);
		this.collectedCount = view.read("TaintedCollected", com.mojang.serialization.Codec.INT).orElse(0);
		this.targetOre = view.read("TaintedOrePos", BlockPos.CODEC).orElse(null);
	}
}
