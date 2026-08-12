package net.infernalpages.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.registry.ModItems;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The Mould of Souls — a soul construct guardian that attacks any intruder that isn't its owner.
 *
 * <p>It is a hostile, melee automaton: strong and durable, it targets and chases anything that
 * comes near except its owner (the player who created it). It can hold a single {@link
 * GuardAbility}, granted by the owner feeding it an item, and can be set to one of three {@link
 * GuardMode}s by the owner.
 */
public class MouldOfSoulsEntity extends HostileEntity implements GeoEntity {
	/** Enemies in ACTIVE mode are only engaged within this many blocks. */
	public static final double ACTIVE_RANGE = 10.0;

	private static final TrackedData<String> ABILITY_DATA = DataTracker.registerData(MouldOfSoulsEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Byte> MODE_DATA = DataTracker.registerData(MouldOfSoulsEntity.class, TrackedDataHandlerRegistry.BYTE);
	private static final TrackedData<Byte> DORMANT_DATA = DataTracker.registerData(MouldOfSoulsEntity.class, TrackedDataHandlerRegistry.BYTE);

	private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

	public MouldOfSoulsEntity(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ABILITY_DATA, GuardAbility.NONE.id());
		builder.add(MODE_DATA, (byte) GuardMode.HUNT.ordinal());
		builder.add(DORMANT_DATA, (byte) 0);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.MAX_HEALTH, 60.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.32)
				.add(EntityAttributes.ATTACK_DAMAGE, 8.0)
				.add(EntityAttributes.ARMOR, 6.0)
				.add(EntityAttributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		// High speed so it sprints at enemies while using the running animation.
		this.goalSelector.add(0, new MeleeAttackGoal(this, 1.7, true));
		this.goalSelector.add(1, new MouldAbilityGoal(this));
		this.goalSelector.add(1, new MouldTeleportGoal(this));
		this.goalSelector.add(2, new MouldFlightGoal(this));
		this.goalSelector.add(3, new MouldReturnHomeGoal(this));
		this.goalSelector.add(4, new MouldReviveGoal(this));
		this.goalSelector.add(5, new MouldFollowOwnerGoal(this));
		this.goalSelector.add(6, new MouldWanderGoal(this));
		this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(0, new RevengeGoal(this));
		// Attacks any living entity that isn't the owner and isn't an allied mould (same owner).
		this.targetSelector.add(1, new ActiveTargetGoal<LivingEntity>(
				this, LivingEntity.class, 16, true, false,
				(target, world) -> isEnemyOf(target) && isWithinActiveRange(target)));
	}

	/**
	 * Whether the given living entity is an enemy of this mould. An entity is not an enemy if it is
	 * the owner, a creative/spectator player, a passive mob, or another mould owned by the same
	 * player (moulds don't attack their allies, but they do view other players' moulds as enemies).
	 */
	public boolean isEnemyOf(LivingEntity target) {
		if (target instanceof PlayerEntity p) {
			// Never attack the owner, creative players, or spectators.
			return !isOwner(p) && !p.isCreative() && !p.isSpectator();
		}
		if (target instanceof MouldOfSoulsEntity otherMould && this.isSameOwner(otherMould)) {
			return false;
		}
		if (isPassiveMob(target)) {
			return false;
		}
		return true;
	}

	/** True if the given entity is a passive/friendly mob (animals, villagers, fish, bats, etc.). */
	private boolean isPassiveMob(LivingEntity target) {
		if (target instanceof net.minecraft.entity.passive.PassiveEntity) {
			return true;
		}
		net.minecraft.entity.SpawnGroup group = target.getType().getSpawnGroup();
		return group == net.minecraft.entity.SpawnGroup.CREATURE
				|| group == net.minecraft.entity.SpawnGroup.AMBIENT
				|| group == net.minecraft.entity.SpawnGroup.AXOLOTLS
				|| group == net.minecraft.entity.SpawnGroup.UNDERGROUND_WATER_CREATURE
				|| group == net.minecraft.entity.SpawnGroup.WATER_CREATURE
				|| group == net.minecraft.entity.SpawnGroup.WATER_AMBIENT;
	}

	/** True if the given mould shares this mould's owner. */
	private boolean isSameOwner(MouldOfSoulsEntity other) {
		return ownerUuid != null && ownerUuid.equals(other.getOwnerUuid());
	}

	/** Whether the mould may engage the given target in its current mode (ACTIVE is range-limited). */
	private boolean isWithinActiveRange(LivingEntity target) {
		if (getMode() != GuardMode.ACTIVE) {
			return true;
		}
		return this.squaredDistanceTo(target) <= ACTIVE_RANGE * ACTIVE_RANGE;
	}

	@Override
	public boolean canTarget(net.minecraft.entity.LivingEntity target) {
		// In ACTIVE mode, only engage a target while at its post (woken by the dormant scan) or
		// already in combat. While it is returning home after clearing enemies, it must not pick up
		// new targets, or the 32-block follow range would pull it off course forever.
		if (getMode() == GuardMode.ACTIVE && getTarget() == null && !isAtHome()) {
			return false;
		}
		return !isDormant() && super.canTarget(target) && isEnemyOf(target);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MouldOfSoulsEntity>("movement", 3, state -> {
			// Use the server-synced dormant flag so the static pose matches the server's state.
			if (isDormantSynced() || isDormant()) {
				// Fully inert: play the static idle once and hold (never repeats).
				state.setAnimation(RawAnimation.begin().thenPlay("static"));
				return PlayState.CONTINUE;
			}
			if (this.getTarget() != null && state.isMoving()) {
				// Chasing an enemy: sprint with the running animation.
				state.setAnimation(RawAnimation.begin().thenLoop("running"));
				return PlayState.CONTINUE;
			}
			if (state.isMoving()) {
				// Following the owner / wandering: walk.
				state.setAnimation(RawAnimation.begin().thenLoop("walk"));
				return PlayState.CONTINUE;
			}
			// Idle while awake (e.g. hunt) — never plays the static animation; freeze on the
			// last applied pose.
			return PlayState.STOP;
		}));
		// Plays the one-shot "ability" animation while an attack is being unleashed.
		controllers.add(new AnimationController<MouldOfSoulsEntity>("ability", 0, state -> {
			if (this.abilityAnimTimer > 0) {
				state.setAnimation(RawAnimation.begin().thenPlay("ability"));
				return PlayState.CONTINUE;
			}
			return PlayState.STOP;
		}));
		// Plays the one-shot "punch" animation when the mould lands a melee attack.
		controllers.add(new AnimationController<MouldOfSoulsEntity>("punch", 0, state -> {
			if (this.punchAnimTimer > 0) {
				state.setAnimation(RawAnimation.begin().thenPlay("punch"));
				return PlayState.CONTINUE;
			}
			return PlayState.STOP;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	/** The owner can right-click the mould to cycle its mode, or feed/remove an ability item. */
	@Override
	public net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand) {
		if (this.getEntityWorld().isClient()) {
			return net.minecraft.util.ActionResult.SUCCESS;
		}
		if (!isOwner(player)) {
			return net.minecraft.util.ActionResult.PASS;
		}
		net.minecraft.item.ItemStack stack = player.getStackInHand(hand);

		// Feeding an item grants/overrides the ability (right-click with an ability item).
		GuardAbility ability = GuardAbility.fromItem(stack.getItem());
		if (ability != GuardAbility.NONE) {
			this.setAbility(ability);
			stack.decrement(1);
			// Show above the hot bar (action bar) rather than in chat.
			player.sendMessage(net.minecraft.text.Text.literal("Mould of Souls gains: " + ability.id())
					.formatted(net.minecraft.util.Formatting.DARK_PURPLE), true);
			return net.minecraft.util.ActionResult.SUCCESS_SERVER;
		}

		// Otherwise (empty hand) cycle the mode: passive -> active -> hunt -> passive...
		// Note: ability removal is done via shift-punch, so empty-hand right-click is purely modes.
		GuardMode[] modes = GuardMode.values();
		GuardMode next = modes[(getMode().ordinal() + 1) % modes.length];
		this.setMode(next);
		// When switched to ACTIVE, the guard's current position becomes its home post.
		if (next == GuardMode.ACTIVE) {
			this.homePos = this.getBlockPos();
		}
		this.setTarget(null);
		this.navigation.stop();
		// Show the new mode above the hot bar (action bar) rather than in chat.
		player.sendMessage(net.minecraft.text.Text.literal("Mould of Souls: " + next.id())
				.formatted(net.minecraft.util.Formatting.DARK_PURPLE), true);
		return net.minecraft.util.ActionResult.SUCCESS_SERVER;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.abilityAnimTimer > 0) {
			this.abilityAnimTimer--;
		}
		if (this.punchAnimTimer > 0) {
			this.punchAnimTimer--;
		}
		// Remember the original position (used by ACTIVE mode to return home).
		if (this.homePos == null) {
			this.homePos = this.getBlockPos();
		}

		// ACTIVE-mode state machine. The behaviour is deliberately simple and deterministic:
		//   at home + no enemy  -> dormant (static)
		//   enemy enters range  -> wake, run to and kill the enemy
		//   enemy dies          -> TELEPORT straight back home, then go dormant again
		if (getMode() == GuardMode.ACTIVE) {
			LivingEntity target = getTarget();

			if (target != null) {
				// The target is no longer valid once it dies (or leaves range). That counts as a
				// completed engagement, so we teleport home and deactivate.
				boolean valid = target.isAlive() && !target.isRemoved() && isEnemyOf(target)
						&& isWithinActiveRange(target);
				if (!valid) {
					this.setTarget(null);
					// If we were away from home fighting, jump straight back to the post.
					if (!isAtHome() && this.homePos != null) {
						teleportHome();
					}
				}
			} else if (isAtHome()) {
				// Dormant at home: only wake if an enemy enters range.
				scanForEnemies();
			}
		}

		boolean dormant = isDormant();
		// Sync the dormant state to the client so its animation matches the server's decision.
		if ((this.dataTracker.get(DORMANT_DATA) == 1) != dormant) {
			this.dataTracker.set(DORMANT_DATA, (byte) (dormant ? 1 : 0));
		}

		// PASSIVE (and dormant ACTIVE) disable the AI entirely: the mould stands still and looks at nothing.
		this.setAiDisabled(dormant);
		if (dormant) {
			this.setVelocity(0, 0, 0);
			this.setTarget(null);
			this.navigation.stop();
		}
		// Dragon flight lifts the mould off the ground; other abilities keep it grounded.
		boolean fly = !dormant && getAbility() == GuardAbility.DRAGON;
		if (this.hasNoGravity() != fly) {
			this.setNoGravity(fly);
		}
		ensureStrengthApplied();

		// Diagnostic logging for the ACTIVE-mode state machine (throttled to ~1/sec).
		if (getMode() == GuardMode.ACTIVE && --this.logCooldown <= 0) {
			this.logCooldown = 20;
			InfernalPagesMod.LOGGER.info("[Mould ACTIVE] pos=({},{},{}), home=({},{},{}), target={}, dormant={}, navIdle={}",
					(int) this.getX(), (int) this.getY(), (int) this.getZ(),
					this.homePos == null ? -1 : this.homePos.getX(),
					this.homePos == null ? -1 : this.homePos.getY(),
					this.homePos == null ? -1 : this.homePos.getZ(),
					this.getTarget() == null ? "none" : this.getTarget().getType().toString(),
					dormant,
					this.getNavigation().isIdle());
		}
	}

	/** Returns true if this mould was created by the given player (owner check). */
	public boolean isOwner(PlayerEntity player) {
		return ownerUuid != null && ownerUuid.equals(player.getUuid());
	}

	public GuardAbility getAbility() {
		return GuardAbility.fromId(this.dataTracker.get(ABILITY_DATA));
	}

	public void setAbility(GuardAbility ability) {
		// Remove the old ability's effects before switching.
		removeStrengthModifiers();
		if (getAbility() == GuardAbility.DRAGON) {
			this.setNoGravity(false);
		}
		this.dataTracker.set(ABILITY_DATA, ability.id());
		if (ability == GuardAbility.STRENGTH) {
			applyStrengthModifiers();
		}
	}

	public GuardMode getMode() {
		return GuardMode.values()[this.dataTracker.get(MODE_DATA)];
	}

	public void setMode(GuardMode mode) {
		this.dataTracker.set(MODE_DATA, (byte) mode.ordinal());
	}

	/** Convenience: true when the mould is not passive (its AI is active). */
	public boolean isActiveAI() {
		return !isDormant();
	}

	/** Synced dormant flag for the client (so the animation matches the server's decision). */
	public boolean isDormantSynced() {
		return this.dataTracker.get(DORMANT_DATA) == 1;
	}

	/**
	 * True when the mould's AI should be fully disabled. This happens in PASSIVE mode, and in ACTIVE
	 * mode whenever there is no enemy to fight and the mould is already back at its home position.
	 * While an ACTIVE mould is away from home with no target, it is NOT dormant so it can walk home
	 * before settling into the frozen/idle state.
	 */
	public boolean isDormant() {
		if (getMode() == GuardMode.PASSIVE) {
			return true;
		}
		return getMode() == GuardMode.ACTIVE && getTarget() == null && isAtHome();
	}

	/** True if the mould is currently within its home-post radius (or has no home recorded). */
	public boolean isAtHome() {
		BlockPos home = this.homePos;
		if (home == null) {
			return true;
		}
		// Match by horizontal position (Y may differ slightly after landing), within ~2 blocks.
		double dx = this.getX() - (home.getX() + 0.5);
		double dz = this.getZ() - (home.getZ() + 0.5);
		return dx * dx + dz * dz <= 2.0 * 2.0;
	}

	/** Teleports the mould straight back to its home position (used after an ACTIVE-mode kill). */
	private void teleportHome() {
		if (this.homePos == null) {
			return;
		}
		double hx = this.homePos.getX() + 0.5;
		double hz = this.homePos.getZ() + 0.5;
		// Land on the topmost solid block at the home column so the mould doesn't sink into the ground.
		double hy = this.getEntityWorld().getTopY(
				net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,
				this.homePos.getX(), this.homePos.getZ());
		this.refreshPositionAfterTeleport(hx, hy, hz);
		this.getNavigation().stop();
	}

	/** In ACTIVE mode, scan for any nearby enemy within range to wake the guard up. */
	private void scanForEnemies() {
		if (getMode() != GuardMode.ACTIVE || getTarget() != null) {
			return;
		}
		net.minecraft.util.math.Box box = this.getBoundingBox().expand(ACTIVE_RANGE);
		LivingEntity best = null;
		double bestDist = ACTIVE_RANGE * ACTIVE_RANGE;
		for (net.minecraft.entity.Entity e : this.getEntityWorld().getOtherEntities(this, box, entity -> entity.isAlive())) {
			if (!(e instanceof LivingEntity living)) {
				continue;
			}
			if (!isEnemyOf(living)) {
				continue;
			}
			double d = this.squaredDistanceTo(living);
			if (d <= bestDist) {
				bestDist = d;
				best = living;
			}
		}
		if (best != null) {
			this.setTarget(best);
		}
	}

	/** The position the mould was created at, to which it returns in ACTIVE mode. */
	public BlockPos getHomePos() {
		return this.homePos;
	}

	public void setHomePos(BlockPos pos) {
		this.homePos = pos;
	}

	/** Flags the one-shot ability animation for a short time so it plays once. */
	public void triggerAbilityAnimation() {
		this.abilityAnimTimer = 15;
	}

	/** Buffs the mould's movement through water. Vanilla is 0.8 (higher = slower); lowering it makes
	 *  the mould swim noticeably faster than most mobs. */
	@Override
	protected float getBaseWaterMovementSpeedMultiplier() {
		return 0.5f;
	}

	/** Flags the one-shot punch animation (used when the mould lands a melee attack). */
	public void triggerPunchAnimation() {
		this.punchAnimTimer = 10;
	}

	/** Trigger the punch animation whenever a melee attack connects. */
	@Override
	public boolean tryAttack(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.Entity target) {
		boolean attacked = super.tryAttack(world, target);
		if (attacked) {
			this.triggerPunchAnimation();
		}
		return attacked;
	}

	private static final Identifier STR_DAMAGE_ID = Identifier.of(InfernalPagesMod.MOD_ID, "mould_str_damage");
	private static final Identifier STR_ATTACK_SPEED_ID = Identifier.of(InfernalPagesMod.MOD_ID, "mould_str_attack_speed");
	private static final Identifier STR_MOVE_ID = Identifier.of(InfernalPagesMod.MOD_ID, "mould_str_move");

	/** Applies the dirt/strength buff: more damage, faster attacks and movement. */
	private void applyStrengthModifiers() {
		applyModifier(EntityAttributes.ATTACK_DAMAGE, STR_DAMAGE_ID, 6.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		applyModifier(EntityAttributes.ATTACK_SPEED, STR_ATTACK_SPEED_ID, 1.0,
				EntityAttributeModifier.Operation.ADD_VALUE);
		applyModifier(EntityAttributes.MOVEMENT_SPEED, STR_MOVE_ID, 0.12,
				EntityAttributeModifier.Operation.ADD_VALUE);
	}

	private void applyModifier(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
			Identifier id, double amount, EntityAttributeModifier.Operation op) {
		// Attribute instances are null before the entity is fully initialised (e.g. during NBT load).
		net.minecraft.entity.attribute.EntityAttributeInstance instance = this.getAttributeInstance(attribute);
		if (instance != null && instance.getModifier(id) == null) {
			instance.addPersistentModifier(new EntityAttributeModifier(id, amount, op));
		}
	}

	private void removeStrengthModifiers() {
		removeModifier(EntityAttributes.ATTACK_DAMAGE, STR_DAMAGE_ID);
		removeModifier(EntityAttributes.ATTACK_SPEED, STR_ATTACK_SPEED_ID);
		removeModifier(EntityAttributes.MOVEMENT_SPEED, STR_MOVE_ID);
	}

	private void removeModifier(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute, Identifier id) {
		net.minecraft.entity.attribute.EntityAttributeInstance instance = this.getAttributeInstance(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	/** Re-applies the strength buff after NBT load (attributes may not have been ready during readCustomData). */
	private void ensureStrengthApplied() {
		if (getAbility() == GuardAbility.STRENGTH) {
			applyStrengthModifiers();
		}
	}

	/** Returns the owner player if they're online on this server, otherwise null. */
	public PlayerEntity getOwnerPlayer() {
		if (ownerUuid == null || this.getEntityWorld().isClient()) {
			return null;
		}
		if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			return sw.getServer().getPlayerManager().getPlayer(ownerUuid);
		}
		return null;
	}

	/**
	 * On death the mould drops the item used to summon it, plus (if an ability was equipped) the item
	 * that granted that ability, so it can be reclaimed and re-fed.
	 */
	@Override
	public void onDeath(net.minecraft.entity.damage.DamageSource source) {
		super.onDeath(source);
		if (this.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
			this.dropStack(sw, new net.minecraft.item.ItemStack(ModItems.MOULD_OF_SOULS));
			GuardAbility ability = getAbility();
			if (ability != GuardAbility.NONE && ability.item() != null) {
				this.dropStack(sw, new net.minecraft.item.ItemStack(ability.item()));
			}
		}
	}

	// Owner and home are stored as simple persistent fields, saved in the entity's NBT.
	private java.util.UUID ownerUuid;
	private BlockPos homePos;
	private int abilityAnimTimer;
	private int punchAnimTimer;
	private int logCooldown;

	public void setOwnerUuid(java.util.UUID uuid) {
		this.ownerUuid = uuid;
	}

	public java.util.UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	@Override
	public void writeCustomData(net.minecraft.storage.WriteView view) {
		super.writeCustomData(view);
		view.putNullable("MouldOwner", net.minecraft.util.Uuids.CODEC, ownerUuid);
		view.put("Mode", com.mojang.serialization.Codec.STRING, getMode().id());
		view.put("Ability", com.mojang.serialization.Codec.STRING, getAbility().id());
		if (this.homePos != null) {
			view.put("HomePos", BlockPos.CODEC, this.homePos);
		}
	}

	@Override
	public void readCustomData(net.minecraft.storage.ReadView view) {
		super.readCustomData(view);
		view.read("MouldOwner", net.minecraft.util.Uuids.CODEC).ifPresent(uuid -> this.ownerUuid = uuid);
		this.setMode(view.read("Mode", com.mojang.serialization.Codec.STRING)
				.map(GuardMode::fromId).orElse(GuardMode.HUNT));
		this.setAbility(view.read("Ability", com.mojang.serialization.Codec.STRING)
				.map(GuardAbility::fromId).orElse(GuardAbility.NONE));
		this.homePos = view.read("HomePos", BlockPos.CODEC).orElse(null);
	}
}
