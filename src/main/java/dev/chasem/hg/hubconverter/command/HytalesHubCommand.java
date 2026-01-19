package dev.chasem.hg.hubconverter.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.Config;
import dev.chasem.hg.hubconverter.config.HytalesHubConfig;
import dev.chasem.hg.hubconverter.convert.RegionCsvConverter;
import dev.chasem.hg.hubconverter.io.HytalesHubPaths;
import dev.chasem.hg.hubconverter.mca.McaRegionExtractor;
import dev.chasem.hg.hubconverter.mapping.BlockMapper;
import dev.chasem.hg.hubconverter.spawn.RegionSpawner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class HytalesHubCommand extends AbstractCommandCollection {

    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER =
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();

    private final Config<HytalesHubConfig> config;

    public HytalesHubCommand(Config<HytalesHubConfig> config) {
        super("hytaleshub", "HytalesHub world conversion commands");
        this.config = config;
        addSubCommand(new ExtractCommand());
        addSubCommand(new MapCommand());
        addSubCommand(new ConvertCommand());
        addSubCommand(new SpawnCommand());
        addSubCommand(new RunCommand());
        addSubCommand(new HelpCommand());
    }

    private void runExtract(CommandContext context) {
        HytalesHubConfig cfg = config.get();
        Path mcRegionsDir = HytalesHubPaths.getMcRegionsDir(cfg);
        ensureDir(mcRegionsDir);

        long start = System.currentTimeMillis();
        announce(context, "Starting extract from " + mcRegionsDir);
        McaRegionExtractor extractor = new McaRegionExtractor();
        McaRegionExtractor.ExtractOptions options = new McaRegionExtractor.ExtractOptions(
                cfg.isSkipAir(), cfg.getExtractYMin(), cfg.getExtractYMax());
        McaRegionExtractor.ExtractSummary summary = extractor.extractAll(mcRegionsDir, mcRegionsDir, options);

        if (summary.totalFiles() == 0) {
            announce(context, "No .mca files found in " + mcRegionsDir);
            return;
        }

        long elapsedMs = System.currentTimeMillis() - start;
        announce(context, String.format(
                "Extract complete: files=%d chunks=%d blocks=%d errors=%d (%.2fs)",
                summary.totalFiles(), summary.totalChunks(), summary.totalBlocks(), summary.totalErrors(),
                elapsedMs / 1000.0));
    }

    private void runMap(CommandContext context) {
        HytalesHubConfig cfg = config.get();
        Path mcRegionsDir = HytalesHubPaths.getMcRegionsDir(cfg);
        ensureDir(mcRegionsDir);

        Path mappingFile = HytalesHubPaths.getBlockMappingFile(cfg);
        Path unmappedFile = HytalesHubPaths.getUnmappedBlocksFile(cfg);
        Path overridesFile = HytalesHubPaths.getBlockOverridesFile(cfg);
        Path blockIdsFile = HytalesHubPaths.getBlockIdsFile(cfg);

        World world = context.isPlayer() ? context.senderAs(Player.class).getWorld() : null;

        long start = System.currentTimeMillis();
        announce(context, "Starting map step (mc-regions=" + mcRegionsDir + ")");
        BlockMapper mapper = new BlockMapper();
        BlockMapper.MapResult result = mapper.mapBlocks(
                mcRegionsDir,
                mappingFile,
                unmappedFile,
                overridesFile,
                blockIdsFile,
                cfg,
                world
        );

        long elapsedMs = System.currentTimeMillis() - start;
        announce(context, String.format(
                "Map complete: mcBlocks=%d rows=%d unmatched=%d hytaleBlocks=%d (%.2fs)",
                result.uniqueMcBlocks(), result.mappingRows(), result.unmatchedCount(), result.hytaleBlockCount(),
                elapsedMs / 1000.0));
        announce(context, "Mapping file: " + mappingFile);
        announce(context, "Unmapped file: " + unmappedFile);
    }

    private void runConvert(CommandContext context) {
        HytalesHubConfig cfg = config.get();
        Path mcRegionsDir = HytalesHubPaths.getMcRegionsDir(cfg);
        ensureDir(mcRegionsDir);

        Path mappingFile = HytalesHubPaths.getBlockMappingFile(cfg);
        Path outputDir = HytalesHubPaths.getHytaleRegionsDir(cfg);
        ensureDir(outputDir);

        long start = System.currentTimeMillis();
        announce(context, "Starting convert step (output=" + outputDir + ")");
        RegionCsvConverter converter = new RegionCsvConverter();
        RegionCsvConverter.ConvertSummary summary = converter.convertAll(
                mcRegionsDir, mappingFile, outputDir, cfg.getConvertYOffset(), cfg.getDefaultUnmappedBlock());

        long elapsedMs = System.currentTimeMillis() - start;
        announce(context, String.format(
                "Convert complete: files=%d rows=%d unmapped=%d errors=%d (%.2fs)",
                summary.totalFiles(), summary.totalRows(), summary.totalUnmapped(), summary.errors(),
                elapsedMs / 1000.0));
        announce(context, "Output folder: " + outputDir);
    }

    private void runSpawn(CommandContext context) {
        World world = context.isPlayer() ? context.senderAs(Player.class).getWorld() : null;
        if (world == null) {
            announce(context, "Player world is not available. Run this command in-game.");
            return;
        }

        HytalesHubConfig cfg = config.get();
        Path outputDir = HytalesHubPaths.getHytaleRegionsDir(cfg);
        ensureDir(outputDir);

        long start = System.currentTimeMillis();
        announce(context, "Starting spawn step (input=" + outputDir + ")");
        RegionSpawner spawner = new RegionSpawner();
        RegionSpawner.SpawnSummary summary = spawner.spawnAll(world, outputDir, cfg.getSpawnThreads());

        long elapsedMs = System.currentTimeMillis() - start;
        announce(context, String.format(
                "Spawn complete: files=%d placed=%d skipped=%d errors=%d (%.2fs)",
                summary.files(), summary.placed(), summary.skipped(), summary.errors(), elapsedMs / 1000.0));
    }

    private void runAll(CommandContext context) {
        announce(context, "Running full pipeline: extract -> map -> convert -> spawn");
        runExtract(context);
        runMap(context);
        runConvert(context);
        runSpawn(context);
        announce(context, "Pipeline complete.");
    }

    private void sendHelp(CommandContext context) {
        HytalesHubConfig cfg = config.get();
        Path mcRegionsDir = HytalesHubPaths.getMcRegionsDir(cfg);
        Path outputDir = HytalesHubPaths.getHytaleRegionsDir(cfg);

        StringBuilder sb = new StringBuilder();
        sb.append("=== /hytaleshub ===\n");
        sb.append("/hytaleshub extract  - Read .mca files from mc-regions and write region CSVs\n");
        sb.append("/hytaleshub map      - Build block mappings + unmapped-blocks.csv\n");
        sb.append("/hytaleshub convert  - Map region CSVs into hytale-region-csv\n");
        sb.append("/hytaleshub spawn    - Place blocks from hytale-region-csv into the world\n");
        sb.append("/hytaleshub run      - Run all steps in order\n");
        sb.append("/hytaleshub --help   - Show this help\n");
        sb.append("\nFolders:\n");
        sb.append("- mc-regions: ").append(mcRegionsDir).append("\n");
        sb.append("- hytale-region-csv: ").append(outputDir);

        context.sender().sendMessage(Message.raw(sb.toString()));
    }

    private void ensureDir(Path dir) {
        if (dir == null || Files.exists(dir)) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
    }

    private void announce(CommandContext context, String message) {
        LOGGER.atInfo().log("[HytalesHub] %s", message);
        try {
            if (context != null && context.sender() != null) {
                context.sender().sendMessage(Message.raw(message));
            }
        } catch (Exception ignored) {
        }
    }

    private final class ExtractCommand extends AbstractAsyncCommand {
        private ExtractCommand() {
            super("extract", "Read .mca files and write region CSVs");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            runExtract(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class MapCommand extends AbstractAsyncCommand {
        private MapCommand() {
            super("map", "Build block mappings + unmapped-blocks.csv");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            runMap(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class ConvertCommand extends AbstractAsyncCommand {
        private ConvertCommand() {
            super("convert", "Map region CSVs into hytale-region-csv");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            runConvert(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class SpawnCommand extends AbstractAsyncCommand {
        private SpawnCommand() {
            super("spawn", "Place blocks from hytale-region-csv into the world");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            runSpawn(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class RunCommand extends AbstractAsyncCommand {
        private RunCommand() {
            super("run", "Run extract, map, convert, and spawn");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            runAll(context);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class HelpCommand extends AbstractAsyncCommand {
        private HelpCommand() {
            super("help", "Show /hytaleshub help");
            addAliases("--help");
            addAliases("-h");
        }

        @Override
        @Nonnull
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            sendHelp(context);
            return CompletableFuture.completedFuture(null);
        }
    }
}
