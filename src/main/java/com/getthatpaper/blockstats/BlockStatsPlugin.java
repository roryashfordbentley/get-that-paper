package com.getthatpaper.blockstats;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public class BlockStatsPlugin extends JavaPlugin {
    private DataStore dataStore;
    private ApiServer apiServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String dbFileName = getConfig().getString("database.filename", "block-stats.db");
        File dbFile = new File(getDataFolder(), dbFileName);
        dataStore = new DataStore(dbFile, getLogger());
        try {
            dataStore.init();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to start BlockStatsAPI", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BlockBreakListener(dataStore, getLogger()), this);

        String host = getConfig().getString("api.host", "0.0.0.0");
        int port = getConfig().getInt("api.port", 8765);
        String context = getConfig().getString("api.context", "/api");
        apiServer = new ApiServer(host, port, context, dataStore, getLogger());
        try {
            apiServer.start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to start REST API", ex);
        }
    }

    @Override
    public void onDisable() {
        if (apiServer != null) {
            apiServer.stop();
        }
        if (dataStore != null) {
            dataStore.close();
        }
    }
}
