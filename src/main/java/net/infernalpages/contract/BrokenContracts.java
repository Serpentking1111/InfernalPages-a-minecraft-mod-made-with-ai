package net.infernalpages.contract;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.infernalpages.InfernalPagesMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A persistent record of severed contracts, stored per world save as
 * <code>&lt;world save&gt;/infernalpages/broken_contracts.json</code>.
 *
 * <p>When a contract is broken, its {@code contract_id} is recorded here. Any Unholy Charm or
 * Contract Sword carrying that {@code contract_id} is invalidated — even if it was stashed in a
 * chest or carried by an offline player — so it can never be used again.
 */
public final class BrokenContracts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Set<String> broken = new HashSet<>();
	private MinecraftServer server;

	public BrokenContracts() {
	}

	public void setServer(MinecraftServer server) {
		this.server = server;
		broken.clear();
		load();
	}

	private Path file() {
		Path root = server.getSavePath(WorldSavePath.ROOT);
		return root.resolve("infernalpages").resolve("broken_contracts.json");
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
			Type type = new TypeToken<Set<String>>() {
			}.getType();
			Set<String> loaded = GSON.fromJson(r, type);
			if (loaded != null) {
				broken.addAll(loaded);
			}
		} catch (IOException e) {
			InfernalPagesMod.LOGGER.error("Failed to load broken contracts", e);
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
				GSON.toJson(broken, w);
			}
		} catch (IOException e) {
			InfernalPagesMod.LOGGER.error("Failed to save broken contracts", e);
		}
	}

	public boolean isBroken(UUID contractId) {
		return contractId != null && broken.contains(contractId.toString());
	}

	public boolean isBroken(String contractId) {
		return contractId != null && broken.contains(contractId);
	}

	/** Records a contract as severed and persists it. */
	public void breakContract(UUID contractId) {
		if (contractId == null) {
			return;
		}
		broken.add(contractId.toString());
		save();
	}

	/** Binds the manager to the running server (so the per-world file is used). */
	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> InfernalPagesMod.BROKEN.setServer(server));
	}
}
