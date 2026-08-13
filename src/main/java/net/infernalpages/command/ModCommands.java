package net.infernalpages.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.infernalpages.InfernalPagesMod;
import net.infernalpages.ban.BanManager;
import net.infernalpages.registry.ModItems;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the mod's admin commands.
 *
 * <p>The mod uses its own ban file (not the vanilla ban list), so vanilla {@code /unban} does
 * nothing to it. This command provides an operator-only way to revive (unban) a banished player:
 *
 * <pre>   /revive &lt;player&gt;</pre>
 *
 * <p>Both {@code /revive} and {@code /setowner} accept Minecraft's standard player selectors
 * (e.g. {@code @p}, {@code @a}, {@code @r}, or a player name) via {@link EntityArgumentType#player()}.
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var reviveNode = literal("revive")
					.requires(ModCommands::requireAdmin)
					.then(argument("player", EntityArgumentType.player())
							.executes(ModCommands::revive))
					.build();

			var setOwnerNode = literal("setowner")
					.requires(ModCommands::requireAdmin)
					.then(argument("player", EntityArgumentType.player())
							.executes(ModCommands::setOwner))
					.build();

			dispatcher.getRoot().addChild(reviveNode);
			dispatcher.getRoot().addChild(setOwnerNode);
			dispatcher.register(literal("infernalpages").then(literal("revive")
					.requires(ModCommands::requireAdmin)
					.then(argument("player", EntityArgumentType.player())
							.executes(ModCommands::revive))));
			dispatcher.register(literal("infernalpages").then(literal("setowner")
					.requires(ModCommands::requireAdmin)
					.then(argument("player", EntityArgumentType.player())
							.executes(ModCommands::setOwner))));
		});
	}

	/** Operators (admins) only. */
	private static boolean requireAdmin(ServerCommandSource source) {
		return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.ADMINS));
	}

	/**
	 * Debug helper: {@code /setowner <player>} sets the owner on the owner-bound item in the
	 * executor's main hand. <player> uses Minecraft's standard player selector, so it can be a name
	 * or a selector like {@code @p}. Works for the Contract (sets its maker/signer), the Unholy
	 * Charm (sets its holder), and the Contract Sword (sets its owner).
	 */
	private static int setOwner(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		if (!source.isExecutedByPlayer()) {
			source.sendError(Text.literal("This command must be run by a player.").formatted(Formatting.RED));
			return 0;
		}
		net.minecraft.server.network.ServerPlayerEntity executor = source.getPlayerOrThrow();
		net.minecraft.server.network.ServerPlayerEntity owner = EntityArgumentType.getPlayer(ctx, "player");

		net.minecraft.item.ItemStack stack = executor.getMainHandStack();
		if (stack.isEmpty()) {
			source.sendError(Text.literal("You must hold an owner-bound item in your main hand.").formatted(Formatting.RED));
			return 0;
		}

		String what = null;
		if (stack.isOf(ModItems.CONTRACT)) {
			stack.set(net.infernalpages.registry.ModComponents.CONTRACT_SIGNER, owner.getUuid());
			stack.set(net.infernalpages.registry.ModComponents.CONTRACT_SIGNER_NAME, owner.getName().getString());
			what = "Contract maker";
		} else if (stack.isOf(ModItems.UNHOLY_CHARM)) {
			stack.set(net.infernalpages.registry.ModComponents.UNHOLY_OWNER, owner.getUuid());
			stack.set(net.infernalpages.registry.ModComponents.UNHOLY_OWNER_NAME, owner.getName().getString());
			what = "Unholy Charm holder";
		} else if (stack.isOf(ModItems.CONTRACT_SWORD)) {
			stack.set(net.infernalpages.registry.ModComponents.SWORD_OWNER, owner.getUuid());
			stack.set(net.infernalpages.registry.ModComponents.SWORD_OWNER_NAME, owner.getName().getString());
			what = "Contract Sword owner";
		} else {
			source.sendError(Text.literal("The held item does not have a settable owner.").formatted(Formatting.RED));
			return 0;
		}

		String finalWhat = what;
		source.sendFeedback(() -> Text.literal(finalWhat + " set to " + owner.getName().getString() + ".")
				.formatted(Formatting.GOLD), true);
		return 1;
	}

	private static int revive(CommandContext<ServerCommandSource> ctx)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerCommandSource source = ctx.getSource();
		// Accepts a standard player selector or name; gives the matching player entity.
		net.minecraft.server.network.ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");

		// The ban list is keyed by UUID; resolve it from the selected player.
		BanManager.Entry entry = InfernalPagesMod.BANS.findByUuid(player.getUuid()).orElse(null);
		if (entry == null) {
			source.sendError(Text.literal("Player \"" + player.getName().getString() + "\" is not banished.")
					.formatted(Formatting.RED));
			return 0;
		}

		String revivedName = entry.name;
		InfernalPagesMod.BANS.unban(revivedName);
		source.sendFeedback(() ->
				Text.literal(revivedName + " has been revived from permanent death.")
						.formatted(Formatting.GOLD), true);
		return 1;
	}
}
