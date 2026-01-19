package dev.chasem.hg.hubconverter.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class HytalesHubConfig {

    public static final BuilderCodec<HytalesHubConfig> CODEC = BuilderCodec.builder(
            HytalesHubConfig.class, HytalesHubConfig::new)
            .append(new KeyedCodec<>("McRegionsFolder", Codec.STRING),
                    (c, v) -> c.mcRegionsFolder = v, c -> c.mcRegionsFolder)
            .add()
            .append(new KeyedCodec<>("HytaleRegionsFolder", Codec.STRING),
                    (c, v) -> c.hytaleRegionsFolder = v, c -> c.hytaleRegionsFolder)
            .add()
            .append(new KeyedCodec<>("BlockMappingFile", Codec.STRING),
                    (c, v) -> c.blockMappingFile = v, c -> c.blockMappingFile)
            .add()
            .append(new KeyedCodec<>("UnmappedBlocksFile", Codec.STRING),
                    (c, v) -> c.unmappedBlocksFile = v, c -> c.unmappedBlocksFile)
            .add()
            .append(new KeyedCodec<>("BlockOverridesFile", Codec.STRING),
                    (c, v) -> c.blockOverridesFile = v, c -> c.blockOverridesFile)
            .add()
            .append(new KeyedCodec<>("BlockIdsFile", Codec.STRING),
                    (c, v) -> c.blockIdsFile = v, c -> c.blockIdsFile)
            .add()
            .append(new KeyedCodec<>("SkipAir", Codec.BOOLEAN),
                    (c, v) -> c.skipAir = v, c -> c.skipAir)
            .add()
            .append(new KeyedCodec<>("ExtractYMin", Codec.INTEGER),
                    (c, v) -> c.extractYMin = v, c -> c.extractYMin)
            .add()
            .append(new KeyedCodec<>("ExtractYMax", Codec.INTEGER),
                    (c, v) -> c.extractYMax = v, c -> c.extractYMax)
            .add()
            .append(new KeyedCodec<>("MapMinScore", Codec.FLOAT),
                    (c, v) -> c.mapMinScore = v, c -> c.mapMinScore)
            .add()
            .append(new KeyedCodec<>("ConvertYOffset", Codec.INTEGER),
                    (c, v) -> c.convertYOffset = v, c -> c.convertYOffset)
            .add()
            .append(new KeyedCodec<>("DefaultUnmappedBlock", Codec.STRING),
                    (c, v) -> c.defaultUnmappedBlock = v, c -> c.defaultUnmappedBlock)
            .add()
            .append(new KeyedCodec<>("SpawnThreads", Codec.INTEGER),
                    (c, v) -> c.spawnThreads = v, c -> c.spawnThreads)
            .add()
            .append(new KeyedCodec<>("UseLiveBlockRegistry", Codec.BOOLEAN),
                    (c, v) -> c.useLiveBlockRegistry = v, c -> c.useLiveBlockRegistry)
            .add()
            .build();

    private String mcRegionsFolder = "mc-regions";
    private String hytaleRegionsFolder = "hytale-region-csv";
    private String blockMappingFile = "block-mapping.csv";
    private String unmappedBlocksFile = "unmapped-blocks.csv";
    private String blockOverridesFile = "block-overrides.csv";
    private String blockIdsFile = "block-ids.txt";
    private boolean skipAir = true;
    private int extractYMin = 0;
    private int extractYMax = 319;
    private float mapMinScore = 0.45f;
    private int convertYOffset = 100;
    private String defaultUnmappedBlock = "Soil_Clay_Smooth_Grey";
    private int spawnThreads = 100;
    private boolean useLiveBlockRegistry = true;

    public String getMcRegionsFolder() {
        return mcRegionsFolder;
    }

    public String getHytaleRegionsFolder() {
        return hytaleRegionsFolder;
    }

    public String getBlockMappingFile() {
        return blockMappingFile;
    }

    public String getUnmappedBlocksFile() {
        return unmappedBlocksFile;
    }

    public String getBlockOverridesFile() {
        return blockOverridesFile;
    }

    public String getBlockIdsFile() {
        return blockIdsFile;
    }

    public boolean isSkipAir() {
        return skipAir;
    }

    public int getExtractYMin() {
        return extractYMin;
    }

    public int getExtractYMax() {
        return extractYMax;
    }

    public float getMapMinScore() {
        return mapMinScore;
    }

    public int getConvertYOffset() {
        return convertYOffset;
    }

    public String getDefaultUnmappedBlock() {
        return defaultUnmappedBlock;
    }

    public int getSpawnThreads() {
        return spawnThreads;
    }

    public boolean isUseLiveBlockRegistry() {
        return useLiveBlockRegistry;
    }
}
