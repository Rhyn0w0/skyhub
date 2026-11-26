<img src="assets/icon-256.png" width="64" height="64" alt="SkyHub icon">

# SkyHub

A unified settings hub, global fuzzy search, and drag-and-drop HUD canvas for **SkyBlock Enhanced 5.4.9** (Minecraft **26.2** / Fabric **0.20.1**).

## Features

- **Unified Mod Hub & Global Fuzzy Search (`\` or `/skyhub`)**
  - Search across every indexed SkyBlock mod simultaneously with multi-token fuzzy search (e.g. `odin term`, `sh farm`, `sb solver`, `pwdr`).
  - Inline live controls (boolean toggles, numeric sliders + step buttons, enum cyclers, inline string editing, color swatches, reset-to-default).
  - Instant **`ON Only`** filter to audit active features across all mods, plus **`SkyBlock`** / **`All Mods`** scope switching.
  - Per-mod color badges, category breadcrumbs, and a left **Mod Switcher** with live adapter health indicators.
- **Unified Drag-and-Drop HUD Canvas (`]` or `/skyhub hud`)**
  - Manage HUD positions and scales across **SkyHanni**, **Skyblocker**, **Odin**, **SkyOcean**, **CustomScoreboard**, and **Stella** on one shared canvas.
  - Snap-to-edge, snap-to-screen-center, and snap-to-sibling-widget alignment guides (hold `Shift` to bypass snapping).
  - Mouse-wheel scaling, arrow-key pixel nudging (`Shift` + arrows for 5px), right-click toggling, and per-mod filter pills.
- **No Duplicate Storage (PackCore Synced)**
  - Every edit writes directly to the owning mod's live config object and invokes its native save hook so native menus and PackCore config backups stay in sync.
- **Crash-Proof Two-Tier Adapter Isolation**
  - Every adapter checks `FabricLoader.isModLoaded(...)` and runs inside an isolated `try/catch` boundary. If a mod update changes internal classes, that adapter disables cleanly and falls back to a one-click **Native Menu** / **Native HUD** button.

## Adapter Coverage (SkyBlock Enhanced 5.4.9)

| Tier | Adapter | Covered Mods |
| --- | --- | --- |
| Tier 1 (Framework) | **ResourcefulConfig** | SkyOcean, CustomScoreboard, SkyBlockPv, Feesh |
| Tier 1 (Framework) | **MidnightLib** | PackCore, SkyBlock Enhancements, SkyRecipes, Enhanced Chat, Enhanced Storage, Enhanced Sound Control, ScaleMe, MarketGuard |
| Tier 1 (Framework) | **MoulConfig** | SkyHanni (and any MoulConfig-based mod) |
| Tier 1 (Framework) | **Dandelion / YACL** | Skyblocker |
| Tier 1 (Framework HUD) | **Meowdding Overlays** | SkyOcean & CustomScoreboard HUD overlays |
| Tier 2 (Mod-Specific) | **SkyHanni / Skyblocker / Odin / Stella** | Live settings + native HUD position/scale registries |
| Tier 3 (Fallback) | **ModMenu & Native Commands** | All remaining mods in SkyBlock Enhanced 5.4.9 |

## Controls & Commands

- `\` (Backslash) — Open SkyHub Settings Hub (rebindable under Controls → SkyHub)
- `]` (Right Bracket) — Open Unified HUD Canvas
- `Tab` — Switch between Settings Hub and Unified HUD Canvas
- `/skyhub` or `/shhub` — Open Settings Hub
- `/skyhub hud` — Open Unified HUD Canvas
- `/skyhub reload` — Re-index all live mod registries

## Building

Requires **Java 25** (matching Minecraft 26.2).

```bash
./gradlew build
```

The built mod JAR is written to `build/libs/skyhub-*.jar`. Drop it directly into your SkyBlock Enhanced 5.4.9 instance's `mods/` folder.

## Releasing (GitHub Releases & Modrinth)

Pushing a version tag (e.g. `v0.1.0-alpha.1`) triggers [release.yml](.github/workflows/release.yml) to build, run tests, publish a GitHub Release with the JARs attached, and upload to Modrinth via Minotaur:

```bash
git tag v0.1.0-alpha.1
git push origin v0.1.0-alpha.1
```

To enable automatic Modrinth uploads on tag pushes, configure these in your GitHub repository settings (**Settings → Secrets and variables → Actions**):

- **Secret** `MODRINTH_TOKEN` — Modrinth PAT with `CREATE_VERSION` (and `WRITE_PROJECT` if syncing description)
- **Variable** `MODRINTH_PROJECT_ID` — your Modrinth project ID or slug (or set `modrinth_project_id` in `gradle.properties`)

