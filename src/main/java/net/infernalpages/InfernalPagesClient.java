package net.infernalpages;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.entity.EntityRendererFactories;
import net.infernalpages.client.MouldOfSoulsRenderer;
import net.infernalpages.registry.ModEntities;

/**
 * Client-side initializer: registers entity renderers.
 */
public class InfernalPagesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererFactories.register(ModEntities.MOULD_OF_SOULS, MouldOfSoulsRenderer::new);
	}
}
