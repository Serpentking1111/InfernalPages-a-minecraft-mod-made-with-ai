package net.infernalpages.client;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.entity.TaintedMouldEntity;

/**
 * GeckoLib model for the Tainted Mould. Uses the custom mining-bot geometry and its own texture,
 * and resolves the animation file so the entity's animation controllers can play its named
 * animations ({@code static}, {@code scan}, {@code run}, {@code mine}, {@code teliport}).
 */
public class TaintedMouldGeoModel extends GeoModel<TaintedMouldEntity> {
	private static final Identifier MODEL_FILE =
			Identifier.of(InfernalPagesMod.MOD_ID, "geckolib/models/taintedmould.geo.json");
	private static final Identifier ANIMATION_FILE =
			Identifier.of(InfernalPagesMod.MOD_ID, "geckolib/animations/taintedmould.animation.json");
	private static final Identifier TEXTURE =
			Identifier.of(InfernalPagesMod.MOD_ID, "textures/entity/tainted_mould.png");

	@Override
	public Identifier getModelResource(GeoRenderState state) {
		return GeckoLibResources.stripPrefixAndSuffix(MODEL_FILE);
	}

	@Override
	public Identifier getTextureResource(GeoRenderState state) {
		return TEXTURE;
	}

	@Override
	public Identifier getAnimationResource(TaintedMouldEntity animatable) {
		return GeckoLibResources.stripPrefixAndSuffix(ANIMATION_FILE);
	}
}
