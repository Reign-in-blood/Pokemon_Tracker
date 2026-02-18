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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public class Command_Tracker extends CommandBase {

    private static final Logger LOGGER = Logger.getLogger("TaleMonPokemonTracker");

    public static final int RADIUS = 200;

    private static final Set<String> DEFAULT_POKEMON_ROLES = Set.of("Bulbasaur", "Ponyta", "Magikarp", "Gastly");

    // Update Pokémon roles by editing src/main/resources/pokemon_roles.txt (no Java code change needed).
    public static final Set<String> POKEMON_ROLES = loadPokemonRoles();

    private final Pokemon_Tracker_Cache cache;


    private static Set<String> loadPokemonRoles() {
        Set<String> fromJson = loadPokemonRolesFromJsonFile();
        if (!fromJson.isEmpty()) return Collections.unmodifiableSet(fromJson);

        Set<String> fromResource = loadPokemonRolesFromResource();
        if (!fromResource.isEmpty()) return Collections.unmodifiableSet(fromResource);

        LOGGER.warning("[TaleMonPokemonTracker] pokemon_roles.txt missing/empty and Pokemon.json not found; falling back to default role list.");
        return Collections.unmodifiableSet(new LinkedHashSet<>(DEFAULT_POKEMON_ROLES));
    }

    private static Set<String> loadPokemonRolesFromJsonFile() {
        List<Path> candidates = Arrays.asList(
                Paths.get("Server", "NPC", "Groups", "Pokemon.json"),
                Paths.get("run", "Server", "NPC", "Groups", "Pokemon.json"),
                Paths.get("NPC", "Groups", "Pokemon.json")
        );

        for (Path p : candidates) {
            try {
                if (!Files.isRegularFile(p)) continue;

                String json = Files.readString(p, StandardCharsets.UTF_8);
                Set<String> roles = extractIncludeRoles(json);
                if (!roles.isEmpty()) {
                    LOGGER.info("[TaleMonPokemonTracker] Loaded " + roles.size() + " pokemon roles from " + p);
                    return roles;
                }
            } catch (Throwable t) {
                LOGGER.warning("[TaleMonPokemonTracker] Failed reading " + p + " : " + t.getMessage());
            }
        }

        return Collections.emptySet();
    }

    private static Set<String> extractIncludeRoles(String json) {
        if (json == null || json.isBlank()) return Collections.emptySet();

        Matcher block = Pattern.compile("\"IncludeRoles\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        LinkedHashSet<String> roles = new LinkedHashSet<>();

        while (block.find()) {
            String arr = block.group(1);
            Matcher item = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"").matcher(arr);
            while (item.find()) {
                String role = item.group(1).replace("\\\"", "\"").trim();
                if (!role.isEmpty()) roles.add(role);
            }
        }

        return roles;
    }

    private static Set<String> loadPokemonRolesFromResource() {
        LinkedHashSet<String> roles = new LinkedHashSet<>();

        try (InputStream in = Command_Tracker.class.getResourceAsStream("/pokemon_roles.txt")) {
            if (in == null) return Collections.emptySet();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String role = line.trim();
                    if (role.isEmpty() || role.startsWith("#")) continue;
                    roles.add(role);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("[TaleMonPokemonTracker] Failed to load /pokemon_roles.txt: " + e.getMessage());
            return Collections.emptySet();
        }

        return roles;
    }

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
