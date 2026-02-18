package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TaleMonPokemonTracker extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> registeredWorlds = new HashSet<>();

    private Pokemon_Tracker_Cache trackerCache;
    private WorldMapManager.MarkerProvider markerProvider;

    public TaleMonPokemonTracker(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());

        trackerCache = new Pokemon_Tracker_Cache();

        this.getCommandRegistry().registerCommand(new Command_Tracker(trackerCache));

        markerProvider = new Pokemon_Marker_Provider(trackerCache).asMarkerProvider();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                Universe u = Universe.get();
                if (u == null) return;

                u.getWorlds().values().forEach(world -> {
                    if (world == null || !world.isAlive()) return;

                    String worldName = world.getName();
                    if (registeredWorlds.contains(worldName)) return;

                    world.execute(() -> {
                        try {
                            var mapManager = world.getWorldMapManager();
                            if (mapManager == null) return;

                            String providerId = "TaleMonPokemonTracker";
                            var existing = mapManager.getMarkerProviders();
                            if (!existing.containsKey(providerId)) {
                                mapManager.addMarkerProvider(providerId, markerProvider);
                                LOGGER.atInfo().log("TaleMonPokemonTracker MarkerProvider registered for world: " + worldName);
                            }
                            registeredWorlds.add(worldName);
                        } catch (Throwable ignored) { }
                    });
                });

            } catch (Throwable ignored) { }
        }, 1, 5, TimeUnit.SECONDS);

        LOGGER.atInfo().log("TaleMonPokemonTracker ready: /tracker in-game (cache), provider will try to show markers on compass.");
    }
}