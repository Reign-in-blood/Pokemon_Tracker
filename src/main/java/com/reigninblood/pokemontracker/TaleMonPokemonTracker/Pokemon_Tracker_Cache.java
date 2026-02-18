package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Pokemon_Tracker_Cache {

    private final Map<String, List<Pokemon_Hit>> perPlayer = new ConcurrentHashMap<>();

    public void updateForPlayer(String playerKey, List<Pokemon_Hit> hits) {
        perPlayer.put(playerKey, hits);
    }

    public List<Pokemon_Hit> getForPlayer(String playerKey) {
        return perPlayer.getOrDefault(playerKey, List.of());
    }

    public void clearPlayer(String playerKey) {
        perPlayer.remove(playerKey);
    }

    public void clearAll() {
        perPlayer.clear();
    }
}
