package net.infernalpages.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.infernalpages.item.Sharpening;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Modifies incoming damage based on the attacker's weapon sharpening. Handles the conditional
 * sharpening effects that cannot be expressed as static attribute modifiers:
 * <ul>
 *   <li>{@code CLOSE} — 1.5× damage when the target is within 1 block.</li>
 *   <li>{@code SPEED} — +1 damage per block/s the attacker is moving.</li>
 *   <li>{@code BLUNT} — the weapon deals no damage.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class SharpeningDamageMixin {
	@ModifyVariable(
			method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0)
	private float infernalpages$modifySharpeningDamage(float amount, ServerWorld world, DamageSource source) {
		// Only players with a sharpened main-hand weapon are affected.
		if (!(source.getAttacker() instanceof PlayerEntity player)) {
			return amount;
		}
		ItemStack stack = player.getMainHandStack();
		switch (Sharpening.fromStack(stack)) {
			case CLOSE -> {
				// The victim is `this`; double-check the player is within 1 block of the target.
				if (player.squaredDistanceTo((LivingEntity) (Object) this) <= 1.0 * 1.0) {
					amount *= 1.5f;
				}
			}
			case SPEED -> {
				// +1 damage per block/s the player is moving (getVelocity is blocks/tick).
				amount += player.getVelocity().horizontalLength() * 20.0f;
			}
			case BLUNT -> amount = 0.0f;
			default -> { }
		}
		return amount;
	}
}
