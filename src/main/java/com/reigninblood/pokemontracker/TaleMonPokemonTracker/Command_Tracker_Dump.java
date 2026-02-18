package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Logger;

public class Command_Tracker_Dump extends CommandBase {

    private static final Logger LOGGER = Logger.getLogger("TaleMonPokemonTracker");
    private final Pokemon_Tracker_Cache cache;

    public Command_Tracker_Dump(Pokemon_Tracker_Cache cache) {
        super("trackerdump", "Affiche le contenu du cache Pokémon (logs).");
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

        world.execute(() -> dump(player, world));

        ctx.sendMessage(Message.raw("OK: dump envoyé dans les logs."));
    }

    private void dump(com.hypixel.hytale.server.core.entity.entities.Player player, World world) {

        String playerKey = String.valueOf(player.getDisplayName());

        List<Pokemon_Hit> hits = cache.getForPlayer(playerKey);

        LOGGER.info("[TaleMonPokemonTracker] ===== DUMP START | player=" + playerKey
                + " world=" + world.getName()
                + " count=" + hits.size()
                + " =====");

        for (int i = 0; i < hits.size(); i++) {
            Pokemon_Hit h = hits.get(i);
            LOGGER.info("[TaleMonPokemonTracker] HIT|i=" + i
                    + "|role=" + h.role
                    + "|dist=" + String.format("%.1f", h.distance)
                    + "|pos=" + String.format("%.1f", h.x) + "," + String.format("%.1f", h.y) + "," + String.format("%.1f", h.z)
                    + "|eid=" + h.entityIndex);
        }

        LOGGER.info("[TaleMonPokemonTracker] ===== DUMP END =====");
    }
}
