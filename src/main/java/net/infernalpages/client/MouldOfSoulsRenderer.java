package net.infernalpages.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import net.infernalpages.entity.MouldOfSoulsEntity;

/**
 * Renders the Mould of Souls with GeckoLib using the animated soulmould model, and picks the
 * texture based on the entity's equipped ability.
 */
public class MouldOfSoulsRenderer extends GeoEntityRenderer<MouldOfSoulsEntity, MouldGeoRenderState> {
	public MouldOfSoulsRenderer(EntityRendererFactory.Context context) {
		super(context, new MouldGeoModel());
	}

	@Override
	public MouldGeoRenderState createRenderState(MouldOfSoulsEntity entity, Void animData) {
		return new MouldGeoRenderState();
	}

	@Override
	public void captureDefaultRenderState(MouldOfSoulsEntity entity, Void animData, MouldGeoRenderState state, float partialTick) {
		super.captureDefaultRenderState(entity, animData, state, partialTick);
		state.ability = entity.getAbility();
	}

	/**
	 * The mould's geo model is authored in 16th-block units, so its ~12-unit height renders as
	 * 0.75 blocks. Scale it up 2x to make it roughly 1.5 blocks tall (the intended size).
	 */
	@Override
	public void scaleModelForRender(RenderPassInfo<MouldGeoRenderState> renderPassInfo, float width, float height) {
		super.scaleModelForRender(renderPassInfo, width * 2.0f, height * 2.0f);
	}

	/**
	 * Head-tracking: rotates the "head" bone to follow where the mould is looking. This runs after
	 * the animation is applied, so the head turns toward the target/player on top of its walk cycle
	 * (the walk animation doesn't animate the head).
	 */
	@Override
	public void adjustModelBonesForRender(RenderPassInfo<MouldGeoRenderState> renderPassInfo, BoneSnapshots boneSnapshots) {
		MouldGeoRenderState state = renderPassInfo.renderState();
		float headYaw = state.relativeHeadYaw;
		float headPitch = state.pitch;
		boneSnapshots.get("head").ifPresent(bone -> {
			// BoneSnapshot rotations are in radians; vanilla head rotation is in degrees.
			bone.setRotY((float) Math.toRadians(headYaw));
			bone.setRotX((float) Math.toRadians(headPitch));
		});
	}
}
