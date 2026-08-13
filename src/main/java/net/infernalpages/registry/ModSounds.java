package net.infernalpages.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;

/**
 * Custom sound events for Infernal Pages.
 */
public final class ModSounds {
	/** Plays when the contract nullifies player B's attack on player A. */
	public static final SoundEvent CONTRACT_BLOCK = SoundEvent.of(
			Identifier.of(InfernalPagesMod.MOD_ID, "contract_block"));

	private ModSounds() {
	}

	public static void register() {
		Registry.register(Registries.SOUND_EVENT, Identifier.of(InfernalPagesMod.MOD_ID, "contract_block"), CONTRACT_BLOCK);
	}
}
