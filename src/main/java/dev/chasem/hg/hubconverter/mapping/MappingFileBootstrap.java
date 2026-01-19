package dev.chasem.hg.hubconverter.mapping;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MappingFileBootstrap {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private MappingFileBootstrap() {
    }

    public static void copyResourceIfMissing(String resourceName, Path destination) {
        if (Files.exists(destination)) {
            return;
        }
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create directory %s: %s", destination.getParent(), e.getMessage());
            return;
        }

        try (InputStream input = MappingFileBootstrap.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                LOGGER.atWarning().log("[HytalesHub] Resource not found: %s", resourceName);
                return;
            }
            Files.copy(input, destination);
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to copy %s to %s: %s", resourceName, destination, e.getMessage());
        }
    }
}
