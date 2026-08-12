package net.infernalpages.client;

import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.entity.MouldOfSoulsEntity;

/**
 * GeckoLib model for the Mould of Souls that explicitly points to the model/animation files,
 * avoiding GeckoLib's default path resolution.
 *
 * <p>GeckoLib only scans the {@code geckolib/models} and {@code geckolib/animations} folders (in any
 * namespace), and it caches baked models/animations under a {@link GeckoLibResources#stripPrefixAndSuffix
 * stripped} key (e.g. {@code infernalpages:soulmould}). We therefore point at the full scanned file
 * path and strip it with GeckoLib's own helper so the returned identifier always equals the cache
 * key, regardless of the file name or folder layout.
 */
public class MouldGeoModel extends GeoModel<MouldOfSoulsEntity> {
	private static final Identifier MODEL_FILE = Identifier.of(InfernalPagesMod.MOD_ID, "geckolib/models/soulmould.geo.json");
	private static final Identifier ANIMATION_FILE = Identifier.of(InfernalPagesMod.MOD_ID, "geckolib/animations/soulmould.animation.json");

	@Override
	public Identifier getModelResource(GeoRenderState state) {
		return GeckoLibResources.stripPrefixAndSuffix(MODEL_FILE);
	}

	@Override
	public Identifier getTextureResource(GeoRenderState state) {
		if (state instanceof MouldGeoRenderState mouldState) {
			return mouldState.ability.texture();
		}
		return net.infernalpages.entity.GuardAbility.NONE.texture();
	}

	@Override
	public Identifier getAnimationResource(MouldOfSoulsEntity animatable) {
		return GeckoLibResources.stripPrefixAndSuffix(ANIMATION_FILE);
	}
}
