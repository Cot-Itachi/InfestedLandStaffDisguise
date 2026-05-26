package dev.plug.disguise.paper;

import dev.plug.disguise.common.PluginChannels;
import dev.plug.disguise.common.message.MessageAction;
import dev.plug.disguise.common.message.PluginMessage;
import dev.plug.disguise.common.message.PluginMessageCodec;
import dev.plug.disguise.paper.gui.DisguiseGui;
import dev.plug.disguise.paper.input.InputSession;
import dev.plug.disguise.paper.listener.PaperListener;
import dev.plug.disguise.paper.messaging.ProxyBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DisguisePaper extends JavaPlugin implements CommandExecutor {

    private final List<String> rankIds = new ArrayList<>();
    private final Set<UUID> activeDisguises = ConcurrentHashMap.newKeySet();

    private InputSession inputSession;
    private DisguiseGui disguiseGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRanks();

        inputSession = new InputSession();
        ProxyBridge proxyBridge = new ProxyBridge(this);
        disguiseGui = new DisguiseGui(this, inputSession, proxyBridge);

        PaperListener listener = new PaperListener(this, disguiseGui, inputSession, proxyBridge);
        getServer().getPluginManager().registerEvents(listener, this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, PluginChannels.SYNC);
        getServer().getMessenger().registerIncomingPluginChannel(this, PluginChannels.STATE, (channel, player, data) -> {
            try {
                PluginMessage msg = PluginMessageCodec.decode(data);
                if (msg.action() == MessageAction.SYNC_STATE) {
                    UUID uuid = UUID.fromString(msg.body().get("uuid").getAsString());
                    boolean active = msg.body().get("active").getAsBoolean();
                    if (active) {
                        activeDisguises.add(uuid);
                    } else {
                        activeDisguises.remove(uuid);
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to handle state sync: " + e.getMessage());
            }
        });

        Objects.requireNonNull(getCommand("disguisegui"), "disguisegui command must be defined in plugin.yml").setExecutor(this);
        getLogger().info("DisguisePaper companion enabled.");
    }

    @Override
    public void onDisable() {
        if (inputSession != null) inputSession.clearAll();
        activeDisguises.clear();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, PluginChannels.SYNC);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, PluginChannels.STATE);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is only for players.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("disguise.gui")) {
            player.sendMessage(Component.text("You do not have permission to use this.", NamedTextColor.RED));
            return true;
        }
        disguiseGui.openMain(player);
        return true;
    }

    public boolean isDisguiseActive(UUID uuid) {
        return activeDisguises.contains(uuid);
    }

    public void clearDisguiseState(UUID uuid) {
        activeDisguises.remove(uuid);
    }

    private void loadRanks() {
        rankIds.clear();
        List<?> configured = getConfig().getList("ranks", Collections.emptyList());
        configured.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .forEach(rankIds::add);
        if (rankIds.isEmpty()) {
            rankIds.addAll(List.of("owner", "admin", "mod", "builder", "vip", "player"));
        }
    }

    public Collection<String> getRankIds() {
        return Collections.unmodifiableList(rankIds);
    }
}