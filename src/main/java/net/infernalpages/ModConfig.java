package net.infernalpages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple JSON config stored at <code>config/infernalpages/config.json</code>.
 *
 * <p><code>ritualIngredients</code> maps a thrown item id to the item id it grants.
 * <code>explosionPower</code> controls the strength of The Scripture's explosion.
 */
public class ModConfig {
	public float explosionPower = 12.0f;
	public float calamityBasePower = 4.0f;
	public Map<String, String> ritualIngredients = new LinkedHashMap<>();

	public static ModConfig load() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("infernalpages");
		Path file = dir.resolve("config.json");

		ModConfig cfg = new ModConfig();
		cfg.ritualIngredients.put("minecraft:writable_book", "infernalpages:scripture");
		cfg.ritualIngredients.put("minecraft:enchanted_golden_apple", "infernalpages:revival_charm");
		cfg.ritualIngredients.put("infernalpages:scripture", "infernalpages:contract");

		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file)) {
				ModConfig loaded = new Gson().fromJson(r, ModConfig.class);
				if (loaded != null) {
					cfg.explosionPower = loaded.explosionPower;
					cfg.calamityBasePower = loaded.calamityBasePower;
					if (loaded.ritualIngredients != null && !loaded.ritualIngredients.isEmpty()) {
						cfg.ritualIngredients = loaded.ritualIngredients;
					}
				}
			} catch (IOException e) {
				InfernalPagesMod.LOGGER.error("Failed to load config, using defaults", e);
			}
		} else {
			try {
				Files.createDirectories(dir);
				try (Writer w = Files.newBufferedWriter(file)) {
					new GsonBuilder().setPrettyPrinting().create().toJson(cfg, w);
				}
			} catch (IOException e) {
				InfernalPagesMod.LOGGER.error("Failed to write default config", e);
			}
		}
		return cfg;
	}
}
