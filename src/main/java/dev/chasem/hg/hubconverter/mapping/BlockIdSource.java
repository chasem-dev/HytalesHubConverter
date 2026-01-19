package dev.chasem.hg.hubconverter.mapping;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockIdSource {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DEFAULT_RESOURCE = "/default-block-ids.txt";

    private BlockIdSource() {
    }

    public static List<String> loadBlockIds(boolean useLiveRegistry, World world, Path blockIdsFile) {
        List<String> liveIds = List.of();
        if (useLiveRegistry) {
            liveIds = tryLoadFromRegistry(world);
            if (!liveIds.isEmpty()) {
                return liveIds;
            }
        }

        MappingFileBootstrap.copyResourceIfMissing(DEFAULT_RESOURCE, blockIdsFile);
        return loadFromFile(blockIdsFile);
    }

    private static List<String> tryLoadFromRegistry(World world) {
        if (world == null) {
            return List.of();
        }

        Object registry = invokeFirst(world,
                "getBlockRegistry",
                "getBlockTypeRegistry",
                "getBlockTypes",
                "getBlocks",
                "getBlockManager");
        if (registry == null) {
            return List.of();
        }

        Object ids = invokeFirst(registry,
                "getIds",
                "getBlockIds",
                "getKeys",
                "keySet",
                "getRegisteredBlocks",
                "getBlocks",
                "getEntries",
                "values");

        return extractIds(ids);
    }

    private static Object invokeFirst(Object target, String... methods) {
        for (String methodName : methods) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (Exception ignored) {
                continue;
            }
        }
        return null;
    }

    private static List<String> extractIds(Object source) {
        if (source == null) {
            return List.of();
        }
        Set<String> ids = new HashSet<>();
        if (source instanceof Collection<?> collection) {
            for (Object entry : collection) {
                addId(ids, entry);
            }
        } else if (source instanceof Map<?, ?> map) {
            for (Object entry : map.keySet()) {
                addId(ids, entry);
            }
            for (Object entry : map.values()) {
                addId(ids, entry);
            }
        } else {
            addId(ids, source);
        }

        return new ArrayList<>(ids);
    }

    private static void addId(Set<String> ids, Object entry) {
        if (entry == null) {
            return;
        }
        if (entry instanceof String str) {
            if (!str.isBlank()) {
                ids.add(str);
            }
            return;
        }

        Object value = invokeFirst(entry, "getId", "getName", "getRegistryId", "getIdentifier");
        if (value instanceof String str && !str.isBlank()) {
            ids.add(str);
            return;
        }

        String str = entry.toString();
        if (str != null && !str.isBlank() && !str.contains("@")) {
            ids.add(str);
        }
    }

    private static List<String> loadFromFile(Path file) {
        List<String> ids = new ArrayList<>();
        if (!Files.exists(file)) {
            LOGGER.atWarning().log("[HytalesHub] Block id file missing: %s", file);
            return ids;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read block ids file %s: %s", file, e.getMessage());
        }

        return ids;
    }
}
