package dev.plug.disguise.paper.input;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class InputSession {

    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public void expect(Player player, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public void fulfill(Player player, String input) {
        Consumer<String> callback = pending.remove(player.getUniqueId());
        if (callback != null) {
            callback.accept(input);
        }
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    public void clearAll() {
        pending.clear();
    }
}
