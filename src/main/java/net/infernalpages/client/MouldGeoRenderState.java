package net.infernalpages.client;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import net.infernalpages.entity.GuardAbility;

/**
 * Render state for the GeckoLib-rendered Mould of Souls.
 *
 * <p>Note: GeckoLib mixes a private data map into vanilla {@link LivingEntityRenderState} and its
 * default {@code addGeckolibData} writes to that hidden map. Because we need our own render-state
 * subclass here, we override the data-map methods so reads and writes all use our single map,
 * otherwise the animation manager GeckoLib stores during capture is never found during extraction.
 */
public class MouldGeoRenderState extends LivingEntityRenderState implements GeoRenderState {
	public GuardAbility ability = GuardAbility.NONE;

	private final java.util.Map<DataTicket<?>, Object> dataMap = new java.util.HashMap<>();

	@Override
	public java.util.Map<DataTicket<?>, Object> getDataMap() {
		return this.dataMap;
	}

	@Override
	public <D> void addGeckolibData(DataTicket<D> ticket, D data) {
		this.dataMap.put(ticket, data);
	}

	@Override
	public boolean hasGeckolibData(DataTicket<?> ticket) {
		return this.dataMap.containsKey(ticket);
	}
}
