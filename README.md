# HytalesHubConverter

HytalesHubConverter is a dedicated Hytale server mod for converting Minecraft region files into Hytale blocks.
It exposes a single command, `/hytaleshub`, with a step-by-step pipeline (extract -> map -> convert -> spawn) and
an all-in-one `/hytaleshub run` option.

This mod is intended for server owners who want a repeatable, in-server conversion workflow.

## Quick Start

1) Drop raw `.mca` files into the `mc-regions` folder.
2) Run `/hytaleshub run`.
3) Watch logs in chat/console for progress and completion.

## Command Reference

- `/hytaleshub` or `/hytaleshub help`
  - Shows the full help output and current folder paths.
- `/hytaleshub extract`
  - Reads `.mca` files in `mc-regions` and outputs region CSVs in the same folder.
- `/hytaleshub map`
  - Builds a block mapping file and an `unmapped-blocks.csv` report.
- `/hytaleshub convert`
  - Produces mapped region CSVs in `hytale-region-csv`.
- `/hytaleshub spawn`
  - Places blocks from `hytale-region-csv` into the world.
- `/hytaleshub run`
  - Runs all steps in order: extract -> map -> convert -> spawn.

## Folder Layout

All files are under the shared config folder `HytalesHubConverter`.

- `mc-regions/`
  - Place raw `.mca` files here.
  - Extract step writes `r.*.csv` outputs here.
- `hytale-region-csv/`
  - Converted, Hytale-ready region CSVs land here.
- `block-mapping.csv`
  - Generated mapping (Minecraft -> Hytale).
- `unmapped-blocks.csv`
  - A report of blocks that were not mapped by name.
- `block-overrides.csv`
  - Manual overrides to force mappings. (You can edit this.)
- `block-ids.txt`
  - A fallback list of Hytale block IDs if the live registry is not available.

## How the Pipeline Works

1) Extract
   - Reads the region file palettes and legacy sections.
   - Writes `x,y,z,block` CSV rows for each non-air block.

2) Map
   - Combines manual overrides with heuristic name matching.
   - Produces `block-mapping.csv` plus `unmapped-blocks.csv`.
   - If available, uses the live block registry to validate targets.

3) Convert
   - Rewrites each region CSV with a `hytale_block` column.
   - Applies a Y-offset so Minecraft heights fit in Hytale space.
   - Unmapped blocks fall back to the configured default target.

4) Spawn
   - Reads converted CSVs and places blocks into the world.
   - Runs in parallel across many threads for speed.

## Configuration

The config file is created under `HytalesHubConverter` on first load.
Key options include:

- `McRegionsFolder` (default: `mc-regions`)
- `HytaleRegionsFolder` (default: `hytale-region-csv`)
- `BlockMappingFile` (default: `block-mapping.csv`)
- `UnmappedBlocksFile` (default: `unmapped-blocks.csv`)
- `BlockOverridesFile` (default: `block-overrides.csv`)
- `BlockIdsFile` (default: `block-ids.txt`)
- `SkipAir` (default: `true`)
- `ExtractYMin` / `ExtractYMax` (default: `0` / `319`)
- `MapMinScore` (default: `0.45`)
- `ConvertYOffset` (default: `100`)
- `DefaultUnmappedBlock` (default: `Soil_Clay_Smooth_Grey`)
- `SpawnThreads` (default: `100`)
- `UseLiveBlockRegistry` (default: `true`)

## Tips

- If you edit `block-overrides.csv`, re-run `/hytaleshub map` and `/hytaleshub convert`.
- If you want to regenerate the default overrides list, delete `block-overrides.csv` and re-run `/hytaleshub map`.
- Large regions can take time to spawn; watch the console logs for progress updates.

