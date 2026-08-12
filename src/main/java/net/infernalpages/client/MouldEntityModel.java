package net.infernalpages.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;

/**
 * A simple box model for the Mould of Souls so it renders as a visible soul construct.
 */
public class MouldEntityModel extends EntityModel<MouldRenderState> {
	public static final EntityModelLayer LAYER =
			new EntityModelLayer(Identifier.of(InfernalPagesMod.MOD_ID, "mould_of_souls"), "main");

	public MouldEntityModel(ModelPart root) {
		super(root);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, Dilation.NONE),
				ModelTransform.origin(0.0f, 24.0f, 0.0f));
		return TexturedModelData.of(data, 64, 64);
	}
}
