package net.infernalpages;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.entity.EntityRendererFactories;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.infernalpages.client.MouldOfSoulsRenderer;
import net.infernalpages.client.TaintedMouldRenderer;
import net.infernalpages.item.Sharpening;
import net.infernalpages.registry.ModEntities;

/**
 * Client-side initializer: registers entity renderers and tooltip callbacks.
 */
public class InfernalPagesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererFactories.register(ModEntities.MOULD_OF_SOULS, MouldOfSoulsRenderer::new);
		EntityRendererFactories.register(ModEntities.TAINTED_MOULD, TaintedMouldRenderer::new);

		// Show the applied sharpening effect on any sharpened weapon's tooltip.
		net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
				(ItemStack stack, net.minecraft.item.Item.TooltipContext context,
				 net.minecraft.item.tooltip.TooltipType type, java.util.List<Text> lines) -> {
					Sharpening sharpening = Sharpening.fromStack(stack);
					if (sharpening != Sharpening.NONE) {
						lines.add(Text.literal("Sharpened: " + sharpening.displayName())
								.formatted(Formatting.LIGHT_PURPLE));
					}
					// Show a note on reinforced armour.
					if (Boolean.TRUE.equals(stack.get(net.infernalpages.registry.ModComponents.TAINTED))) {
						lines.add(Text.literal("Reinforced with a Tainted Shard")
								.formatted(Formatting.DARK_PURPLE));
						lines.add(Text.literal("Blocks an incoming hit, then recharges")
								.formatted(Formatting.DARK_GRAY));
						lines.add(Text.literal("15s cooldown, -3s per extra reinforced piece")
								.formatted(Formatting.DARK_GRAY));
					}
				});
	}
}
