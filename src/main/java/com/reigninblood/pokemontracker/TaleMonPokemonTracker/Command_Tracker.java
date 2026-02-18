package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class Command_Tracker extends CommandBase {

    private static final Logger LOGGER = Logger.getLogger("TaleMonPokemonTracker");

    public static final int RADIUS = 200;

    public static final Set<String> POKEMON_ROLES = Set.of("Bulbasaur", "Ponyta", "Magikarp", "Gastly");

    private final Pokemon_Tracker_Cache cache;

    public Command_Tracker(Pokemon_Tracker_Cache cache) {
        super("tracker", "Scan Pokémon autour du joueur (logs + cache).");
        this.setPermissionGroup(GameMode.Adventure);
        this.cache = cache;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(Message.raw("Commande réservée au joueur (pas console)."));
            return;
        }

        var player = (com.hypixel.hytale.server.core.entity.entities.Player) ctx.sender();
        World world = player.getWorld();
        if (world == null) {
            ctx.sendMessage(Message.raw("Erreur: world null (voir logs)."));
            return;
        }

        world.execute(() -> scanOnce(player, world));

        ctx.sendMessage(Message.raw("OK: scan fait (boussole + map)"));
    }

    public void scanOnce(com.hypixel.hytale.server.core.entity.entities.Player player, World world) {
        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) return;

        Store<EntityStore> store = entityStore.getStore();
        if (store == null) return;

        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null || playerTc.getPosition() == null) return;

        Vector3d playerPos = playerTc.getPosition();
        List<Pokemon_Hit> hits = Pokemon_Scanner.scan(store, playerPos, RADIUS, POKEMON_ROLES);

        String playerKey = String.valueOf(player.getDisplayName());
        cache.updateForPlayer(playerKey, hits);

        LOGGER.info("[TaleMonPokemonTracker] cache updated | player=" + playerKey
                + " world=" + world.getName()
                + " radius=" + RADIUS
                + " pokemonFound=" + hits.size());
    }
}
