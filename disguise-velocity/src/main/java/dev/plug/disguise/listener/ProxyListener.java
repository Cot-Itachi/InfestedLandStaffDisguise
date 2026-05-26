package dev.plug.disguise.listener;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.plug.disguise.DisguiseData;
import dev.plug.disguise.DisguiseManager;
import dev.plug.disguise.common.PluginChannels;
import dev.plug.disguise.common.message.MessageAction;
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

    private static final MinecraftChannelIdentifier SYNC_CHANNEL  = MinecraftChannelIdentifier.from(PluginChannels.SYNC);
    private static final MinecraftChannelIdentifier STATE_CHANNEL = MinecraftChannelIdentifier.from(PluginChannels.STATE);
    private static final String PERM_USE = "disguise.use";

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
        if (!config.isPersistDisguise()) return;
        storage.get(event.getOriginalProfile().getId()).ifPresent(data -> {
            if (data.isActive()) {
                event.setGameProfile(manager.buildDisguisedProfile(event.getGameProfile(), data));
            }
        });
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        storage.get(event.getPlayer().getUniqueId()).ifPresent(data -> {
            if (data.isActive() && !config.isPersistDisguise()) {
                data.setActive(false);
                storage.put(data);
                event.getPlayer().sendMessage(DisguiseMessages.info("Stored disguise data is inactive because persistDisguise is disabled."));
            }
        });
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        sendStateSync(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (config.isSaveOnQuit()) storage.save();
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Optional<DisguiseData> dataOpt = storage.get(event.getPlayer().getUniqueId());
        if (dataOpt.isEmpty()) return;

        DisguiseData data = dataOpt.get();
        if (!data.isActive() || (data.getNick() == null && data.getRank() == null)) return;

        String displayName = buildChatName(event.getPlayer(), data);
        Component chatLine = DisguiseMessages.chat(displayName, event.getMessage());
        server.getAllPlayers().forEach(p -> p.sendMessage(chatLine));
        logger.info("<{}> {}", displayName, event.getMessage());

        event.setResult(PlayerChatEvent.ChatResult.allowed());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!SYNC_CHANNEL.equals(event.getIdentifier())) return;
        if (!(event.getSource() instanceof ServerConnection)) return;

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
        if (player == null) return;

        if (!player.hasPermission(PERM_USE)) {
            player.sendMessage(DisguiseMessages.error("You do not have permission to use disguise."));
            return;
        }

        switch (request.action()) {
            case REQUEST_NICK -> {
                String nick = body.get("nick").getAsString();
                manager.applyNick(player, nick).thenAccept(result -> {
                    player.sendMessage(formatResult(result));
                    sendStateSync(player);
                });
            }
            case REQUEST_SKIN -> {
                String username = body.get("username").getAsString();
                player.sendMessage(DisguiseMessages.info("Fetching skin for " + username + "..."));
                manager.applySkinByUsername(player, username).thenAccept(result -> {
                    player.sendMessage(formatResult(result));
                    sendStateSync(player);
                });
            }
            case REQUEST_RANK -> handleRankRequest(player, body);
            case REQUEST_TOGGLE -> {
                player.sendMessage(formatResult(manager.toggleDisguise(player)));
                sendStateSync(player);
            }
            case REQUEST_CLEAR -> {
                player.sendMessage(formatResult(manager.clearDisguise(player)));
                sendStateSync(player);
            }
            default -> logger.warn("Unhandled action: {}", request.action());
        }
    }

    private void handleRankRequest(Player player, JsonObject body) {
        String rankId = body.get("rank").getAsString();
        RankData rank = config.getRank(rankId);
        if (rank == null) {
            player.sendMessage(DisguiseMessages.error("Unknown rank: " + rankId));
            return;
        }
        manager.applyRank(player, rank).thenAccept(result -> {
            player.sendMessage(formatResult(result));
            sendStateSync(player);
        });
    }

    private void sendStateSync(Player player) {
        player.getCurrentServer().ifPresent(conn -> sendStateSync(player, conn));
    }

    private void sendStateSync(Player player, ServerConnection conn) {
        boolean active = storage.get(player.getUniqueId()).map(DisguiseData::isActive).orElse(false);
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUniqueId().toString());
        body.addProperty("active", active);
        conn.sendPluginMessage(STATE_CHANNEL, PluginMessageCodec.encode(new PluginMessage(MessageAction.SYNC_STATE, body)));
    }

    private Component formatResult(DisguiseManager.Result result) {
        return result.ok() ? DisguiseMessages.success(result.message()) : DisguiseMessages.error(result.message());
    }

    private String buildChatName(Player player, DisguiseData data) {
        StringBuilder sb = new StringBuilder();
        RankData rank = data.getRank();
        if (rank != null && !rank.prefix().isBlank()) sb.append(rank.prefix());
        sb.append(data.getNick() != null ? data.getNick() : player.getUsername());
        if (rank != null && !rank.suffix().isBlank()) sb.append(rank.suffix());
        return sb.toString();
    }
}