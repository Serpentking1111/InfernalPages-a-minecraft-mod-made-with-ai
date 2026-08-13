package net.infernalpages.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import net.infernalpages.entity.TaintedMouldEntity;

/**
 * Renders the Tainted Mould with GeckoLib using the same (un-animated) body model as the Mould of
 * Souls, scaled up to roughly the same ~1.5-block size.
 */
public class TaintedMouldRenderer extends GeoEntityRenderer<TaintedMouldEntity, TaintedMouldGeoRenderState> {
	public TaintedMouldRenderer(EntityRendererFactory.Context context) {
		super(context, new TaintedMouldGeoModel());
	}

	@Override
	public TaintedMouldGeoRenderState createRenderState(TaintedMouldEntity entity, Void animData) {
		return new TaintedMouldGeoRenderState();
	}

	@Override
	public void captureDefaultRenderState(TaintedMouldEntity entity, Void animData,
			TaintedMouldGeoRenderState state, float partialTick) {
		super.captureDefaultRenderState(entity, animData, state, partialTick);
	}

	/** Scale the ~0.75-block geo model up 2x to ~1.5 blocks, matching the Mould of Souls. */
	@Override
	public void scaleModelForRender(RenderPassInfo<TaintedMouldGeoRenderState> renderPassInfo,
			float width, float height) {
		super.scaleModelForRender(renderPassInfo, width * 2.0f, height * 2.0f);
	}
}
