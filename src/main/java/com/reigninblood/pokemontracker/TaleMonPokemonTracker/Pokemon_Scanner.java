package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Pokemon_Scanner {

    private Pokemon_Scanner() {}

    public static List<Pokemon_Hit> scan(
            Store<EntityStore> store,
            Vector3d playerPos,
            int radius,
            Set<String> pokemonRoles
    ) {
        List<Pokemon_Hit> hits = new ArrayList<>();

        Archetype npcQuery = Archetype.of(NPCEntity.getComponentType());

        store.forEachChunk(npcQuery, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb) -> {
            int size = chunk.size();

            for (int i = 0; i < size; i++) {
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null) continue;

                String role = npc.getRoleName();
                if (role == null || role.isEmpty()) continue;

                if (!pokemonRoles.contains(role)) continue;

                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef == null || !entityRef.isValid()) continue;

                TransformComponent tc = store.getComponent(entityRef, TransformComponent.getComponentType());
                if (tc == null || tc.getPosition() == null) continue;

                Vector3d pos = tc.getPosition();
                double dist = distance(playerPos, pos);
                if (dist > radius) continue;

                int eid = entityRef.getIndex();
                hits.add(new Pokemon_Hit(role, pos.x, pos.y, pos.z, dist, eid));
            }
        });

        return hits;
    }

    private static double distance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
