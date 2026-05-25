package dev.plug.disguise.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.plug.disguise.DisguiseData;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StorageManager {

    private static final Gson GSON     = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE   = "disguises.json";

    private final Path storageFile;
    private final Logger logger;
    private final Map<UUID, DisguiseData> store = new ConcurrentHashMap<>();

    public StorageManager(Path dataDirectory, Logger logger) {
        this.logger      = logger;
        this.storageFile = dataDirectory.resolve(FILE);
    }

    public void load() {
        store.clear();
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            String raw = Files.readString(storageFile);
            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (JsonElement el : arr) {
                DisguiseData data = DisguiseData.deserialize(el.getAsJsonObject());
                store.put(data.getPlayerUuid(), data);
            }
            logger.info("Loaded {} disguise entries.", store.size());
        } catch (Exception e) {
            logger.error("Could not read {}: {}", storageFile, e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(storageFile.getParent());
            JsonArray arr = new JsonArray();
            List<DisguiseData> disguises = store.values().stream()
                .filter(DisguiseData::hasAnyDisguise)
                .sorted((left, right) -> left.getPlayerUuid().compareTo(right.getPlayerUuid()))
                .toList();
            disguises.stream()
                .map(DisguiseData::serialize)
                .forEach(arr::add);
            Files.writeString(storageFile, GSON.toJson(arr));
        } catch (IOException e) {
            logger.error("Could not save {}: {}", storageFile, e.getMessage());
        }
    }

    public Optional<DisguiseData> get(UUID uuid) {
        return Optional.ofNullable(store.get(uuid));
    }

    public DisguiseData getOrCreate(UUID uuid) {
        return store.computeIfAbsent(uuid, DisguiseData::new);
    }

    public void put(DisguiseData data) {
        store.put(data.getPlayerUuid(), data);
    }

    public void remove(UUID uuid) {
        store.remove(uuid);
    }

    public Collection<DisguiseData> all() {
        return store.values();
    }
}
