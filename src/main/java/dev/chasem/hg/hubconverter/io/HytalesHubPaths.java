package dev.chasem.hg.hubconverter.io;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;
import dev.chasem.hg.hubconverter.HytalesHubConverterPlugin;
import dev.chasem.hg.hubconverter.config.HytalesHubConfig;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class HytalesHubPaths {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static Path configDir;

    private HytalesHubPaths() {
    }

    public static synchronized void initialize(Config<HytalesHubConfig> config, HytalesHubConverterPlugin plugin) {
        if (configDir != null) {
            return;
        }

        configDir = resolveConfigDir(config, plugin);
        try {
            Files.createDirectories(configDir);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create config dir '%s': %s", configDir, e.getMessage());
        }
    }

    public static Path getConfigDir() {
        return configDir != null ? configDir : Paths.get("config");
    }

    public static Path getMcRegionsDir(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getMcRegionsFolder());
    }

    public static Path getHytaleRegionsDir(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getHytaleRegionsFolder());
    }

    public static Path getBlockMappingFile(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getBlockMappingFile());
    }

    public static Path getUnmappedBlocksFile(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getUnmappedBlocksFile());
    }

    public static Path getBlockOverridesFile(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getBlockOverridesFile());
    }

    public static Path getBlockIdsFile(HytalesHubConfig config) {
        return getConfigDir().resolve(config.getBlockIdsFile());
    }

    private static Path resolveConfigDir(Config<HytalesHubConfig> config, HytalesHubConverterPlugin plugin) {
        Path configPath = resolvePathFromConfig(config);
        if (configPath != null) {
            return ensureConverterSubdir(configPath);
        }

        Path pluginPath = resolvePathFromPlugin(plugin);
        if (pluginPath != null) {
            return ensureConverterSubdir(pluginPath);
        }

        return Paths.get("config").resolve("HytalesHubConverter");
    }

    private static Path resolvePathFromConfig(Config<HytalesHubConfig> config) {
        String[] methods = {
                "getPath",
                "getFile",
                "getConfigFile",
                "getConfigPath",
                "getLocation"
        };

        for (String methodName : methods) {
            Path path = resolvePathFromMethod(config, methodName);
            if (path != null) {
                return path;
            }
        }

        return null;
    }

    private static Path resolvePathFromPlugin(HytalesHubConverterPlugin plugin) {
        if (plugin == null) {
            return null;
        }

        String[] methods = {
                "getConfigFolder",
                "getConfigDirectory",
                "getDataFolder",
                "getDataDirectory",
                "getDataPath"
        };

        for (String methodName : methods) {
            Path path = resolvePathFromMethod(plugin, methodName);
            if (path != null) {
                return path;
            }
        }

        return null;
    }

    private static Path resolvePathFromMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            Path path = coercePath(value);
            if (path == null) {
                return null;
            }
            if (Files.isDirectory(path)) {
                return path;
            }
            Path parent = path.getParent();
            return parent != null ? parent : path;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Path ensureConverterSubdir(Path baseDir) {
        Path normalized = baseDir;
        if (!"HytalesHubConverter".equals(baseDir.getFileName() == null ? "" : baseDir.getFileName().toString())) {
            normalized = baseDir.resolve("HytalesHubConverter");
        }
        return normalized;
    }

    private static Path coercePath(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Path path) {
            return path;
        }
        if (value instanceof File file) {
            return file.toPath();
        }
        if (value instanceof String str && !str.isBlank()) {
            return Paths.get(str);
        }
        return null;
    }
}
