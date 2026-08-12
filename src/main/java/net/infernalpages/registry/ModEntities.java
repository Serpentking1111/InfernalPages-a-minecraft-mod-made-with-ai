package net.infernalpages.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.entity.MouldOfSoulsEntity;

/**
 * Registers Infernal Pages' custom entities.
 */
public final class ModEntities {
	/** The Mould of Souls — a soul guardian automaton. */
	public static final EntityType<MouldOfSoulsEntity> MOULD_OF_SOULS = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(InfernalPagesMod.MOD_ID, "mould_of_souls"),
			EntityType.Builder.<MouldOfSoulsEntity>create(MouldOfSoulsEntity::new, SpawnGroup.MISC)
					.dimensions(0.9f, 1.5f)
					.maxTrackingRange(8)
					.build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(InfernalPagesMod.MOD_ID, "mould_of_souls"))));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(MOULD_OF_SOULS, MouldOfSoulsEntity.createAttributes());
	}
}
