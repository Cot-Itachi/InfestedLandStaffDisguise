package dev.plug.disguise;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.util.GameProfile;
import dev.plug.disguise.rank.RankData;
import dev.plug.disguise.skin.SkinData;
import dev.plug.disguise.skin.SkinFetcher;
import dev.plug.disguise.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class DisguiseManager {

    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final ProxyServer server;
    private final SkinFetcher skinFetcher;
    private final StorageManager storage;

    public DisguiseManager(ProxyServer server, SkinFetcher skinFetcher, StorageManager storage) {
        this.server = server;
        this.skinFetcher = skinFetcher;
        this.storage = storage;
    }

    public CompletableFuture<Result> applyNick(Player player, String nick) {
        String normalizedNick = nick.trim();
        if (!PLAYER_NAME_PATTERN.matcher(normalizedNick).matches()) {
            return CompletableFuture.completedFuture(Result.failure("Nick must be between 3 and 16 letters, numbers, or underscores."));
        }

        DisguiseData data = storage.getOrCreate(player.getUniqueId());
        data.setNick(normalizedNick);
        data.setActive(true);
        storage.put(data);

        refreshDisplayName(player, data);
        return CompletableFuture.completedFuture(Result.success("Nick set to " + normalizedNick + "."));
    }

    public CompletableFuture<Result> applySkinByUsername(Player player, String username) {
        String normalizedUsername = username.trim();
        if (!PLAYER_NAME_PATTERN.matcher(normalizedUsername).matches()) {
            return CompletableFuture.completedFuture(Result.failure("Skin usernames must be between 3 and 16 letters, numbers, or underscores."));
        }

        return skinFetcher.fetchByUsername(normalizedUsername).thenApply(skinOpt -> {
            if (skinOpt.isEmpty()) {
                return Result.failure("Could not fetch skin for '" + normalizedUsername + "'.");
            }

            DisguiseData data = storage.getOrCreate(player.getUniqueId());
            data.setSkin(skinOpt.get());
            data.setActive(true);
            storage.put(data);

            refreshDisplayName(player, data);
            return Result.success("Skin changed to " + normalizedUsername + ". Reconnect to refresh your skin everywhere.");
        });
    }

    public CompletableFuture<Result> applyRank(Player player, RankData rank) {
        DisguiseData data = storage.getOrCreate(player.getUniqueId());
        data.setRank(rank);
        data.setActive(true);
        storage.put(data);

        refreshDisplayName(player, data);
        return CompletableFuture.completedFuture(Result.success("Rank set to " + rank.id() + "."));
    }

    public Result toggleDisguise(Player player) {
        DisguiseData data = storage.getOrCreate(player.getUniqueId());
        if (!data.hasAnyDisguise()) {
            return Result.failure("You have no disguise configured. Set a nick, skin, or rank first.");
        }

        boolean nowActive = !data.isActive();
        data.setActive(nowActive);
        storage.put(data);

        if (nowActive) {
            refreshDisplayName(player, data);
            return Result.success("Disguise enabled.");
        }

        resetTabList(player);
        return Result.success("Disguise disabled.");
    }

    public Result clearDisguise(Player player) {
        storage.remove(player.getUniqueId());
        resetTabList(player);
        return Result.success("Disguise cleared.");
    }

    public GameProfile buildDisguisedProfile(GameProfile original, DisguiseData data) {
        String targetName = data.getNick() != null ? data.getNick() : original.getName();
        List<GameProfile.Property> properties = new ArrayList<>(original.getProperties());

        SkinData skin = data.getSkin();
        if (skin != null) {
            properties.removeIf(property -> property.getName().equals("textures"));
            properties.add(new GameProfile.Property("textures", skin.value(), skin.signature()));
        }

        return new GameProfile(original.getId(), targetName, properties);
    }

    private void refreshDisplayName(Player target, DisguiseData data) {
        Component displayName = buildDisplayName(target.getUsername(), data);
        server.getAllPlayers().forEach(viewer -> updateTabEntry(viewer, target, displayName));
    }

    private void resetTabList(Player target) {
        Component originalName = Component.text(target.getUsername(), NamedTextColor.WHITE);
        server.getAllPlayers().forEach(viewer -> updateTabEntry(viewer, target, originalName));
    }

    private void updateTabEntry(Player viewer, Player target, Component displayName) {
        TabList tabList = viewer.getTabList();
        tabList.getEntries().stream()
            .filter(entry -> entry.getProfile().getId().equals(target.getUniqueId()))
            .findFirst()
            .ifPresent(entry -> entry.setDisplayName(displayName));
    }

    private Component buildDisplayName(String realName, DisguiseData data) {
        String name = data.getNick() != null ? data.getNick() : realName;
        RankData rank = data.getRank();

        if (rank == null) {
            return Component.text(name);
        }

        return Component.text()
            .append(Component.text(rank.prefix()).color(rank.color()))
            .append(Component.text(name))
            .append(Component.text(rank.suffix()).color(rank.color()))
            .build();
    }

    public sealed interface Result permits Result.Success, Result.Failure {
        boolean ok();
        String message();

        static Result success(String message) {
            return new Success(message);
        }

        static Result failure(String message) {
            return new Failure(message);
        }

        record Success(String message) implements Result {
            @Override
            public boolean ok() {
                return true;
            }
        }

        record Failure(String message) implements Result {
            @Override
            public boolean ok() {
                return false;
            }
        }
    }
}
