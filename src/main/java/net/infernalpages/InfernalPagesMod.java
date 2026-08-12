package net.infernalpages;

import net.fabricmc.api.ModInitializer;
import net.infernalpages.ban.BanManager;
import net.infernalpages.command.ModCommands;
import net.infernalpages.death.ContractProtectionHandler;
import net.infernalpages.death.KillHandler;
import net.infernalpages.health.HealthPenaltyManager;
import net.infernalpages.effect.ScriptureCalamity;
import net.infernalpages.registry.ModComponents;
import net.infernalpages.registry.ModItems;
import net.infernalpages.revive.ReviveChatHandler;
import net.infernalpages.ritual.RitualHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Infernal Pages mod.
 *
 * <p>Features:
 * <ul>
 *   <li><b>The Scripture</b> - a single-use item. When held in the main or off hand while the
 *       holder kills another player, it triggers a large explosion and permanently banishes
 *       (bans) the victim. Kills against non-players trigger the explosion but no ban.</li>
 *   <li><b>The Ritual</b> - place four candles in a cross (N/S/E/W) and throw (drop) an item
 *       into the middle. The thrower permanently loses 1 max health. Depending on the item,
 *       they receive The Scripture (default: book and quill) or a Revival Charm (default:
 *       enchanted golden apple).</li>
 *   <li><b>Revival</b> - while holding a Revival Charm, type a banished player's username in
 *       chat to revive them (remove them from the mod's ban file).</li>
 * </ul>
 */
public class InfernalPagesMod implements ModInitializer {
	public static final String MOD_ID = "infernalpages";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ModConfig CONFIG;
	public static BanManager BANS;
	public static net.infernalpages.contract.BrokenContracts BROKEN;

	@Override
	public void onInitialize() {
		CONFIG = ModConfig.load();
		BANS = new BanManager();
		BROKEN = new net.infernalpages.contract.BrokenContracts();

		ModComponents.register();
		ModItems.register();
		net.infernalpages.registry.ModSounds.register();
		net.infernalpages.registry.ModEntities.register();
		// Register the Mould of Souls recipe serializers (immune to recipe-codec overrides from other mods).
		net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,
				net.minecraft.util.Identifier.of(MOD_ID, "mould_crafting"),
				net.infernalpages.recipe.MouldCraftingSerializer.INSTANCE);
		net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.RECIPE_SERIALIZER,
				net.minecraft.util.Identifier.of(MOD_ID, "mould_shapeless"),
				net.infernalpages.recipe.MouldShapelessCraftingSerializer.INSTANCE);
		KillHandler.register();
		ContractProtectionHandler.register();
		net.infernalpages.contract.ContractBreakHandler.register();
		net.infernalpages.entity.MouldPickupHandler.register();
		ScriptureCalamity.register();
		net.infernalpages.effect.TaintedArmorHandler.register();
		RitualHandler.register();
		ReviveChatHandler.register();
		ModCommands.register();
		BanManager.registerEvents();
		net.infernalpages.contract.BrokenContracts.registerEvents();

		LOGGER.info("[Infernal Pages] Mod initialized.");
	}
}
