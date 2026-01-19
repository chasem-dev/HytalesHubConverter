package dev.chasem.hg.hubconverter.mapping;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BlockOverrides {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DEFAULT_RESOURCE = "/default-block-overrides.csv";

    private BlockOverrides() {
    }

    public static Map<String, String> load(Path overridesFile) {
        MappingFileBootstrap.copyResourceIfMissing(DEFAULT_RESOURCE, overridesFile);

        Map<String, String> overrides = new LinkedHashMap<>();
        if (!Files.exists(overridesFile)) {
            return overrides;
        }

        try (BufferedReader reader = Files.newBufferedReader(overridesFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return overrides;
            }

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length < 2) {
                    continue;
                }
                String mc = parts[0].trim();
                String hytale = parts[1].trim();
                if (!mc.isEmpty()) {
                    overrides.put(mc, hytale);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read overrides file %s: %s", overridesFile, e.getMessage());
        }

        return overrides;
    }
}
