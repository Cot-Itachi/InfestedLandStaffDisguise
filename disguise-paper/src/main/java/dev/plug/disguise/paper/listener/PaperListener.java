package dev.plug.disguise.paper.listener;

import dev.plug.disguise.paper.DisguisePaper;
import dev.plug.disguise.paper.gui.DisguiseGui;
import dev.plug.disguise.paper.input.InputSession;
import dev.plug.disguise.paper.messaging.ProxyBridge;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;

public final class PaperListener implements Listener {

    private final DisguisePaper plugin;
    private final DisguiseGui gui;
    private final InputSession inputSession;
    private final ProxyBridge proxyBridge;

    public PaperListener(DisguisePaper plugin, DisguiseGui gui, InputSession inputSession, ProxyBridge proxyBridge) {
        this.plugin = plugin;
        this.gui = gui;
        this.inputSession = inputSession;
        this.proxyBridge = proxyBridge;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (DisguiseGui.TITLE_MAIN.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                handleMainClick(player, event.getSlot());
            }
            return;
        }

        if (DisguiseGui.TITLE_RANK.equals(title)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                handleRankClick(player, event.getSlot(), event.getInventory().getSize());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!inputSession.hasPending(player)) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (event.isAsynchronous()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> inputSession.fulfill(player, input));
            return;
        }

        inputSession.fulfill(player, input);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!inputSession.hasPending(player)) {
            return;
        }

        String raw = event.getMessage();
        if ("/cancel".equalsIgnoreCase(raw)) {
            event.setCancelled(true);
            inputSession.fulfill(player, "cancel");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inputSession.cancel(event.getPlayer());
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 11 -> gui.promptNick(player);
            case 13 -> gui.promptSkin(player);
            case 15 -> gui.openRankSelection(player, new ArrayList<>(plugin.getRankIds()));
            case 29 -> {
                player.closeInventory();
                proxyBridge.requestToggle(player);
            }
            case 31 -> {
                player.closeInventory();
                proxyBridge.requestClear(player);
            }
            case 33 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleRankClick(Player player, int slot, int inventorySize) {
        if (slot == inventorySize - 5) {
            gui.openMain(player);
            return;
        }

        List<String> rankIds = new ArrayList<>(plugin.getRankIds());
        if (slot < rankIds.size()) {
            player.closeInventory();
            proxyBridge.requestRank(player, rankIds.get(slot));
        }
    }
}
