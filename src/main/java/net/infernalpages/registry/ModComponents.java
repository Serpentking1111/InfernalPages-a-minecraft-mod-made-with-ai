package net.infernalpages.registry;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.infernalpages.InfernalPagesMod;

import java.util.UUID;

/**
 * Custom data components used by the contract system. These store player links directly on the
 * relevant item stacks (the contract's signer, the Unholy Charm's target, and the Contract
 * Sword's owner / forbidden target), so they travel with the items and persist in player data.
 */
public final class ModComponents {
	/** UUID of the first signer (player A) on a Contract item. */
	public static final ComponentType<UUID> CONTRACT_SIGNER = register(
			"contract_signer", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Unique UUID shared by all items produced by a single sealed pact (charm + sword). */
	public static final ComponentType<UUID> CONTRACT_ID = register(
			"contract_id", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Username of the first signer (player A) on a Contract item. */
	public static final ComponentType<String> CONTRACT_SIGNER_NAME = register(
			"contract_signer_name", Codec.STRING, PacketCodecs.STRING);

	/** UUID of the player the Unholy Charm is bound to as its holder (player A). */
	public static final ComponentType<UUID> UNHOLY_OWNER = register(
			"unholy_owner", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Username of the Unholy Charm's holder (player A). */
	public static final ComponentType<String> UNHOLY_OWNER_NAME = register(
			"unholy_owner_name", Codec.STRING, PacketCodecs.STRING);

	/** UUID of the doomed player (B) that an Unholy Charm targets. */
	public static final ComponentType<UUID> UNHOLY_TARGET = register(
			"unholy_target", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Username of the doomed player (B) that an Unholy Charm targets. */
	public static final ComponentType<String> UNHOLY_TARGET_NAME = register(
			"unholy_target_name", Codec.STRING, PacketCodecs.STRING);

	/** UUID of the player the Contract Sword is bound to (its owner). */
	public static final ComponentType<UUID> SWORD_OWNER = register(
			"sword_owner", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Username of the Contract Sword's owner (player B). */
	public static final ComponentType<String> SWORD_OWNER_NAME = register(
			"sword_owner_name", Codec.STRING, PacketCodecs.STRING);

	/** UUID of the player the Contract Sword cannot harm (player A). */
	public static final ComponentType<UUID> SWORD_FORBIDDEN = register(
			"sword_forbidden", Uuids.CODEC, Uuids.PACKET_CODEC);

	/** Marks a contract item (Unholy Charm / Contract Sword) as severed so it can no longer be used. */
	public static final ComponentType<Boolean> CONTRACT_BROKEN = register(
			"contract_broken", Codec.BOOL, PacketCodecs.BOOLEAN);

	/** The current mode of the Tainted Remains weapon: "sword" or "hoe". */
	public static final ComponentType<String> TAINTED_MODE = register(
			"tainted_mode", Codec.STRING, PacketCodecs.STRING);

	/** Marks an armour piece as reinforced with a Tainted Shard (grants the Tainted hit-shield). */
	public static final ComponentType<Boolean> TAINTED = register(
			"tainted", Codec.BOOL, PacketCodecs.BOOLEAN);

	/** The current sharpening effect on a weapon (a {@code Sharpening} id), or null if unsharpened. */
	public static final ComponentType<String> SHARPENING = register(
			"sharpening", Codec.STRING, PacketCodecs.STRING);

	/**
	 * Snapshot of a weapon's enchantments taken <em>before</em> a BLUNT sharpening wiped them, so
	 * {@link net.infernalpages.item.Sharpening#applyToStack} can put them back when the player
	 * rerolls away from BLUNT.
	 *
	 * <p>Set only when BLUNT is applied to a weapon that still has enchantments; cleared the
	 * moment the weapon leaves BLUNT. Invariant: SAVED_ENCHANTMENTS is set iff the weapon is
	 * currently BLUNT and has at least one enchantment to restore later.
	 */
	public static final ComponentType<ItemEnchantmentsComponent> SAVED_ENCHANTMENTS = register(
			"saved_enchantments", ItemEnchantmentsComponent.CODEC, ItemEnchantmentsComponent.PACKET_CODEC);

	private ModComponents() {
	}

	private static <T> ComponentType<T> register(String name, Codec<T> codec,
			PacketCodec<? super RegistryByteBuf, T> packetCodec) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE,
				Identifier.of(InfernalPagesMod.MOD_ID, name),
				ComponentType.<T>builder().codec(codec).packetCodec(packetCodec).build());
	}

	/** Called to ensure the component types are registered at startup. */
	public static void register() {
	}
}
