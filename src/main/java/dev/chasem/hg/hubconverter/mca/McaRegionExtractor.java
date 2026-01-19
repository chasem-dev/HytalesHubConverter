package dev.chasem.hg.hubconverter.mca;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class McaRegionExtractor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int SECTOR_BYTES = 4096;
    private static final int VERSION_20W17A = 2529;

    public ExtractSummary extractAll(Path mcaDir, Path outputDir, ExtractOptions options) {
        List<Path> mcaFiles = listMcaFiles(mcaDir);
        if (mcaFiles.isEmpty()) {
            return new ExtractSummary(0, 0, 0, 0);
        }

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to create output dir %s: %s", outputDir, e.getMessage());
        }

        long totalChunks = 0;
        long totalBlocks = 0;
        int totalErrors = 0;

        for (Path mcaFile : mcaFiles) {
            Path outputPath = outputDir.resolve(mcaFile.getFileName().toString().replace(".mca", ".csv"));
            ExtractSummary result = extractRegion(mcaFile, outputPath, options);
            totalChunks += result.totalChunks();
            totalBlocks += result.totalBlocks();
            totalErrors += result.totalErrors();
        }

        return new ExtractSummary(mcaFiles.size(), totalChunks, totalBlocks, totalErrors);
    }

    private List<Path> listMcaFiles(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return List.of();
        }
        try {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".mca"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .forEach(files::add);
            }
            return files;
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to list MCA files in %s: %s", dir, e.getMessage());
            return List.of();
        }
    }

    private ExtractSummary extractRegion(Path mcaFile, Path outputPath, ExtractOptions options) {
        long totalChunks = 0;
        long totalBlocks = 0;
        int errors = 0;

        try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r");
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            long fileLength = raf.length();
            if (fileLength < SECTOR_BYTES * 2L) {
                LOGGER.atWarning().log("[HytalesHub] MCA file too small: %s", mcaFile.getFileName());
                return new ExtractSummary(0, 0, 0, 1);
            }

            byte[] locations = new byte[SECTOR_BYTES];
            raf.readFully(locations);
            raf.skipBytes(SECTOR_BYTES); // timestamps

            writer.write("x,y,z,block");
            writer.newLine();

            for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
                for (int chunkX = 0; chunkX < 32; chunkX++) {
                    int offset = 4 * (chunkX + chunkZ * 32);
                    int sectorOffset = ((locations[offset] & 0xFF) << 16)
                            | ((locations[offset + 1] & 0xFF) << 8)
                            | (locations[offset + 2] & 0xFF);
                    int sectorCount = locations[offset + 3] & 0xFF;
                    if (sectorOffset == 0 || sectorCount == 0) {
                        continue;
                    }

                    long chunkStart = (long) sectorOffset * SECTOR_BYTES;
                    if (chunkStart + 5 > fileLength) {
                        errors++;
                        continue;
                    }

                    raf.seek(chunkStart);
                    int length = raf.readInt();
                    if (length <= 1 || length > sectorCount * SECTOR_BYTES) {
                        errors++;
                        continue;
                    }

                    int compressionType = raf.readUnsignedByte();
                    byte[] compressed = new byte[length - 1];
                    raf.readFully(compressed);

                    try (InputStream decompressed = decompress(compressionType, compressed)) {
                        if (decompressed == null) {
                            errors++;
                            continue;
                        }
                        NbtTag rootTag = NbtReader.read(decompressed);
                        Map<String, NbtTag> root = NbtUtil.asCompound(rootTag);
                        if (root == null) {
                            errors++;
                            continue;
                        }
                        int dataVersion = getDataVersion(root);
                        boolean stretches = dataVersion < VERSION_20W17A;

                        Map<String, NbtTag> level = NbtUtil.getCompound(root, "Level");
                        if (level == null) {
                            level = root;
                        }

                        List<NbtTag> sections = NbtUtil.getList(level, "Sections");
                        if (sections == null) {
                            sections = NbtUtil.getList(level, "sections");
                        }
                        if (sections == null) {
                            continue;
                        }

                        Integer chunkXPos = NbtUtil.getInt(level, "xPos");
                        Integer chunkZPos = NbtUtil.getInt(level, "zPos");
                        int chunkXCoord = chunkXPos != null ? chunkXPos : chunkX;
                        int chunkZCoord = chunkZPos != null ? chunkZPos : chunkZ;
                        int baseX = chunkXCoord * 16;
                        int baseZ = chunkZCoord * 16;

                        totalChunks++;
                        totalBlocks += writeSections(writer, sections, baseX, baseZ, stretches, options);
                    } catch (Exception e) {
                        errors++;
                        LOGGER.atWarning().log("[HytalesHub] Failed to read chunk (%d,%d) in %s: %s",
                                chunkX, chunkZ, mcaFile.getFileName(), e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytalesHub] Failed to read %s: %s", mcaFile.getFileName(), e.getMessage());
            return new ExtractSummary(0, 0, 0, 1);
        }

        LOGGER.atInfo().log("[HytalesHub] Extracted %s: chunks=%d blocks=%d", outputPath.getFileName(), totalChunks, totalBlocks);
        return new ExtractSummary(1, totalChunks, totalBlocks, errors);
    }

    private int getDataVersion(Map<String, NbtTag> root) {
        Integer dataVersion = NbtUtil.getInt(root, "DataVersion");
        return dataVersion != null ? dataVersion : 0;
    }

    private long writeSections(BufferedWriter writer,
                               List<NbtTag> sections,
                               int baseX,
                               int baseZ,
                               boolean stretches,
                               ExtractOptions options) throws IOException {
        long written = 0;

        for (NbtTag sectionTag : sections) {
            Map<String, NbtTag> section = NbtUtil.asCompound(sectionTag);
            if (section == null) {
                continue;
            }
            Integer sectionY = NbtUtil.getInt(section, "Y");
            if (sectionY == null) {
                sectionY = NbtUtil.getInt(section, "y");
            }
            if (sectionY == null) {
                continue;
            }

            int worldYBase = sectionY * 16;
            if (worldYBase > options.yMax() || worldYBase + 15 < options.yMin()) {
                continue;
            }

            byte[] blocks = NbtUtil.asByteArray(NbtUtil.getTag(section, "Blocks"));
            if (blocks != null) {
                byte[] data = NbtUtil.asByteArray(NbtUtil.getTag(section, "Data"));
                byte[] add = NbtUtil.asByteArray(NbtUtil.getTag(section, "Add"));
                if (data == null) {
                    continue;
                }
                written += writeLegacyBlocks(writer, blocks, data, add, baseX, baseZ, worldYBase, options);
                continue;
            }

            PaletteAndStates paletteData = extractPaletteAndStates(section);
            if (paletteData.palette == null || paletteData.palette.isEmpty()) {
                continue;
            }
            List<String> paletteStrings = formatPalette(paletteData.palette);
            long[] states = paletteData.states;
            int bits = 0;
            if (states != null) {
                bits = Math.max(Integer.SIZE - Integer.numberOfLeadingZeros(paletteStrings.size() - 1), 4);
            }

            for (int index = 0; index < 4096; index++) {
                int paletteId;
                if (states == null) {
                    paletteId = 0;
                } else {
                    paletteId = decodePaletteId(states, index, bits, stretches);
                    if (paletteId < 0 || paletteId >= paletteStrings.size()) {
                        continue;
                    }
                }

                String blockStr = paletteStrings.get(paletteId);
                if (options.skipAir() && "minecraft:air".equals(blockStr)) {
                    continue;
                }

                int ly = index >> 8;
                int lz = (index >> 4) & 15;
                int lx = index & 15;
                int y = worldYBase + ly;
                if (y < options.yMin() || y > options.yMax()) {
                    continue;
                }
                int x = baseX + lx;
                int z = baseZ + lz;
                writer.write(x + "," + y + "," + z + "," + blockStr);
                writer.newLine();
                written++;
            }
        }

        return written;
    }

    private long writeLegacyBlocks(BufferedWriter writer,
                                   byte[] blocks,
                                   byte[] data,
                                   byte[] add,
                                   int baseX,
                                   int baseZ,
                                   int worldYBase,
                                   ExtractOptions options) throws IOException {
        long written = 0;
        for (int index = 0; index < 4096; index++) {
            int blockId = index < blocks.length ? blocks[index] & 0xFF : 0;
            if (add != null && (index / 2) < add.length) {
                blockId += (nibble(add, index) << 8);
            }
            int blockData = (index / 2) < data.length ? nibble(data, index) : 0;
            if (options.skipAir() && blockId == 0 && blockData == 0) {
                continue;
            }

            int ly = index >> 8;
            int lz = (index >> 4) & 15;
            int lx = index & 15;
            int y = worldYBase + ly;
            if (y < options.yMin() || y > options.yMax()) {
                continue;
            }

            int x = baseX + lx;
            int z = baseZ + lz;
            String blockStr = "legacy:" + blockId + ":" + blockData;
            writer.write(x + "," + y + "," + z + "," + blockStr);
            writer.newLine();
            written++;
        }
        return written;
    }

    private PaletteAndStates extractPaletteAndStates(Map<String, NbtTag> section) {
        Map<String, NbtTag> blockStates = NbtUtil.getCompound(section, "block_states");
        if (blockStates != null) {
            List<NbtTag> palette = NbtUtil.getList(blockStates, "palette");
            long[] states = NbtUtil.asLongArray(NbtUtil.getTag(blockStates, "data"));
            return new PaletteAndStates(palette, states);
        }

        List<NbtTag> palette = NbtUtil.getList(section, "Palette");
        if (palette == null) {
            palette = NbtUtil.getList(section, "palette");
        }
        NbtTag statesTag = NbtUtil.getTag(section, "BlockStates");
        if (statesTag == null) {
            statesTag = NbtUtil.getTag(section, "data");
        }
        long[] states = NbtUtil.asLongArray(statesTag);
        return new PaletteAndStates(palette, states);
    }

    private List<String> formatPalette(List<NbtTag> palette) {
        List<String> result = new ArrayList<>(palette.size());
        for (NbtTag entry : palette) {
            Map<String, NbtTag> compound = NbtUtil.asCompound(entry);
            if (compound == null) {
                result.add("minecraft:air");
                continue;
            }
            String name = NbtUtil.getString(compound, "Name");
            if (name == null || name.isBlank()) {
                result.add("minecraft:air");
                continue;
            }
            Map<String, NbtTag> props = NbtUtil.getCompound(compound, "Properties");
            if (props == null || props.isEmpty()) {
                result.add(name);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(name).append('[');
            Map<String, String> sortedProps = new TreeMap<>();
            for (Map.Entry<String, NbtTag> prop : props.entrySet()) {
                String value = NbtUtil.asString(prop.getValue());
                if (value != null) {
                    sortedProps.put(prop.getKey(), value);
                }
            }
            boolean first = true;
            for (Map.Entry<String, String> prop : sortedProps.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(prop.getKey()).append('=').append(prop.getValue());
            }
            sb.append(']');
            result.add(sb.toString());
        }
        return result;
    }

    private int decodePaletteId(long[] states, int index, int bits, boolean stretches) {
        if (states.length == 0 || bits <= 0) {
            return 0;
        }
        int state;
        int shift;
        if (stretches) {
            state = (index * bits) / 64;
            shift = (index * bits) % 64;
        } else {
            int valuesPerLong = 64 / bits;
            state = index / valuesPerLong;
            shift = (index % valuesPerLong) * bits;
        }

        if (state < 0 || state >= states.length) {
            return 0;
        }
        long data = states[state];
        long shifted = data >>> shift;

        if (stretches && 64 - shift < bits && state + 1 < states.length) {
            long data2 = states[state + 1];
            int leftover = bits - (64 - shift);
            shifted |= (data2 & ((1L << leftover) - 1L)) << (bits - leftover);
        }

        return (int) (shifted & ((1L << bits) - 1L));
    }

    private InputStream decompress(int compressionType, byte[] compressed) throws IOException {
        return switch (compressionType) {
            case 1 -> new GZIPInputStream(new ByteArrayInputStream(compressed));
            case 2 -> new InflaterInputStream(new ByteArrayInputStream(compressed));
            case 3 -> new ByteArrayInputStream(compressed);
            default -> null;
        };
    }

    private int nibble(byte[] data, int index) {
        int value = data[index / 2] & 0xFF;
        if (index % 2 == 0) {
            return value & 0x0F;
        }
        return (value >> 4) & 0x0F;
    }

    private record PaletteAndStates(List<NbtTag> palette, long[] states) {
    }

    public record ExtractOptions(boolean skipAir, int yMin, int yMax) {
    }

    public record ExtractSummary(long totalFiles, long totalChunks, long totalBlocks, int totalErrors) {
    }
}
