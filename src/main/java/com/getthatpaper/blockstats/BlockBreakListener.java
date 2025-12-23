package com.getthatpaper.blockstats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.logging.Logger;

public class BlockBreakListener implements Listener {
    private final DataStore dataStore;
    private final Logger logger;

    public BlockBreakListener(DataStore dataStore, Logger logger) {
        this.dataStore = dataStore;
        this.logger = logger;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        var material = event.getBlock().getType().name();
        dataStore.recordBlockBreak(player.getUniqueId(), player.getName(), material);
    }
}
