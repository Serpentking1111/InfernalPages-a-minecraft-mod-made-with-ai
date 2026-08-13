package net.infernalpages.ban;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.infernalpages.InfernalPagesMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The mod's own ban list, persisted per world save as
 * <code>&lt;world save&gt;/infernalpages/banned_players.json</code>.
 *
 * <p>This is a hard, "permanent death" style ban: banished players are kicked immediately and
 * refused entry on join. They are revived by removing their entry from this list (see {@link
 * net.infernalpages.revive.ReviveChatHandler}).
 *
 * <p>Bans are stored <em>inside each world save</em> (via {@code server.getSavePath(WorldSavePath.ROOT)}),
 * so every world save carries its own independent ban list. On a dedicated server this is the
 * server's world folder; in single-player/LAN it's the individual save folder.
 */
public class BanManager {
	public static class Entry {
		public String uuid;
		public String name;
		public String reason;
		public long bannedAt;

		public Entry() {
		}

		public Entry(String uuid, String name, String reason) {
			this.uuid = uuid;
			this.name = name;
			this.reason = reason;
			this.bannedAt = System.currentTimeMillis();
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final List<Entry> banned = new ArrayList<>();
	private MinecraftServer server;

	public BanManager() {
	}

	/** Binds this manager to a server (which provides the world save directory) and loads its ban list. */
	public void setServer(MinecraftServer server) {
		this.server = server;
		banned.clear();
		load();
	}

	private Path file() {
		Path root = server.getSavePath(WorldSavePath.ROOT);
		return root.resolve("infernalpages").resolve("banned_players.json");
	}

	private void load() {
		if (server == null) {
			return;
		}
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f)) {
			Type type = new TypeToken<List<Entry>>() {
			}.getType();
			List<Entry> loaded = GSON.fromJson(r, type);
			if (loaded != null) {
				banned.addAll(loaded);
			}
		} catch (IOException e) {
			InfernalPagesMod.LOGGER.error("Failed to load ban list", e);
		}
	}

	private void save() {
		if (server == null) {
			return;
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f)) {
				GSON.toJson(banned, w);
			}
		} catch (IOException e) {
			InfernalPagesMod.LOGGER.error("Failed to save ban list", e);
		}
	}

	public boolean isBanned(UUID uuid) {
		return banned.stream().anyMatch(e -> e.uuid.equals(uuid.toString()));
	}

	public Optional<Entry> findByName(String name) {
		return banned.stream().filter(e -> e.name.equalsIgnoreCase(name)).findFirst();
	}

	public Optional<Entry> findByUuid(UUID uuid) {
		if (uuid == null) {
			return Optional.empty();
		}
		return banned.stream().filter(e -> e.uuid.equals(uuid.toString())).findFirst();
	}

	public void ban(UUID uuid, String name, String reason) {
		if (isBanned(uuid)) {
			return;
		}
		banned.add(new Entry(uuid.toString(), name, reason));
		save();
	}

	/** Removes a player from the ban list by (case-insensitive) name. Returns true if found. */
	public boolean unban(String name) {
		boolean removed = banned.removeIf(e -> e.name.equalsIgnoreCase(name));
		if (removed) {
			save();
		}
		return removed;
	}

	public List<Entry> getAll() {
		return new ArrayList<>(banned);
	}

	/**
	 * Binds the ban manager to the running server (so the per-world ban file is used) and kicks
	 * any banished player the moment they try to join.
	 */
	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> InfernalPagesMod.BANS.setServer(server));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (InfernalPagesMod.BANS.server != server) {
				InfernalPagesMod.BANS.setServer(server);
			}
			ServerPlayerEntity player = handler.player;
			if (player == null) {
				return;
			}
			if (InfernalPagesMod.BANS.isBanned(player.getUuid())) {
				handler.disconnect(Text.literal("You have been banished by The Scripture. This is a permanent death."));
			}
		});
	}
}
