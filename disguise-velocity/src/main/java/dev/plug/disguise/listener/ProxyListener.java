package dev.plug.disguise.listener;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.plug.disguise.DisguiseData;
import dev.plug.disguise.DisguiseManager;
import dev.plug.disguise.common.PluginChannels;
import dev.plug.disguise.common.message.PluginMessage;
import dev.plug.disguise.common.message.PluginMessageCodec;
import dev.plug.disguise.config.PluginConfig;
import dev.plug.disguise.rank.RankData;
import dev.plug.disguise.storage.StorageManager;
import dev.plug.disguise.text.DisguiseMessages;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

public final class ProxyListener {

    private static final MinecraftChannelIdentifier CHANNEL =
        MinecraftChannelIdentifier.from(PluginChannels.SYNC);

    private final ProxyServer server;
    private final Logger logger;
    private final DisguiseManager manager;
    private final StorageManager storage;
    private final PluginConfig config;

    public ProxyListener(ProxyServer server, Logger logger, DisguiseManager manager,
                         StorageManager storage, PluginConfig config) {
        this.server = server;
        this.logger = logger;
        this.manager = manager;
        this.storage = storage;
        this.config = config;
    }

    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (!config.isPersistDisguise()) {
            return;
        }

        Optional<DisguiseData> dataOpt = storage.get(event.getOriginalProfile().getId());
        if (dataOpt.isEmpty()) {
            return;
        }

        DisguiseData data = dataOpt.get();
        if (!data.isActive()) {
            return;
        }

        event.setGameProfile(manager.buildDisguisedProfile(event.getGameProfile(), data));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Optional<DisguiseData> dataOpt = storage.get(event.getPlayer().getUniqueId());
        if (dataOpt.isEmpty()) {
            return;
        }

        DisguiseData data = dataOpt.get();
        if (data.isActive() && !config.isPersistDisguise()) {
            data.setActive(false);
            storage.put(data);
            event.getPlayer().sendMessage(DisguiseMessages.info("Stored disguise data is inactive because persistDisguise is disabled."));
        }
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Optional<DisguiseData> dataOpt = storage.get(event.getPlayer().getUniqueId());
        if (dataOpt.isEmpty()) {
            return;
        }

        DisguiseData data = dataOpt.get();
        if (!data.isActive() || (data.getNick() == null && data.getRank() == null)) {
            return;
        }

        String displayName = buildChatName(event.getPlayer(), data);
        Component chatLine = DisguiseMessages.chat(displayName, event.getMessage());
        server.getAllPlayers().forEach(target -> target.sendMessage(chatLine));
        logger.info("<{}> {}", displayName, event.getMessage());
        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (config.isSaveOnQuit()) {
            storage.save();
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        try {
            handleBackendRequest(PluginMessageCodec.decode(event.getData()));
        } catch (RuntimeException ex) {
            logger.warn("Failed to handle plugin message on {}: {}", PluginChannels.SYNC, ex.getMessage());
        }
    }

    private void handleBackendRequest(PluginMessage request) {
        JsonObject body = request.body();
        UUID playerId = UUID.fromString(body.get("uuid").getAsString());
        Player player = server.getPlayer(playerId).orElse(null);
        if (player == null) {
            return;
        }

        switch (request.action()) {
            case REQUEST_NICK -> {
                String nick = body.get("nick").getAsString();
                manager.applyNick(player, nick).thenAccept(result -> player.sendMessage(formatResult(result)));
            }
            case REQUEST_SKIN -> {
                String username = body.get("username").getAsString();
                player.sendMessage(DisguiseMessages.info("Fetching skin for " + username + "..."));
                manager.applySkinByUsername(player, username).thenAccept(result -> player.sendMessage(formatResult(result)));
            }
            case REQUEST_RANK -> handleRankRequest(player, body);
            case REQUEST_TOGGLE -> player.sendMessage(formatResult(manager.toggleDisguise(player)));
            case REQUEST_CLEAR -> player.sendMessage(formatResult(manager.clearDisguise(player)));
        }
    }

    private void handleRankRequest(Player player, JsonObject body) {
        String rankId = body.get("rank").getAsString();
        RankData rank = config.getRank(rankId);
        if (rank == null) {
            player.sendMessage(DisguiseMessages.error("Unknown rank: " + rankId));
            return;
        }

        manager.applyRank(player, rank).thenAccept(result -> player.sendMessage(formatResult(result)));
    }

    private Component formatResult(DisguiseManager.Result result) {
        return result.ok()
            ? DisguiseMessages.success(result.message())
            : DisguiseMessages.error(result.message());
    }

    private String buildChatName(Player player, DisguiseData data) {
        StringBuilder builder = new StringBuilder();
        RankData rank = data.getRank();
        if (rank != null && !rank.prefix().isBlank()) {
            builder.append(rank.prefix());
        }
        builder.append(data.getNick() != null ? data.getNick() : player.getUsername());
        if (rank != null && !rank.suffix().isBlank()) {
            builder.append(rank.suffix());
        }
        return builder.toString();
    }
}
