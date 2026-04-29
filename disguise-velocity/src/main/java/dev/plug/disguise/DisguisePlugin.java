package dev.plug.disguise;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.plug.disguise.command.DisguiseCommand;
import dev.plug.disguise.config.PluginConfig;
import dev.plug.disguise.listener.ProxyListener;
import dev.plug.disguise.skin.SkinFetcher;
import dev.plug.disguise.storage.StorageManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "disguise",
    name = "Disguise",
    version = "1.0.0",
    description = "Advanced disguise plugin for network-wide nick, skin, and rank changes.",
    authors = {"plug"}
)
public final class DisguisePlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private StorageManager storageManager;
    private PluginConfig pluginConfig;

    @Inject
    public DisguisePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        pluginConfig = new PluginConfig(dataDirectory, logger);
        storageManager = new StorageManager(dataDirectory, logger);
        DisguiseManager disguiseManager = new DisguiseManager(server, new SkinFetcher(logger), storageManager);

        pluginConfig.load();
        storageManager.load();

        server.getCommandManager().register(
            server.getCommandManager().metaBuilder("disguise")
                .aliases("dis", "d")
                .plugin(this)
                .build(),
            new DisguiseCommand(disguiseManager, pluginConfig).build()
        );
        server.getEventManager().register(this, new ProxyListener(
            server,
            logger,
            disguiseManager,
            storageManager,
            pluginConfig
        ));

        logger.info("Disguise loaded with {} configured rank(s).", pluginConfig.getRanks().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (storageManager != null) {
            storageManager.save();
        }
        logger.info("Disguise saved and shut down.");
    }
}
