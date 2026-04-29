package dev.plug.disguise.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.plug.disguise.DisguiseManager;
import dev.plug.disguise.config.PluginConfig;
import dev.plug.disguise.rank.RankData;
import dev.plug.disguise.text.DisguiseMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.stream.Collectors;

public final class DisguiseCommand {

    private static final String PERM_USE = "disguise.use";
    private static final String PERM_ADMIN = "disguise.admin";

    private final DisguiseManager manager;
    private final PluginConfig config;

    public DisguiseCommand(DisguiseManager manager, PluginConfig config) {
        this.manager = manager;
        this.config = config;
    }

    public BrigadierCommand build() {
        LiteralCommandNode<CommandSource> root = LiteralArgumentBuilder
            .<CommandSource>literal("disguise")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                sendHelp(context.getSource());
                return Command.SINGLE_SUCCESS;
            })
            .then(buildNick())
            .then(buildSkin())
            .then(buildRank())
            .then(buildToggle())
            .then(buildClear())
            .then(buildReload())
            .build();

        return new BrigadierCommand(root);
    }

    private LiteralArgumentBuilder<CommandSource> buildNick() {
        return LiteralArgumentBuilder.<CommandSource>literal("nick")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                send(context.getSource(), Component.text("Usage: /disguise nick <name>", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            })
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("name", StringArgumentType.word())
                .executes(context -> {
                    Player player = requirePlayer(context.getSource());
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }

                    String name = StringArgumentType.getString(context, "name");
                    manager.applyNick(player, name).thenAccept(result -> sendResult(player, result));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private LiteralArgumentBuilder<CommandSource> buildSkin() {
        return LiteralArgumentBuilder.<CommandSource>literal("skin")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                send(context.getSource(), Component.text("Usage: /disguise skin <username>", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            })
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.word())
                .executes(context -> {
                    Player player = requirePlayer(context.getSource());
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }

                    String username = StringArgumentType.getString(context, "username");
                    send(player, DisguiseMessages.info("Fetching skin for " + username + "..."));
                    manager.applySkinByUsername(player, username).thenAccept(result -> sendResult(player, result));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private LiteralArgumentBuilder<CommandSource> buildRank() {
        return LiteralArgumentBuilder.<CommandSource>literal("rank")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                String availableRanks = config.getRanks().stream()
                    .map(RankData::id)
                    .collect(Collectors.joining(", "));
                send(context.getSource(), Component.text("Available ranks: " + availableRanks, NamedTextColor.GRAY));
                return Command.SINGLE_SUCCESS;
            })
            .then(RequiredArgumentBuilder.<CommandSource, String>argument("rank", StringArgumentType.word())
                .suggests((context, builder) -> {
                    config.getRanks().forEach(rank -> builder.suggest(rank.id()));
                    return builder.buildFuture();
                })
                .executes(context -> {
                    Player player = requirePlayer(context.getSource());
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }

                    String rankId = StringArgumentType.getString(context, "rank");
                    RankData rank = config.getRank(rankId);
                    if (rank == null) {
                        send(player, DisguiseMessages.error("Unknown rank: " + rankId));
                        return Command.SINGLE_SUCCESS;
                    }

                    manager.applyRank(player, rank).thenAccept(result -> sendResult(player, result));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private LiteralArgumentBuilder<CommandSource> buildToggle() {
        return LiteralArgumentBuilder.<CommandSource>literal("toggle")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                Player player = requirePlayer(context.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }

                sendResult(player, manager.toggleDisguise(player));
                return Command.SINGLE_SUCCESS;
            });
    }

    private LiteralArgumentBuilder<CommandSource> buildClear() {
        return LiteralArgumentBuilder.<CommandSource>literal("clear")
            .requires(source -> source.hasPermission(PERM_USE))
            .executes(context -> {
                Player player = requirePlayer(context.getSource());
                if (player == null) {
                    return Command.SINGLE_SUCCESS;
                }

                sendResult(player, manager.clearDisguise(player));
                return Command.SINGLE_SUCCESS;
            });
    }

    private LiteralArgumentBuilder<CommandSource> buildReload() {
        return LiteralArgumentBuilder.<CommandSource>literal("reload")
            .requires(source -> source.hasPermission(PERM_ADMIN))
            .executes(context -> {
                config.load();
                send(context.getSource(), DisguiseMessages.success("Configuration reloaded."));
                return Command.SINGLE_SUCCESS;
            });
    }

    private Player requirePlayer(CommandSource source) {
        if (source instanceof Player player) {
            return player;
        }

        send(source, Component.text("This command is only for players.", NamedTextColor.RED));
        return null;
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(Component.text("Disguise commands", NamedTextColor.AQUA));
        source.sendMessage(Component.text("/disguise nick <name> - Set a fake nickname", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/disguise skin <username> - Apply another player's skin", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/disguise rank <rank> - Apply a configured rank", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/disguise toggle - Enable or disable your disguise", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/disguise clear - Remove all disguise data", NamedTextColor.GRAY));
    }

    private void sendResult(CommandSource source, DisguiseManager.Result result) {
        send(source, result.ok() ? DisguiseMessages.success(result.message()) : DisguiseMessages.error(result.message()));
    }

    private void send(CommandSource source, Component message) {
        source.sendMessage(message);
    }
}
