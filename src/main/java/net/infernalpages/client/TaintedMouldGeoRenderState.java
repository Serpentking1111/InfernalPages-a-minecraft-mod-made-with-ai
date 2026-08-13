package net.infernalpages.client;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Render state for the GeckoLib-rendered Tainted Mould. Mirrors {@link MouldGeoRenderState}: GeckoLib
 * mixes a private data map into vanilla {@link LivingEntityRenderState}, so we override the
 * data-map methods to use our own single map.
 */
public class TaintedMouldGeoRenderState extends LivingEntityRenderState implements GeoRenderState {
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
