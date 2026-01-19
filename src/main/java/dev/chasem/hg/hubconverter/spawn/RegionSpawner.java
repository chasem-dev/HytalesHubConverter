package dev.chasem.hg.hubconverter.spawn;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RegionSpawner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String UNMAPPED_VALUE = "UNMAPPED";

    public SpawnSummary spawnAll(World world, Path regionDir, int threadCount) {
        if (world == null || regionDir == null || !Files.exists(regionDir)) {
            return new SpawnSummary(0, 0, 0, 0, 1);
        }

        List<Path> files = listCsvFiles(regionDir);
        if (files.isEmpty()) {
            return new SpawnSummary(0, 0, 0, 0, 0);
        }

        long totalPlaced = 0;
        long totalSkipped = 0;
        long totalErrors = 0;
        long start = System.currentTimeMillis();

        int index = 0;
        for (Path file : files) {
            index++;
            SpawnResult result = loadFile(world, file, threadCount);
            totalPlaced += result.placed;
            totalSkipped += result.skipped;
            totalErrors += result.errors;
            LOGGER.atInfo().log("[HytalesHub] Spawned %s (%d/%d): placed=%d skipped=%d errors=%d",
                    file.getFileName(), index, files.size(), result.placed, result.skipped, result.errors);
        }

        long elapsed = System.currentTimeMillis() - start;
        return new SpawnSummary(files.size(), totalPlaced, totalSkipped, totalErrors, elapsed);
    }

    private List<Path> listCsvFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(files::add);
        } catch (Exception e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to list region CSVs in %s: %s", dir, e.getMessage());
        }
        return files;
    }

    private SpawnResult loadFile(World world, Path csvPath, int threadCount) {
        int placed = 0;
        int skippedEmpty = 0;
        int skippedUnmapped = 0;
        int skippedMalformed = 0;
        int parseErrors = 0;
        int setBlockErrors = 0;
        long start = System.currentTimeMillis();

        List<String> lines = new ArrayList<>();
        int xIdx;
        int yIdx;
        int zIdx;
        int blockIdx;

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new SpawnResult(0, 0, 1, 0);
            }
            String[] header = headerLine.split(",", -1);
            xIdx = findColumnIndex(header, "x");
            yIdx = findColumnIndex(header, "y");
            zIdx = findColumnIndex(header, "z");
            blockIdx = findColumnIndex(header, "hytale_block");
            if (blockIdx == -1) {
                blockIdx = findColumnIndex(header, "block");
            }

            if (xIdx == -1 || yIdx == -1 || zIdx == -1 || blockIdx == -1) {
                LOGGER.atWarning().log("[HytalesHub] CSV missing columns: %s", csvPath.getFileName());
                return new SpawnResult(0, 0, 1, 0);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read %s: %s", csvPath.getFileName(), e.getMessage());
            return new SpawnResult(0, 0, 1, 0);
        }

        if (lines.isEmpty()) {
            return new SpawnResult(0, 0, 0, System.currentTimeMillis() - start);
        }

        final int fxIdx = xIdx;
        final int fyIdx = yIdx;
        final int fzIdx = zIdx;
        final int fBlockIdx = blockIdx;
        final int threads = Math.max(1, threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger placedCounter = new AtomicInteger();
        AtomicInteger skippedEmptyCounter = new AtomicInteger();
        AtomicInteger skippedUnmappedCounter = new AtomicInteger();
        AtomicInteger skippedMalformedCounter = new AtomicInteger();
        AtomicInteger parseErrorCounter = new AtomicInteger();
        AtomicInteger setBlockErrorCounter = new AtomicInteger();
        AtomicInteger processedCounter = new AtomicInteger();
        AtomicInteger nextPercentLog = new AtomicInteger(10);
        AtomicLong totalSetBlockTimeNs = new AtomicLong();
        AtomicInteger setBlockCalls = new AtomicInteger();

        ConcurrentHashMap<String, AtomicInteger> setBlockErrorCounts = new ConcurrentHashMap<>();

        int batchSize = Math.max(1000, lines.size() / threads);
        int totalBatches = (lines.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < lines.size(); i += batchSize) {
            final int startIdx = i;
            final int endIdx = Math.min(i + batchSize, lines.size());
            executor.submit(() -> {
                for (int j = startIdx; j < endIdx; j++) {
                    String line = lines.get(j);
                    String[] parts = line.split(",", -1);
                    if (parts.length <= fBlockIdx) {
                        skippedMalformedCounter.incrementAndGet();
                        continue;
                    }

                    String blockId = parts[fBlockIdx].trim();
                    if (blockId.isEmpty()) {
                        skippedEmptyCounter.incrementAndGet();
                        continue;
                    }
                    if (UNMAPPED_VALUE.equalsIgnoreCase(blockId)) {
                        skippedUnmappedCounter.incrementAndGet();
                        continue;
                    }

                    try {
                        int x = Integer.parseInt(parts[fxIdx].trim());
                        int y = Integer.parseInt(parts[fyIdx].trim());
                        int z = Integer.parseInt(parts[fzIdx].trim());
                        try {
                            long t0 = System.nanoTime();
                            world.setBlock(x, y, z, blockId);
                            long dt = System.nanoTime() - t0;
                            totalSetBlockTimeNs.addAndGet(dt);
                            setBlockCalls.incrementAndGet();
                            placedCounter.incrementAndGet();
                        } catch (Exception e) {
                            setBlockErrorCounter.incrementAndGet();
                            setBlockErrorCounts.computeIfAbsent(blockId, k -> new AtomicInteger()).incrementAndGet();
                        }
                    } catch (NumberFormatException e) {
                        parseErrorCounter.incrementAndGet();
                    }

                    int current = processedCounter.incrementAndGet();
                    int percent = (int) ((current * 100L) / lines.size());
                    int next = nextPercentLog.get();
                    if (percent >= next && nextPercentLog.compareAndSet(next, next + 10)) {
                        long avgNs = setBlockCalls.get() > 0 ? totalSetBlockTimeNs.get() / setBlockCalls.get() : 0;
                        LOGGER.atInfo().log("[HytalesHub] %s progress: %d%% (%d/%d) placed=%d avgSetBlock=%.2fms",
                                csvPath.getFileName(), next, current, lines.size(), placedCounter.get(), avgNs / 1_000_000.0);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        placed = placedCounter.get();
        skippedEmpty = skippedEmptyCounter.get();
        skippedUnmapped = skippedUnmappedCounter.get();
        skippedMalformed = skippedMalformedCounter.get();
        parseErrors = parseErrorCounter.get();
        setBlockErrors = setBlockErrorCounter.get();

        if (!setBlockErrorCounts.isEmpty()) {
            setBlockErrorCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                    .limit(10)
                    .forEach(entry -> LOGGER.atWarning().log("[HytalesHub] setBlock failures '%s': %d",
                            entry.getKey(), entry.getValue().get()));
        }

        int skipped = skippedEmpty + skippedUnmapped + skippedMalformed;
        int errors = parseErrors + setBlockErrors;
        long elapsed = System.currentTimeMillis() - start;
        return new SpawnResult(placed, skipped, errors, elapsed);
    }

    private int findColumnIndex(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(header[i].trim())) {
                return i;
            }
        }
        return -1;
    }

    public record SpawnResult(int placed, int skipped, int errors, long elapsedMs) {
    }

    public record SpawnSummary(int files, long placed, long skipped, long errors, long elapsedMs) {
    }
}
