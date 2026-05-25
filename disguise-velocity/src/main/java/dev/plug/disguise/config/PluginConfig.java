package dev.plug.disguise.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.plug.disguise.rank.RankData;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginConfig {

    private final Path      dataDirectory;
    private final Logger    logger;

    private final Map<String, RankData> ranks = new LinkedHashMap<>();
    private boolean saveOnQuit = true;
    private boolean persistDisguise = true;

    public PluginConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger        = logger;
    }

    public void load() {
        Path configFile = dataDirectory.resolve("config.json");
        if (!Files.exists(configFile)) {
            writeDefaults(configFile);
        }
        try {
            String raw = Files.readString(configFile);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            parseSettings(root);
            parseRanks(root);
        } catch (Exception e) {
            logger.error("Failed to load config.json: {}", e.getMessage());
        }
    }

    private void parseSettings(JsonObject root) {
        if (root.has("saveOnQuit"))      saveOnQuit      = root.get("saveOnQuit").getAsBoolean();
        if (root.has("persistDisguise")) persistDisguise = root.get("persistDisguise").getAsBoolean();
    }

    private void parseRanks(JsonObject root) {
        ranks.clear();
        if (!root.has("ranks")) return;
        JsonArray arr = root.getAsJsonArray("ranks");
        for (JsonElement el : arr) {
            JsonObject obj  = el.getAsJsonObject();
            RankData   rank = RankData.fromJson(obj);
            ranks.put(rank.id().toLowerCase(), rank);
        }
        logger.info("Loaded {} ranks from config.", ranks.size());
    }

    private void writeDefaults(Path target) {
        try (InputStream in = getClass().getResourceAsStream("/config.json")) {
            if (in == null) {
                throw new IOException("Bundled config.json resource is missing.");
            }
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.copy(in, target);
                return;
            }
        } catch (IOException e) {
            logger.error("Could not write default config: {}", e.getMessage());
        }
    }

    public Collection<RankData> getRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    public RankData getRank(String id) {
        return ranks.get(id.toLowerCase());
    }

    public boolean isSaveOnQuit() { return saveOnQuit; }
    public boolean isPersistDisguise() { return persistDisguise; }
}
