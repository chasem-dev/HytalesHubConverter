package dev.chasem.hg.hubconverter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.chasem.hg.hubconverter.command.HytalesHubCommand;
import dev.chasem.hg.hubconverter.config.HytalesHubConfig;
import dev.chasem.hg.hubconverter.io.HytalesHubPaths;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;

public class HytalesHubConverterPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HytalesHubConverterPlugin instance;

    private final Config<HytalesHubConfig> config =
            this.withConfig("HytalesHubConverter", HytalesHubConfig.CODEC);

    public HytalesHubConverterPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("Loaded %s version %s", this.getName(), this.getManifest().getVersion().toString());
    }

    public static HytalesHubConverterPlugin get() {
        return instance;
    }

    public Config<HytalesHubConfig> getConfigHandle() {
        return config;
    }

    @Override
    protected void setup() {
        HytalesHubPaths.initialize(config, this);
        ensureConfigFolders();
        getCommandRegistry().registerCommand(new HytalesHubCommand(config));
        LOGGER.atInfo().log("Registered /hytaleshub commands");
    }

    private void ensureConfigFolders() {
        HytalesHubConfig cfg = config.get();
        createDir(HytalesHubPaths.getConfigDir());
        createDir(HytalesHubPaths.getMcRegionsDir(cfg));
        createDir(HytalesHubPaths.getHytaleRegionsDir(cfg));
    }

    private void createDir(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create directory %s: %s", dir, e.getMessage());
        }
    }
}
