package net.infernalpages.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;

/**
 * An unsigned Contract. Right-click to sign.
 *
 * <p>The first player to sign is <b>A</b> (the contract's signer). A second, different player who
 * signs is <b>B</b> (the doomed). Signing by B completes the pact and issues the rewards:
 * <ul>
 *   <li><b>A</b> receives an <b>Unholy Charm</b> targeted at B.</li>
 *   <li><b>B</b> receives a <b>Contract Sword</b> bound to B, unable to harm A.</li>
 * </ul>
 * The contract is then consumed.
 */
public class ContractItem extends Item {
	public ContractItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(net.minecraft.item.ItemStack stack,
			net.minecraft.item.Item.TooltipContext context,
			net.minecraft.component.type.TooltipDisplayComponent tooltipDisplay,
			java.util.function.Consumer<net.minecraft.text.Text> tooltip,
			net.minecraft.item.tooltip.TooltipType type) {
		String signer = stack.get(ModComponents.CONTRACT_SIGNER_NAME);
		if (signer != null) {
			tooltip.accept(net.minecraft.text.Text.literal("Contract maker: " + signer)
					.formatted(net.minecraft.util.Formatting.GOLD));
			tooltip.accept(net.minecraft.text.Text.literal("Signed — awaiting the second signer.")
					.formatted(net.minecraft.util.Formatting.GRAY));
		} else {
			tooltip.accept(net.minecraft.text.Text.translatable("item.infernalpages.contract.tooltip")
					.formatted(net.minecraft.util.Formatting.GRAY));
		}
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) user;
		ItemStack stack = user.getStackInHand(hand);

		java.util.UUID signer = stack.get(ModComponents.CONTRACT_SIGNER);

		// First signature: player A.
		if (signer == null) {
			stack.set(ModComponents.CONTRACT_SIGNER, player.getUuid());
			stack.set(ModComponents.CONTRACT_SIGNER_NAME, player.getName().getString());
			// Switch the contract's model to the "signed by A" texture (custom_model_data = 1).
			stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_MODEL_DATA,
					new net.minecraft.component.type.CustomModelDataComponent(
							java.util.List.of(1.0f), java.util.List.of(), java.util.List.of(), java.util.List.of()));
			player.sendMessage(net.minecraft.text.Text.literal("You have signed the contract. "
					+ "Pass it to another player to seal the pact.")
					.formatted(Formatting.RED), false);
			return ActionResult.SUCCESS_SERVER;
		}

		// Same player can't sign twice.
		if (signer.equals(player.getUuid())) {
			player.sendMessage(net.minecraft.text.Text.literal("You have already signed this contract.")
					.formatted(Formatting.GRAY), false);
			return ActionResult.FAIL;
		}

		// Second signature: player B. Seal the pact.
		ServerPlayerEntity playerA = world.getServer().getPlayerManager().getPlayer(signer);
		java.util.UUID uuidB = player.getUuid();
		String nameB = player.getName().getString();
		// One unique id ties together every item of this specific pact (charm + sword).
		java.util.UUID pactId = java.util.UUID.randomUUID();

		if (playerA != null) {
			// A receives the Unholy Charm, bound to A as holder and B as the doomed target.
			ItemStack charm = new ItemStack(ModItems.UNHOLY_CHARM);
			charm.set(ModComponents.CONTRACT_ID, pactId);
			charm.set(ModComponents.UNHOLY_OWNER, signer);
			charm.set(ModComponents.UNHOLY_OWNER_NAME, stack.get(ModComponents.CONTRACT_SIGNER_NAME));
			charm.set(ModComponents.UNHOLY_TARGET, uuidB);
			charm.set(ModComponents.UNHOLY_TARGET_NAME, nameB);
			playerA.giveItemStack(charm);
			playerA.sendMessage(net.minecraft.text.Text.literal("The pact is sealed. "
					+ "An Unholy Charm has been bound to your soul — speak " + nameB + "'s name to end them.")
					.formatted(Formatting.DARK_RED), false);
		} else {
			player.sendMessage(net.minecraft.text.Text.literal(
					"Warning: " + stack.get(ModComponents.CONTRACT_SIGNER_NAME) + " is offline and did not receive their Unholy Charm.")
					.formatted(Formatting.RED), false);
		}

		// B receives the Contract Sword, bound to B, unable to harm A, and tied to this pact.
		ItemStack sword = new ItemStack(ModItems.CONTRACT_SWORD);
		sword.set(ModComponents.CONTRACT_ID, pactId);
		sword.set(ModComponents.SWORD_OWNER, uuidB);
		sword.set(ModComponents.SWORD_OWNER_NAME, nameB);
		sword.set(ModComponents.SWORD_FORBIDDEN, signer);
		player.giveItemStack(sword);
		player.sendMessage(net.minecraft.text.Text.literal("The pact is sealed. "
				+ "The Contract Sword is yours — it can never harm " + stack.get(ModComponents.CONTRACT_SIGNER_NAME) + ".")
				.formatted(Formatting.DARK_RED), false);

		// Consume the contract.
		stack.decrement(1);
		return ActionResult.SUCCESS_SERVER;
	}
}
