package dev.plug.disguise.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class SkinFetcher {

    private static final String MOJANG_API_UUID  = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String MOJANG_API_SKIN  = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final HttpClient http;
    private final Logger logger;
    private final Map<String, SkinData> cache = new ConcurrentHashMap<>();

    public SkinFetcher(Logger logger) {
        this.logger = logger;
        this.http   = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    public CompletableFuture<Optional<SkinData>> fetchByUsername(String username) {
        String key = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(key).matches()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        SkinData cached = cache.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        return resolveUUID(username)
            .thenCompose(uuidOpt -> uuidOpt
                .map(this::fetchSkinByUUID)
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
            .thenApply(skinOpt -> {
                skinOpt.ifPresent(skin -> cache.put(key, skin));
                return skinOpt;
            });
    }

    private CompletableFuture<Optional<UUID>> resolveUUID(String username) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MOJANG_API_UUID + username))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) return Optional.<UUID>empty();
                JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                String rawId = body.get("id").getAsString();
                return Optional.of(formatUUID(rawId));
            })
            .exceptionally(ex -> {
                logger.warn("Failed to resolve UUID for {}: {}", username, ex.getMessage());
                return Optional.empty();
            });
    }

    private CompletableFuture<Optional<SkinData>> fetchSkinByUUID(UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MOJANG_API_SKIN + uuid + "?unsigned=false"))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) return Optional.<SkinData>empty();
                JsonObject body       = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray  properties = body.getAsJsonArray("properties");

                for (var element : properties) {
                    JsonObject prop = element.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        return Optional.of(new SkinData(
                            prop.get("value").getAsString(),
                            prop.get("signature").getAsString()
                        ));
                    }
                }
                return Optional.<SkinData>empty();
            })
            .exceptionally(ex -> {
                logger.warn("Failed to fetch skin for UUID {}: {}", uuid, ex.getMessage());
                return Optional.empty();
            });
    }

    public void invalidate(String username) {
        cache.remove(username.toLowerCase(Locale.ROOT));
    }

    private static UUID formatUUID(String raw) {
        if (raw.length() != 32) {
            throw new IllegalArgumentException("Unexpected UUID payload length: " + raw.length());
        }
        return UUID.fromString(
            raw.substring(0, 8)  + "-" +
            raw.substring(8, 12) + "-" +
            raw.substring(12, 16) + "-" +
            raw.substring(16, 20) + "-" +
            raw.substring(20)
        );
    }
}
