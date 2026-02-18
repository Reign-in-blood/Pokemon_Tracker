package com.reigninblood.pokemontracker.TaleMonPokemonTracker;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Pokemon_Marker_Provider {

    private static final Logger LOGGER = Logger.getLogger("TaleMonPokemonTracker");

    private static final String MARKER_PREFIX = "TaleMonPokemonTracker-";

    private static final String DEFAULT_ICON = "Pokemon.png";

    private static final long AUTO_SCAN_INTERVAL_MS = 600;

    private static final int LOG_EVERY_N_CALLS = 150;
    private static final AtomicInteger UPDATE_CALLS = new AtomicInteger(0);

    private final Pokemon_Tracker_Cache cache;
    private final Map<String, Long> lastAutoScan = new ConcurrentHashMap<>();

    private boolean dumpedOnce = false;

    public Pokemon_Marker_Provider(Pokemon_Tracker_Cache cache) {
        this.cache = cache;
    }

    public WorldMapManager.MarkerProvider asMarkerProvider() {
        return (world, viewingPlayer, collector) -> update(world, viewingPlayer, collector);
    }

    public void update(World world, Player viewingPlayer, Object collector) {
        int call = UPDATE_CALLS.incrementAndGet();
        boolean shouldLog = (call % LOG_EVERY_N_CALLS == 1);

        try {
            if (viewingPlayer == null || collector == null) return;

            String playerKey = String.valueOf(viewingPlayer.getDisplayName());

            maybeAutoScan(world, viewingPlayer, playerKey);

            List<Pokemon_Hit> hits = cache.getForPlayer(playerKey);

            if (!dumpedOnce) {
                dumpedOnce = true;
            }

            if (hits.isEmpty()) return;

            for (Pokemon_Hit h : hits) {
                String id = MARKER_PREFIX + playerKey + "-" + h.entityIndex;
                Vector3d pos = new Vector3d(h.x, h.y, h.z);
                String label = h.role + " (" + String.format(Locale.US, "%.0f", h.distance) + "m)";

                String icon = iconForRoleAuto(h.role);

                MapMarker marker = buildMarker(id, label, icon, pos);
                if (marker == null) continue;

                pushMarker(collector, marker);
            }

        } catch (Throwable t) {
            if (shouldLog) {
                LOGGER.warning("[TaleMonPokemonTracker] MarkerProvider.update error: " + t.getMessage());
            }
        }
    }

    private String iconForRoleAuto(String role) {
        if (role == null) return DEFAULT_ICON;

        String safe = role.trim();
        if (safe.isEmpty()) return DEFAULT_ICON;

        safe = safe.replace(" ", "_").replaceAll("[^A-Za-z0-9_\\-]", "");
        if (safe.isEmpty()) return DEFAULT_ICON;

        return safe + ".png";
    }

    private void maybeAutoScan(World world, Player viewingPlayer, String playerKey) {
        long now = System.currentTimeMillis();
        long last = lastAutoScan.getOrDefault(playerKey, 0L);
        if (now - last < AUTO_SCAN_INTERVAL_MS) return;

        lastAutoScan.put(playerKey, now);

        world.execute(() -> {
            try {
                EntityStore entityStore = world.getEntityStore();
                if (entityStore == null) return;

                Store<EntityStore> store = entityStore.getStore();
                if (store == null) return;

                Ref<EntityStore> playerRef = viewingPlayer.getReference();
                if (playerRef == null || !playerRef.isValid()) return;

                TransformComponent tc = store.getComponent(playerRef, TransformComponent.getComponentType());
                if (tc == null || tc.getPosition() == null) return;

                Vector3d playerPos = tc.getPosition();

                List<Pokemon_Hit> hits = Pokemon_Scanner.scan(
                        store,
                        playerPos,
                        Command_Tracker.RADIUS,
                        Command_Tracker.POKEMON_ROLES
                );

                cache.updateForPlayer(playerKey, hits);

            } catch (Throwable ignored) { }
        });
    }

    private MapMarker buildMarker(String id, String label, String icon, Vector3d pos) {
        try {
            MapMarker marker = tryConstructMapMarker(id, label, icon);
            if (marker == null) return null;

            forceStringField(marker, "id", id);
            applyMarkerText(marker, label);
            forceStringField(marker, "markerImage", icon);

            boolean ok = forceProtocolTransform(marker, pos);
            if (!ok && dumpedOnce) {
            }

            return marker;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void applyMarkerText(MapMarker marker, String label) {
        marker.name = makeFormattedMessage(label);
        marker.customName = label;
    }

    private FormattedMessage makeFormattedMessage(String text) {
        if (text == null) return null;

        try {
            for (Method m : FormattedMessage.class.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getReturnType() != FormattedMessage.class) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == String.class) {
                    String n = m.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("plain") || n.contains("text") || n.contains("string") || n.contains("message") || n.contains("from")) {
                        Object obj = m.invoke(null, text);
                        if (obj instanceof FormattedMessage) return (FormattedMessage) obj;
                    }
                }
            }
        } catch (Throwable ignored) { }

        try {
            Constructor<FormattedMessage> c = FormattedMessage.class.getDeclaredConstructor(String.class);
            c.setAccessible(true);
            return c.newInstance(text);
        } catch (Throwable ignored) { }

        try {
            Constructor<?>[] constructors = FormattedMessage.class.getConstructors();
            for (Constructor<?> c : constructors) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0] == String.class) {
                    Object obj = c.newInstance(text);
                    if (obj instanceof FormattedMessage) return (FormattedMessage) obj;
                }
            }
        } catch (Throwable ignored) { }

        return null;
    }

    private void pushMarker(Object collector, MapMarker marker) {
        if (collector == null || marker == null) return;

        try {
            Method directCollect = collector.getClass().getMethod("collect", MapMarker.class);
            directCollect.invoke(collector, marker);
            return;
        } catch (Throwable ignored) { }

        try {
            Method directAdd = collector.getClass().getMethod("add", MapMarker.class);
            directAdd.invoke(collector, marker);
            return;
        } catch (Throwable ignored) { }

        try {
            for (Method m : collector.getClass().getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1) continue;
                if (!params[0].isAssignableFrom(MapMarker.class) && !MapMarker.class.isAssignableFrom(params[0])) continue;

                m.setAccessible(true);
                m.invoke(collector, marker);
                return;
            }
        } catch (Throwable ignored) { }
    }

    private MapMarker tryConstructMapMarker(String id, String label, String icon) {
        try {
            for (Constructor<?> c : MapMarker.class.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                Object[] args = new Object[p.length];

                boolean ok = true;
                int strCount = 0;

                for (int i = 0; i < p.length; i++) {
                    Class<?> t = p[i];

                    if (t == String.class) {
                        if (strCount == 0) args[i] = id;
                        else if (strCount == 1) args[i] = label;
                        else if (strCount == 2) args[i] = icon;
                        else args[i] = "";
                        strCount++;
                    } else if (t == int.class || t == Integer.class) {
                        args[i] = 0;
                    } else if (t == float.class || t == Float.class) {
                        args[i] = 0f;
                    } else if (t == double.class || t == Double.class) {
                        args[i] = 0d;
                    } else if (t == boolean.class || t == Boolean.class) {
                        args[i] = true;
                    } else if (t.isArray()) {
                        args[i] = null;
                    } else {
                        ok = false;
                        break;
                    }
                }

                if (!ok) continue;

                Object obj = c.newInstance(args);
                if (obj instanceof MapMarker) return (MapMarker) obj;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private boolean forceProtocolTransform(MapMarker marker, Vector3d pos) {
        try {
            Field tf = findFieldDeep(marker.getClass(), "transform");
            if (tf == null) return false;
            tf.setAccessible(true);

            Object tr = tf.get(marker);
            if (tr == null) {
                Class<?> transformClass = tf.getType();
                tr = createEmptyObject(transformClass);
                if (tr == null) return false;
                tf.set(marker, tr);
            }

            boolean posOk = forceTransformPosition(tr, pos);
            boolean oriOk = forceTransformOrientation(tr);

            return posOk && oriOk;

        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean forceTransformPosition(Object tr, Vector3d pos) {
        try {
            Method setter = findMethodAny(tr.getClass(), "setPosition");
            if (setter != null && setter.getParameterCount() == 1) {
                Object vec = makeVec3(setter.getParameterTypes()[0], pos.x, pos.y, pos.z);
                if (vec != null) {
                    setter.setAccessible(true);
                    setter.invoke(tr, vec);
                }
            }

            Field fPos = findFieldDeep(tr.getClass(), "position");
            if (fPos != null) {
                fPos.setAccessible(true);
                Object cur = fPos.get(tr);
                if (cur == null) {
                    Object vec = makeVec3(fPos.getType(), pos.x, pos.y, pos.z);
                    if (vec != null) {
                        fPos.set(tr, vec);
                        cur = vec;
                    }
                }
                return cur != null;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private boolean forceTransformOrientation(Object tr) {
        try {
            Field fOri = findFieldDeep(tr.getClass(), "orientation");
            if (fOri == null) fOri = findFieldDeep(tr.getClass(), "rotation");
            if (fOri == null) return false;

            fOri.setAccessible(true);
            Object ori = fOri.get(tr);

            if (ori == null) {
                ori = createEmptyObject(fOri.getType());
                if (ori == null) return false;
                fOri.set(tr, ori);
            }

            forceNumberField(ori, "yaw", 0f);
            forceNumberField(ori, "pitch", 0f);
            forceNumberField(ori, "roll", 0f);

            forceNumberField(ori, "x", 0f);
            forceNumberField(ori, "y", 0f);
            forceNumberField(ori, "z", 0f);
            forceNumberField(ori, "w", 1f);

            return true;

        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object createEmptyObject(Class<?> clazz) {
        try {
            try {
                Constructor<?> noArg = clazz.getDeclaredConstructor();
                noArg.setAccessible(true);
                return noArg.newInstance();
            } catch (Throwable ignored) { }

            for (Constructor<?> c : clazz.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                Object[] args = new Object[p.length];

                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    Class<?> t = p[i];
                    if (t == int.class || t == Integer.class) args[i] = 0;
                    else if (t == float.class || t == Float.class) args[i] = 0f;
                    else if (t == double.class || t == Double.class) args[i] = 0d;
                    else if (t == boolean.class || t == Boolean.class) args[i] = true;
                    else if (t == String.class) args[i] = "";
                    else { ok = false; break; }
                }

                if (!ok) continue;
                return c.newInstance(args);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private Object makeVec3(Class<?> vecClass, double x, double y, double z) {
        try {
            for (Constructor<?> c : vecClass.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == double.class) {
                    return c.newInstance(x, y, z);
                }
            }
            for (Constructor<?> c : vecClass.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 3 && p[0] == float.class && p[1] == float.class && p[2] == float.class) {
                    return c.newInstance((float) x, (float) y, (float) z);
                }
            }
            for (Method m : vecClass.getMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (m.getReturnType() != vecClass) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == double.class) {
                    return m.invoke(null, x, y, z);
                }
                if (p.length == 3 && p[0] == float.class && p[1] == float.class && p[2] == float.class) {
                    return m.invoke(null, (float) x, (float) y, (float) z);
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private void forceStringField(Object obj, String fieldName, String value) {
        try {
            Field f = findFieldDeep(obj.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Throwable ignored) { }
    }

    private void forceNumberField(Object obj, String fieldName, float value) {
        try {
            Field f = findFieldDeep(obj.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);

            Class<?> t = f.getType();
            if (t == float.class) f.setFloat(obj, value);
            else if (t == Float.class) f.set(obj, value);
            else if (t == double.class) f.setDouble(obj, (double) value);
            else if (t == Double.class) f.set(obj, (double) value);
            else if (t == int.class) f.setInt(obj, (int) value);
            else if (t == Integer.class) f.set(obj, (int) value);

        } catch (Throwable ignored) { }
    }

    private static Field findFieldDeep(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (Throwable ignored) { }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethodAny(Class<?> c, String name) {
        try {
            for (Method m : c.getMethods()) {
                if (m.getName().equals(name)) return m;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name)) return m;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
