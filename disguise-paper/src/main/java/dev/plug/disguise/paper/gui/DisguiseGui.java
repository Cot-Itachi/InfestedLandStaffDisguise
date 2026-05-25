package dev.plug.disguise.paper.gui;

import dev.plug.disguise.paper.input.InputSession;
import dev.plug.disguise.paper.messaging.ProxyBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DisguiseGui {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final TextColor AQUA = TextColor.color(0x55C7FF);

    public static final Component TITLE_MAIN_COMPONENT = Component.text()
        .append(Component.text("Disguise", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
        .append(Component.text("Main Menu", NamedTextColor.GRAY))
        .build();
    public static final Component TITLE_RANK_COMPONENT = Component.text()
        .append(Component.text("Disguise", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
        .append(Component.text("Select Rank", NamedTextColor.GRAY))
        .build();
    public static final String TITLE_MAIN = legacy(TITLE_MAIN_COMPONENT);
    public static final String TITLE_RANK = legacy(TITLE_RANK_COMPONENT);

    private static final int SLOT_NICK = 11;
    private static final int SLOT_SKIN = 13;
    private static final int SLOT_RANK = 15;
    private static final int SLOT_TOGGLE = 29;
    private static final int SLOT_CLEAR = 31;
    private static final int SLOT_CLOSE = 33;

    private final JavaPlugin plugin;
    private final InputSession inputSession;
    private final ProxyBridge proxyBridge;

    public DisguiseGui(JavaPlugin plugin, InputSession inputSession, ProxyBridge proxyBridge) {
        this.plugin = plugin;
        this.inputSession = inputSession;
        this.proxyBridge = proxyBridge;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 45, TITLE_MAIN_COMPONENT);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItem(SLOT_NICK, buildItem(
            Material.NAME_TAG,
            Component.text("Change Nick", AQUA),
            List.of(
                Component.text("Set a fake name.", NamedTextColor.GRAY),
                Component.text("Click to enter a nickname.", NamedTextColor.YELLOW)
            )
        ));
        inventory.setItem(SLOT_SKIN, buildItem(
            Material.PLAYER_HEAD,
            Component.text("Change Skin", NamedTextColor.LIGHT_PURPLE),
            List.of(
                Component.text("Use another player's skin.", NamedTextColor.GRAY),
                Component.text("Click to enter a username.", NamedTextColor.YELLOW)
            )
        ));
        inventory.setItem(SLOT_RANK, buildItem(
            Material.DIAMOND_CHESTPLATE,
            Component.text("Change Rank", NamedTextColor.GOLD),
            List.of(
                Component.text("Fake a rank prefix.", NamedTextColor.GRAY),
                Component.text("Click to select a rank.", NamedTextColor.YELLOW)
            )
        ));
        inventory.setItem(SLOT_TOGGLE, buildItem(
            Material.LIME_DYE,
            Component.text("Toggle Disguise", NamedTextColor.GREEN),
            List.of(Component.text("Enable or disable your disguise.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SLOT_CLEAR, buildItem(
            Material.RED_DYE,
            Component.text("Clear Disguise", NamedTextColor.RED),
            List.of(Component.text("Remove all disguise data.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SLOT_CLOSE, buildItem(
            Material.BARRIER,
            Component.text("Close", NamedTextColor.GRAY),
            List.of(Component.text("Close this menu.", NamedTextColor.GRAY))
        ));

        player.openInventory(inventory);
    }

    public void openRankSelection(Player player, List<String> rankIds) {
        int rows = Math.max(2, (int) Math.ceil(rankIds.size() / 9.0) + 1);
        int size = Math.min(rows * 9, 54);
        Inventory inventory = Bukkit.createInventory(null, size, TITLE_RANK_COMPONENT);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        Material[] swatches = {
            Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL,
            Material.LIME_WOOL, Material.CYAN_WOOL, Material.BLUE_WOOL,
            Material.PURPLE_WOOL, Material.PINK_WOOL, Material.WHITE_WOOL
        };

        for (int index = 0; index < rankIds.size() && index < size - 9; index++) {
            String rankId = rankIds.get(index);
            inventory.setItem(index, buildItem(
                swatches[index % swatches.length],
                Component.text(rankId, NamedTextColor.YELLOW),
                List.of(
                    Component.text("Click to apply this rank.", NamedTextColor.GRAY),
                    Component.text()
                        .append(Component.text("ID: ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(rankId, NamedTextColor.WHITE))
                        .build()
                )
            ));
        }

        inventory.setItem(size - 5, buildItem(
            Material.BARRIER,
            Component.text("Back", NamedTextColor.RED),
            List.of(Component.text("Return to the main menu.", NamedTextColor.GRAY))
        ));
        player.openInventory(inventory);
    }

    public void promptNick(Player player) {
        player.closeInventory();
        player.sendMessage(prefixedMessage(
            Component.text("Type your desired nickname in chat, or type ", NamedTextColor.GRAY)
                .append(Component.text("cancel", NamedTextColor.RED))
                .append(Component.text(" to abort.", NamedTextColor.GRAY))
        ));

        inputSession.expect(player, input -> {
            if ("cancel".equalsIgnoreCase(input)) {
                player.sendMessage(prefixedMessage(Component.text("Cancelled.", NamedTextColor.GRAY)));
                reopenMain(player);
                return;
            }

            proxyBridge.requestNick(player, input);
            reopenMain(player);
        });
    }

    public void promptSkin(Player player) {
        player.closeInventory();
        player.sendMessage(prefixedMessage(
            Component.text("Type the ", NamedTextColor.GRAY)
                .append(Component.text("Minecraft username", NamedTextColor.YELLOW))
                .append(Component.text(" for the skin you want, or type ", NamedTextColor.GRAY))
                .append(Component.text("cancel", NamedTextColor.RED))
                .append(Component.text(" to abort.", NamedTextColor.GRAY))
        ));

        inputSession.expect(player, input -> {
            if ("cancel".equalsIgnoreCase(input)) {
                player.sendMessage(prefixedMessage(Component.text("Cancelled.", NamedTextColor.GRAY)));
                reopenMain(player);
                return;
            }

            proxyBridge.requestSkin(player, input);
            reopenMain(player);
        });
    }

    private void reopenMain(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
    }

    private static ItemStack buildItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inventory, Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static Component prefixedMessage(Component message) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text("Disguise", AQUA))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(message)
            .build();
    }

    private static String legacy(Component component) {
        return LEGACY.serialize(component);
    }
}
