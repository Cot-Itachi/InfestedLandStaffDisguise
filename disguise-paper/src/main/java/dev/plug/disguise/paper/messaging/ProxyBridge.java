package dev.plug.disguise.paper.messaging;

import com.google.gson.JsonObject;
import dev.plug.disguise.common.PluginChannels;
import dev.plug.disguise.common.message.MessageAction;
import dev.plug.disguise.common.message.PluginMessage;
import dev.plug.disguise.common.message.PluginMessageCodec;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public final class ProxyBridge {

    private final JavaPlugin plugin;

    public ProxyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestNick(Player player, String nick) {
        send(player, MessageAction.REQUEST_NICK, body -> body.addProperty("nick", nick.trim()));
    }

    public void requestSkin(Player player, String username) {
        send(player, MessageAction.REQUEST_SKIN, body -> body.addProperty("username", username.trim()));
    }

    public void requestRank(Player player, String rankId) {
        send(player, MessageAction.REQUEST_RANK, body -> body.addProperty("rank", rankId));
    }

    public void requestToggle(Player player) {
        send(player, MessageAction.REQUEST_TOGGLE, body -> {
        });
    }

    public void requestClear(Player player) {
        send(player, MessageAction.REQUEST_CLEAR, body -> {
        });
    }

    private void send(Player player, MessageAction action, Consumer<JsonObject> bodyWriter) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", player.getUniqueId().toString());
        bodyWriter.accept(body);
        player.sendPluginMessage(plugin, PluginChannels.SYNC, PluginMessageCodec.encode(new PluginMessage(action, body)));
    }
}
