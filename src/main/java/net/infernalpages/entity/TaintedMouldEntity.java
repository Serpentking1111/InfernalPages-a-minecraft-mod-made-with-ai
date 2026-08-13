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
 * and it will travel to the
 * nearest matching ore, mine it (preferring exposed ores and tunnelling through blocks to reach
 * buried ones), and collect only the ore's drops. Once it holds a full stack it teleports back to
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
	/** The block-reach distance (blocks) at which the mould can break a block. */
	private static final double REACH = 3.2;
	/** Ticks the mould stands still scanning (playing the "scan" animation) after each ore break. */
	private static final int SCAN_PAUSE_TICKS = 30;

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
				this.notifyOwner("No " + this.oreType.displayName() + " found within "
						+ SEARCH_RADIUS + " blocks.");
				this.mode = Mode.IDLE;
				this.dataTracker.set(MODE_DATA, (byte) 0);
				return;
			}
		}

		// If the ore is in reach, break it and collect its drops.
		if (canReach(this.targetOre)) {
			this.breakOre(this.targetOre);
			this.targetOre = null;
			return;
		}

		// Ores generate in veins, so the blocks between us and the target are very often more of
		// the same ore. Mine those properly (collecting their drops) instead of treating them as
		// obstacles — findBlocker deliberately refuses to touch them, which previously left the
		// mould tunnelling around its own vein and never picking up the ore it was set to.
		BlockPos adjacentOre = findAdjacentTargetOre();
		if (adjacentOre != null) {
			this.breakOre(adjacentOre);
			if (adjacentOre.equals(this.targetOre)) {
				this.targetOre = null;
			}
			return;
		}

		// Otherwise, if a solid block is in reach and lies between us and the ore, tunnel through it.
		BlockPos blocker = findBlocker();
		if (blocker != null) {
			breakBlockNoDrops(blocker);
			return;
		}

		// Otherwise walk toward the ore.
		this.getNavigation().startMovingTo(
				this.targetOre.getX() + 0.5, this.targetOre.getY(), this.targetOre.getZ() + 0.5, 1.0);
	}

	/** Picks the closest matching ore, regardless of whether it is exposed. */
	private void reselectOre() {
		this.targetOre = findNearestOre();
	}

	/** Scans a box around the mould for the closest matching ore. */
	private BlockPos findNearestOre() {
		if (this.oreType == null) {
			return null;
		}
		World world = this.getEntityWorld();
		BlockPos base = this.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				for (int dy = -SEARCH_HEIGHT; dy <= SEARCH_HEIGHT; dy++) {
					mutable.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					BlockState state = world.getBlockState(mutable);
					if (!this.oreType.isOre(state)) {
						continue;
					}
					double d = squaredDistanceToCenter(mutable);
					if (d < bestD) {
						bestD = d;
						best = mutable.toImmutable();
					}
				}
			}
		}
		return best;
	}

	/** True if the mould can break the block at {@code pos} from its current position. */
	private boolean canReach(BlockPos pos) {
		double dx = this.getX() - (pos.getX() + 0.5);
		double dy = (this.getY() + 0.5) - (pos.getY() + 0.5);
		double dz = this.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= REACH * REACH;
	}

	/**
	 * Finds a target-ore block within reach, nearest to the current ore target. Used so the mould
	 * harvests the rest of a vein it is standing in rather than tunnelling past it.
	 */
	private BlockPos findAdjacentTargetOre() {
		if (this.oreType == null) {
			return null;
		}
		World world = this.getEntityWorld();
		BlockPos mouldPos = this.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					mutable.set(mouldPos.getX() + dx, mouldPos.getY() + dy, mouldPos.getZ() + dz);
					if (!canReach(mutable) || !this.oreType.isOre(world.getBlockState(mutable))) {
						continue;
					}
					double d = squaredDistanceToOre(mutable);
					if (d < bestD) {
						bestD = d;
						best = mutable.toImmutable();
					}
				}
			}
		}
		return best;
	}

	/** Finds a solid, breakable, non-ore block within reach that is nearest the current ore target. */
	private BlockPos findBlocker() {
		if (this.targetOre == null) {
			return null;
		}
		World world = this.getEntityWorld();
		BlockPos mouldPos = this.getBlockPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					mutable.set(mouldPos.getX() + dx, mouldPos.getY() + dy, mouldPos.getZ() + dz);
					if (!canReach(mutable)) {
						continue;
					}
					BlockState state = world.getBlockState(mutable);
					if (state.isAir() || !isBreakable(state, mutable) || (this.oreType != null && this.oreType.isOre(state))) {
						continue;
					}
					double d = squaredDistanceToOre(mutable);
					if (d < bestD) {
						bestD = d;
						best = mutable.toImmutable();
					}
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
		int amount = Math.max(0, this.collectedCount);
		if (amount > 0) {
			ItemStack result = new ItemStack(this.oreType.result(), amount);
			if (owner instanceof ServerPlayerEntity serverPlayer) {
				if (!serverPlayer.getInventory().insertStack(result)) {
					this.dropStack((ServerWorld) world, result);
				}
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
