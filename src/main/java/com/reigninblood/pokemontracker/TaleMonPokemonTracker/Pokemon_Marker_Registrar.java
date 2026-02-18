package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Pokemon_Marker_Registrar {

    private static final Set<String> registeredWorlds = new HashSet<>();
    private static final String PROVIDER_ID = "TaleMonPokemonTracker";

    public static void ensureRegistered(World world, WorldMapManager.MarkerProvider provider) {
        if (world == null || !world.isAlive()) return;

        String worldName = world.getName();
        if (registeredWorlds.contains(worldName)) return;

        WorldMapManager mapManager = world.getWorldMapManager();
        if (mapManager == null) return;

        Map<String, ?> existing = mapManager.getMarkerProviders();
        if (!existing.containsKey(PROVIDER_ID)) {
            mapManager.addMarkerProvider(PROVIDER_ID, provider);
        }

        registeredWorlds.add(worldName);
    }
}
