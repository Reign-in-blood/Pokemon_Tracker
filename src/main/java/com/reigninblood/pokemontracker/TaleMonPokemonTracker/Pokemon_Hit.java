package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

public class Pokemon_Hit {
    public final String role;
    public final double x, y, z;
    public final double distance;
    public final int entityIndex;

    public Pokemon_Hit(String role, double x, double y, double z, double distance, int entityIndex) {
        this.role = role;
        this.x = x;
        this.y = y;
        this.z = z;
        this.distance = distance;
        this.entityIndex = entityIndex;
    }
}
