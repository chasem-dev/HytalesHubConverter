package dev.chasem.hg.hubconverter.convert;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionCsvConverter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String UNMAPPED_VALUE = "UNMAPPED";
    private static final String OUTPUT_BLOCK_COLUMN_NAME = "hytale_block";
    private static final String OUTPUT_PREFIX = "hytale-region-";

    public ConvertSummary convertAll(Path inputDir,
                                     Path mappingFile,
                                     Path outputDir,
                                     int yOffset,
                                     String defaultUnmappedBlock) {
        if (inputDir == null || !Files.exists(inputDir)) {
            return new ConvertSummary(0, 0, 0, 1);
        }
        if (mappingFile == null || !Files.exists(mappingFile)) {
            return new ConvertSummary(0, 0, 0, 1);
        }

        Map<String, String> mapping = loadMapping(mappingFile);
        if (mapping.isEmpty()) {
            return new ConvertSummary(0, 0, 0, 1);
        }

        List<Path> csvFiles = new ArrayList<>();
        try (var stream = Files.list(inputDir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(csvFiles::add);
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to list CSVs in %s: %s", inputDir, e.getMessage());
            return new ConvertSummary(0, 0, 0, 1);
        }

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create output dir %s: %s", outputDir, e.getMessage());
        }

        int totalFiles = 0;
        long totalRows = 0;
        long totalUnmapped = 0;
        int errors = 0;

        for (Path csvFile : csvFiles) {
            Path outputPath = outputDir.resolve(OUTPUT_PREFIX + csvFile.getFileName().toString());
            FileResult result = convertFile(csvFile, outputPath, mapping, yOffset, defaultUnmappedBlock);
            totalFiles++;
            totalRows += result.rows;
            totalUnmapped += result.unmapped;
            if (result.error) {
                errors++;
            }
        }

        return new ConvertSummary(totalFiles, totalRows, totalUnmapped, errors);
    }

    private Map<String, String> loadMapping(Path mappingFile) {
        Map<String, String> mapping = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return mapping;
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
                    mapping.put(mc, hytale);
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read mapping file %s: %s", mappingFile, e.getMessage());
        }
        return mapping;
    }

    private FileResult convertFile(Path inputFile,
                                   Path outputFile,
                                   Map<String, String> mapping,
                                   int yOffset,
                                   String defaultUnmappedBlock) {
        long rows = 0;
        long unmapped = 0;

        try (BufferedReader reader = Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new FileResult(0, 0, true);
            }
            String[] header = headerLine.split(",", -1);
            int blockIdx = findColumnIndex(header, "block");
            int yIdx = findColumnIndex(header, "y");
            String[] outHeader;
            if (blockIdx != -1) {
                outHeader = header.clone();
                outHeader[blockIdx] = OUTPUT_BLOCK_COLUMN_NAME;
            } else {
                blockIdx = 3;
                yIdx = yIdx == -1 ? 1 : yIdx;
                outHeader = new String[] {"x", "y", "z", OUTPUT_BLOCK_COLUMN_NAME};
                if (header.length > blockIdx) {
                    String[] row = header;
                    String mcBlock = stripBlockState(row[blockIdx]);
                    String hytale = resolveMapping(mapping, mcBlock, defaultUnmappedBlock);
                    if (isUnmapped(mapping, mcBlock)) {
                        unmapped++;
                    }
                    row[blockIdx] = hytale;
                    adjustYOffset(row, yIdx, yOffset);
                    writer.write(String.join(",", outHeader));
                    writer.newLine();
                    writer.write(String.join(",", row));
                    writer.newLine();
                    rows++;
                    return new FileResult(rows, unmapped, false);
                }
            }

            if (yIdx == -1) {
                yIdx = 1;
            }

            writer.write(String.join(",", outHeader));
            writer.newLine();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] row = line.split(",", -1);
                if (row.length <= blockIdx) {
                    continue;
                }
                String mcBlock = stripBlockState(row[blockIdx]);
                String hytale = resolveMapping(mapping, mcBlock, defaultUnmappedBlock);
                if (isUnmapped(mapping, mcBlock)) {
                    unmapped++;
                }
                row[blockIdx] = hytale;
                adjustYOffset(row, yIdx, yOffset);
                writer.write(String.join(",", row));
                writer.newLine();
                rows++;
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to convert %s: %s", inputFile.getFileName(), e.getMessage());
            return new FileResult(rows, unmapped, true);
        }

        return new FileResult(rows, unmapped, false);
    }

    private void adjustYOffset(String[] row, int yIdx, int yOffset) {
        if (row.length <= yIdx) {
            return;
        }
        try {
            int y = Integer.parseInt(row[yIdx].trim());
            row[yIdx] = Integer.toString(y + yOffset);
        } catch (NumberFormatException ignored) {
        }
    }

    private String stripBlockState(String blockName) {
        if (blockName == null) {
            return "";
        }
        String trimmed = blockName.trim();
        int idx = trimmed.indexOf('[');
        if (idx != -1) {
            return trimmed.substring(0, idx);
        }
        return trimmed;
    }

    private int findColumnIndex(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(header[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    private String resolveMapping(Map<String, String> mapping, String mcBlock, String defaultUnmappedBlock) {
        String mapped = mapping.get(mcBlock);
        if (mapped == null || mapped.isBlank()) {
            if (defaultUnmappedBlock != null && !defaultUnmappedBlock.isBlank()) {
                return defaultUnmappedBlock;
            }
            return UNMAPPED_VALUE;
        }
        return mapped;
    }

    private boolean isUnmapped(Map<String, String> mapping, String mcBlock) {
        String mapped = mapping.get(mcBlock);
        return mapped == null || mapped.isBlank();
    }

    private record FileResult(long rows, long unmapped, boolean error) {
    }

    public record ConvertSummary(int totalFiles, long totalRows, long totalUnmapped, int errors) {
    }
}
