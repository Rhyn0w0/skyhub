<img src="assets/icon-256.png" width="64" height="64" alt="SkyHub icon">

# SkyHub

A unified settings hub, global fuzzy search, and drag-and-drop HUD canvas for popular **Hypixel SkyBlock** mods.

## Features

- **Unified Settings Hub & Global Fuzzy Search (`\`, `/skyhub`, or `/skyhub config`)**
  - iPad Settings–style layout with **recursive collapsible sections** and grouped setting cards.
  - Browse by **Feature Type** by default (`Mod Settings`, `Dungeons`, `Mining`, `Farming & Garden`, `Fishing`, `Slayers`, `Crimson Isle`, `The End`, `Foraging & Hunting`, `Combat & Bosses`, `Economy & Bazaar`, `Inventory & Items`, `Chat & Party`, `HUD & Visuals`) or switch to **By Mod** in `/skyhub settings`.
  - Global multi-token fuzzy search across all indexed mods (e.g. `odin term`, `sh farm`, `sb solver`, `pwdr`) that auto-expands matching sections.
  - Inline live controls (boolean toggles, numeric sliders + step buttons, enum cyclers, inline string editing, color swatches, reset-to-default).
- **Unified Drag-and-Drop HUD Canvas (`]` or `/skyhub hud`)**
  - Manage HUD positions and scales across **SkyHanni**, **Skyblocker**, **Odin**, **SkyOcean**, **CustomScoreboard**, and **Stella** on one shared canvas.
  - Filter widgets cleanly using composable **Mod**, **Type**, and **Visibility** (`All HUDs` / `Active` / `Disabled`) dropdowns.
  - Drag corner handles to resize smoothly, drag widgets with snap-to-edge / snap-to-center / snap-to-widget guides (hold `Shift` to bypass snapping), nudge with arrow keys (`Shift` + arrows for 5px), and right-click to toggle ON/OFF.
  - Hover the `?` toolbar button anytime for a quick controls reference.
- **Skyblocker-Style Mod Settings (`/skyhub settings` or ModMenu)**
  - Native vanilla Minecraft UI with full resource/texture pack support for configuring SkyHub's own preferences (`Group By`, `SkyBlock / All Mods`, `HUD Snap`), opening the config folder, and quick project links.
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
| Tier 3 (Fallback) | **ModMenu & Native Commands** | Most remaining mods |

## Controls & Commands

- `\` (Backslash) — Open SkyHub Settings Hub (rebindable under Controls → SkyHub)
- `]` (Right Bracket) — Open Unified HUD Canvas
- `Tab` — Switch between Settings Hub and Unified HUD Canvas
- `/skyhub`, `/skyhub config`, or `/shhub` — Open Settings Hub
- `/skyhub hud` — Open Unified HUD Canvas
- `/skyhub settings` — Open SkyHub's own mod settings (also via ModMenu)
- `/skyhub reload` — Re-index all live mod registries
