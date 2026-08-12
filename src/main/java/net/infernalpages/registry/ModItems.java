package net.infernalpages.registry;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.item.ContractItem;
import net.infernalpages.item.ContractSwordItem;
import net.infernalpages.item.MouldOfSoulsItem;
import net.infernalpages.item.PuritySealItem;
import net.infernalpages.item.RevivalCharmItem;
import net.infernalpages.item.ScriptureItem;
import net.infernalpages.item.UnholyCharmItem;

import java.util.function.Function;

/**
 * Registers the mod's items and adds them to the creative INGREDIENTS tab.
 *
 * <p>Since Minecraft 1.21.2, every item must have a <b>registry key</b> set on its
 * {@code Item.Settings} (via {@code .registryKey(key)}). Without it the {@code Item}
 * constructor throws {@code NullPointerException: Item id not set} at startup. Each item is
 * therefore created with a registry-keyed {@code Item.Settings}, constructed, and registered
 * all together in {@link #register(String, Function)}.
 */
public final class ModItems {
	/** Single-use item that permanently banishes a player you kill. */
	public static final Item SCRIPTURE = register("scripture", ScriptureItem::new);

	/** Single-use item that lets you revive a banished player by typing their name in chat. */
	public static final Item REVIVAL_CHARM = register("revival_charm", RevivalCharmItem::new);

	/** An unsigned contract; two different players sign it to seal a pact. */
	public static final Item CONTRACT = register("contract", ContractItem::new);

	/** Single-use charm, bound to a doomed player (B); typing their name kills them permanently. */
	public static final Item UNHOLY_CHARM = register("unholy_charm", UnholyCharmItem::new);

	/** The Contract Sword: powerful, reusable, owner-bound, cannot harm the other signer. */
	public static final Item CONTRACT_SWORD = registerSword("contract_sword");

	/** Single-use item that resets the holder's max health to normal when right-clicked. */
	public static final Item PURITY_SEAL = register("purity_seal", PuritySealItem::new);

	/** Deploys a Mould of Souls guardian, owned by the player. */
	public static final Item MOULD_OF_SOULS = register("mould_of_souls", MouldOfSoulsItem::new);

	/** A shard obtained by smashing a broken Contract Sword; combine three to form the weapon. */
	public static final Item TAINTED_SHARD = registerShard("tainted_shard");

	/** "The remains of a tainted past" — a hybrid sword/hoe weapon. */
	public static final Item TAINTED_REMAINS = registerRemains("tainted_remains");

	/** The Calling Horn — summons all of the user's soul moulds to them. */
	public static final Item CALLING_HORN = register("calling_horn", net.infernalpages.item.CallingHornItem::new);

	private ModItems() {
	}

	/** Builds a registry-keyed {@code Item.Settings}, constructs the item and registers it. */
	private static Item register(String name, Function<Item.Settings, Item> factory) {
		Identifier id = Identifier.of(InfernalPagesMod.MOD_ID, name);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		Item.Settings settings = new Item.Settings().maxCount(1).registryKey(key);
		return Registry.register(Registries.ITEM, key, factory.apply(settings));
	}

	/** Registers the Contract Sword with its powerful attribute settings. */
	private static Item registerSword(String name) {
		Identifier id = Identifier.of(InfernalPagesMod.MOD_ID, name);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		Item.Settings settings = new Item.Settings()
				.maxCount(1)
				.maxDamage(2500)
				.fireproof()
				.enchantable(25)
				.attributeModifiers(ContractSwordItem.createAttributes())
				.registryKey(key);
		return Registry.register(Registries.ITEM, key, new ContractSwordItem(settings));
	}

	/** Registers the Tainted Shard with a max stack size of 64. */
	private static Item registerShard(String name) {
		Identifier id = Identifier.of(InfernalPagesMod.MOD_ID, name);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		Item.Settings settings = new Item.Settings().maxCount(64).registryKey(key);
		return Registry.register(Registries.ITEM, key, new net.infernalpages.item.TaintedRemainsItem.Shard(settings));
	}

	/** Registers "The remains of a tainted past" with sword/hoe-like attributes. */
	private static Item registerRemains(String name) {
		Identifier id = Identifier.of(InfernalPagesMod.MOD_ID, name);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		Item.Settings settings = new Item.Settings()
				.maxCount(1)
				.maxDamage(2500)
				.fireproof()
				.enchantable(25)
				.attributeModifiers(createRemainsAttributes())
				.registryKey(key);
		return Registry.register(Registries.ITEM, key, new net.infernalpages.item.TaintedRemainsItem(settings));
	}

	/** Attribute modifiers: 13 attack damage, 1.8 attack speed (sword mode). */
	private static net.minecraft.component.type.AttributeModifiersComponent createRemainsAttributes() {
		return net.minecraft.component.type.AttributeModifiersComponent.builder()
				.add(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE,
						new net.minecraft.entity.attribute.EntityAttributeModifier(
								Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, 12.0,
								net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
						net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
				.add(net.minecraft.entity.attribute.EntityAttributes.ATTACK_SPEED,
						new net.minecraft.entity.attribute.EntityAttributeModifier(
								Item.BASE_ATTACK_SPEED_MODIFIER_ID, -2.2,
								net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE),
						net.minecraft.component.type.AttributeModifierSlot.MAINHAND)
				.build();
	}

	public static void register() {
		// Items are registered eagerly above; here we just wire up the creative tab.
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
			entries.add(SCRIPTURE);
			entries.add(REVIVAL_CHARM);
			entries.add(CONTRACT);
			entries.add(UNHOLY_CHARM);
			entries.add(CONTRACT_SWORD);
			entries.add(PURITY_SEAL);
			entries.add(MOULD_OF_SOULS);
			entries.add(TAINTED_SHARD);
			entries.add(TAINTED_REMAINS);
			entries.add(CALLING_HORN);
		});
	}
}
