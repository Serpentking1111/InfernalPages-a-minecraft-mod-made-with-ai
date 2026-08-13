package net.infernalpages.item;

import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * The Contract Sword — a powerful blade bound to its owner (B).
 *
 * <p>It is stronger than netherite (higher attack damage, slightly slower attack speed) and highly
 * enchantable. It works like The Scripture: killing a player permanently banishes them with a
 * lightning bolt (no large explosion), and it is <b>reusable</b>. It can never harm the other
 * signer (A), and its effect only works for its owner.
 *
 * <p>The kill behaviour is implemented in {@link net.infernalpages.death.KillHandler}; ownership and
 * the forbidden target are stored in {@link net.infernalpages.registry.ModComponents#SWORD_OWNER}
 * and {@link net.infernalpages.registry.ModComponents#SWORD_FORBIDDEN}.
 */
public class ContractSwordItem extends Item {
	public ContractSwordItem(Settings settings) {
		super(settings);
	}

	/** Renders the sword's name in gold (or dark gray if broken). */
	@Override
	public Text getName(ItemStack stack) {
		if (Boolean.TRUE.equals(stack.get(net.infernalpages.registry.ModComponents.CONTRACT_BROKEN))) {
			return Text.translatable(this.getTranslationKey()).formatted(Formatting.DARK_GRAY);
		}
		return Text.translatable(this.getTranslationKey()).formatted(Formatting.GOLD);
	}

	/**
	 * Smash mechanic: a <b>broken</b> Contract Sword can be right-clicked on a block tougher than
	 * stone to shatter it into seven Tainted Shards (the raw material for "The remains of a tainted
	 * past"). A healthy sword is not smashable.
	 */
	@Override
	public net.minecraft.util.ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
		if (context.getWorld().isClient()) {
			return net.minecraft.util.ActionResult.SUCCESS;
		}
		ItemStack stack = context.getStack();
		// Only a broken sword can be smashed.
		if (!Boolean.TRUE.equals(stack.get(net.infernalpages.registry.ModComponents.CONTRACT_BROKEN))
				&& !net.infernalpages.InfernalPagesMod.BROKEN.isBroken(stack.get(net.infernalpages.registry.ModComponents.CONTRACT_ID))) {
			return net.minecraft.util.ActionResult.PASS;
		}
		net.minecraft.block.Block block = context.getWorld().getBlockState(context.getBlockPos()).getBlock();
		if (block.getBlastResistance() <= net.minecraft.block.Blocks.STONE.getBlastResistance()) {
			return net.minecraft.util.ActionResult.PASS;
		}
		if (context.getPlayer() instanceof net.minecraft.server.network.ServerPlayerEntity player) {
			stack.decrement(1);
			player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					net.minecraft.sound.SoundEvents.BLOCK_GLASS_BREAK,
					net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
			for (int i = 0; i < 7; i++) {
				player.giveItemStack(new net.minecraft.item.ItemStack(net.infernalpages.registry.ModItems.TAINTED_SHARD));
			}
			player.sendMessage(Text.literal("The broken sword shatters into seven Tainted Shards.")
					.formatted(Formatting.DARK_PURPLE), false);
			return net.minecraft.util.ActionResult.SUCCESS_SERVER;
		}
		return net.minecraft.util.ActionResult.PASS;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType type) {
		String owner = stack.get(net.infernalpages.registry.ModComponents.SWORD_OWNER_NAME);
		if (owner != null) {
			tooltip.accept(Text.literal("Bound to: " + owner).formatted(Formatting.GOLD));
		} else {
			tooltip.accept(Text.translatable("item.infernalpages.contract_sword.tooltip").formatted(Formatting.DARK_RED));
		}
		tooltip.accept(Text.translatable("item.infernalpages.contract_sword.tooltip2").formatted(Formatting.GRAY));
	}

	/**
	 * Builds the sword's attribute modifiers: attack damage higher than netherite, and a slightly
	 * reduced attack speed.
	 */
	public static AttributeModifiersComponent createAttributes() {
		return AttributeModifiersComponent.builder()
			.add(EntityAttributes.ATTACK_DAMAGE,
					new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, 14.0,
							EntityAttributeModifier.Operation.ADD_VALUE),
					net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
			.add(EntityAttributes.ATTACK_SPEED,
					new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, -2.6,
							EntityAttributeModifier.Operation.ADD_VALUE),
					net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
				.build();
	}
}
