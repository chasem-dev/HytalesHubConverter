package dev.chasem.hg.hubconverter.mapping;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.chasem.hg.hubconverter.config.HytalesHubConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BlockMapper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String UNMAPPED_VALUE = "UNMAPPED";
    private static final Set<String> BANNED_TARGETS = Set.of("Wood_Sticks");

    private static final Map<String, List<String>> TOKEN_SYNONYMS = Map.ofEntries(
            Map.entry("log", List.of("trunk")),
            Map.entry("planks", List.of("planks")),
            Map.entry("slab", List.of("half")),
            Map.entry("stairs", List.of("stairs")),
            Map.entry("wall", List.of("wall")),
            Map.entry("fence", List.of("fence")),
            Map.entry("gate", List.of("gate")),
            Map.entry("leaves", List.of("leaves")),
            Map.entry("glass", List.of("glass")),
            Map.entry("wool", List.of("wool")),
            Map.entry("sand", List.of("sand")),
            Map.entry("gravel", List.of("gravel")),
            Map.entry("stone", List.of("stone")),
            Map.entry("cobblestone", List.of("stone", "cobble")),
            Map.entry("brick", List.of("brick")),
            Map.entry("bricks", List.of("brick")),
            Map.entry("mossy", List.of("mossy"))
    );

    public MapResult mapBlocks(Path mcRegionsDir,
                               Path mappingFile,
                               Path unmappedFile,
                               Path overridesFile,
                               Path blockIdsFile,
                               HytalesHubConfig config,
                               World world) {
        Map<String, Integer> mcCounts = loadMcCounts(mcRegionsDir);
        if (mcCounts.isEmpty()) {
            return new MapResult(0, 0, 0, 0);
        }

        Map<String, String> overrides = BlockOverrides.load(overridesFile);
        List<String> hytaleBlocks = BlockIdSource.loadBlockIds(config.isUseLiveBlockRegistry(), world, blockIdsFile)
                .stream()
                .filter(id -> !BANNED_TARGETS.contains(id))
                .toList();
        Set<String> hytaleBlockSet = new HashSet<>(hytaleBlocks);
        Map<String, List<String>> hytaleTokensMap = new HashMap<>();
        for (String block : hytaleBlocks) {
            hytaleTokensMap.put(block, hytaleTokens(block));
        }

        List<MappingRow> rows = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();

        List<String> mcBlocks = new ArrayList<>(mcCounts.keySet());
        mcBlocks.sort(String::compareToIgnoreCase);

        for (String mcBlock : mcBlocks) {
            if (mcBlock == null || mcBlock.isBlank()) {
                continue;
            }
            if (overrides.containsKey(mcBlock)) {
                String hytaleBlock = overrides.get(mcBlock);
                rows.add(new MappingRow(mcBlock, hytaleBlock, 1.0f, "manual"));
                if (hytaleBlock == null || hytaleBlock.isBlank() || !hytaleBlockSet.contains(hytaleBlock)) {
                    unmatched.add(mcBlock);
                }
                continue;
            }

            List<String> tokens = mcTokens(mcBlock);
            float bestScore = 0.0f;
            String bestMatch = "";
            for (Map.Entry<String, List<String>> entry : hytaleTokensMap.entrySet()) {
                float score = scoreMatch(tokens, entry.getValue());
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = entry.getKey();
                }
            }

            if (bestScore >= config.getMapMinScore()) {
                rows.add(new MappingRow(mcBlock, bestMatch, roundScore(bestScore), "heuristic"));
            } else {
                rows.add(new MappingRow(mcBlock, "", roundScore(bestScore), UNMAPPED_VALUE.toLowerCase(Locale.ROOT)));
                unmatched.add(mcBlock);
            }
        }

        writeMapping(mappingFile, rows);
        writeUnmatched(unmappedFile, unmatched);
        return new MapResult(mcCounts.size(), rows.size(), unmatched.size(), hytaleBlocks.size());
    }

    private Map<String, Integer> loadMcCounts(Path mcRegionsDir) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (mcRegionsDir == null || !Files.exists(mcRegionsDir)) {
            return counts;
        }

        List<Path> csvFiles = new ArrayList<>();
        try (var stream = Files.list(mcRegionsDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(csvFiles::add);
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to list CSVs in %s: %s", mcRegionsDir, e.getMessage());
            return counts;
        }

        for (Path csv : csvFiles) {
            loadBlocksFromCsv(csv, counts);
        }

        return counts;
    }

    private void loadBlocksFromCsv(Path csvFile, Map<String, Integer> counts) {
        try (BufferedReader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = headerLine.split(",", -1);
            int blockIdx = findColumnIndex(header, "block");
            if (blockIdx == -1) {
                blockIdx = 3;
                if (header.length > blockIdx) {
                    String block = mcBaseId(header[blockIdx]);
                    if (!block.isBlank()) {
                        counts.merge(block, 1, Integer::sum);
                    }
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                if (parts.length <= blockIdx) {
                    continue;
                }
                String block = mcBaseId(parts[blockIdx]);
                if (!block.isBlank()) {
                    counts.merge(block, 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read %s: %s", csvFile.getFileName(), e.getMessage());
        }
    }

    private int findColumnIndex(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(header[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private String mcBaseId(String blockStr) {
        if (blockStr == null) {
            return "";
        }
        String trimmed = blockStr.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("legacy:")) {
            return trimmed;
        }
        int bracket = trimmed.indexOf('[');
        if (bracket >= 0) {
            trimmed = trimmed.substring(0, bracket);
        }
        if (trimmed.contains(":")) {
            return trimmed;
        }
        return "minecraft:" + trimmed;
    }

    private List<String> mcTokens(String blockId) {
        if (blockId.startsWith("legacy:")) {
            return List.of("legacy");
        }
        String name = blockId.contains(":") ? blockId.split(":", 2)[1] : blockId;
        List<String> tokens = TokenUtils.normalizeTokens(TokenUtils.tokenize(name), TOKEN_SYNONYMS);
        if (name.endsWith("_wood")) {
            tokens = new ArrayList<>(tokens);
            tokens.add("trunk");
        }
        return tokens;
    }

    private List<String> hytaleTokens(String blockId) {
        return TokenUtils.normalizeTokens(TokenUtils.tokenize(blockId), TOKEN_SYNONYMS);
    }

    private float scoreMatch(List<String> tokens, List<String> candidateTokens) {
        if (tokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0f;
        }
        Set<String> tSet = new HashSet<>(tokens);
        Set<String> cSet = new HashSet<>(candidateTokens);
        int overlap = 0;
        for (String token : tSet) {
            if (cSet.contains(token)) {
                overlap++;
            }
        }
        if (overlap == 0) {
            return 0.0f;
        }
        return (2.0f * overlap) / (tSet.size() + cSet.size());
    }

    private float roundScore(float score) {
        return Math.round(score * 1000f) / 1000f;
    }

    private void writeMapping(Path mappingFile, List<MappingRow> rows) {
        try {
            Files.createDirectories(mappingFile.getParent());
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create directory for mapping file: %s", e.getMessage());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(mappingFile, StandardCharsets.UTF_8)) {
            writer.write("minecraft_block,hytale_block,score,source");
            writer.newLine();
            for (MappingRow row : rows) {
                writer.write(row.minecraftBlock + "," + (row.hytaleBlock == null ? "" : row.hytaleBlock)
                        + "," + row.score + "," + row.source);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to write mapping file %s: %s", mappingFile, e.getMessage());
        }
    }

    private void writeUnmatched(Path unmatchedFile, List<String> unmatched) {
        try {
            Files.createDirectories(unmatchedFile.getParent());
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create directory for unmatched file: %s", e.getMessage());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(unmatchedFile, StandardCharsets.UTF_8)) {
            writer.write("minecraft_block");
            writer.newLine();
            for (String block : unmatched) {
                writer.write(block);
                writer.newLine();
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to write unmatched file %s: %s", unmatchedFile, e.getMessage());
        }
    }

    public record MapResult(int uniqueMcBlocks, int mappingRows, int unmatchedCount, int hytaleBlockCount) {
    }

    private static final class MappingRow {
        private final String minecraftBlock;
        private final String hytaleBlock;
        private final float score;
        private final String source;

        private MappingRow(String minecraftBlock, String hytaleBlock, float score, String source) {
            this.minecraftBlock = minecraftBlock;
            this.hytaleBlock = hytaleBlock;
            this.score = score;
            this.source = source;
        }
    }
}
