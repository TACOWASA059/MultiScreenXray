# MultiScreen X-ray

[日本語](README_ja.md)

<p align="center">
  <img src="docs/media/multiscreenxray-icon.png" width="220" alt="MultiScreen X-ray icon">
</p>

Open multiple independent X-ray views beside the normal Minecraft window. Every view has its own saved block filters, entity filters, preset, visual options, scan range, size, and position.

![MultiScreen X-ray showing ores, fluids, entities, and the normal game view](docs/media/showcase.png)

![A single X-ray view with full-bright entities, fluids, block textures, and outlines](docs/media/xray-view.png)

## Features

- Up to eight additional X-ray windows
- Real Minecraft block textures with optional translucent surrounding block outlines
- Full-bright entities, fluids, and the player's hand rendered in each extra view
- Separate settings for every screen
- Visual block and entity libraries with icons, names, search, and All/Selected views
- Select blocks or entities by registry ID, `#tag`, or `*` wildcard
- Ores, valuables, Nether, structures, and entities presets
- Saved screen count, filters, visual options, scan distance, window size, and window position
- Client side operation in singleplayer and multiplayer

## Visual configuration

### Screen profiles and presets

![Modern screen profile and preset settings](docs/media/gui-settings.png)

### Block library

![Searchable block library with named ore tag shortcuts](docs/media/gui-block-library.png)

### Entity library

![Searchable entity library with common selection shortcuts](docs/media/gui-entity-library.png)

## Controls

| Key | Action |
|---|---|
| `F8` | Add an X-ray window |
| `Shift` + `F8` | Remove an X-ray window |
| `F9` | Open the visual settings screen |

The settings screen provides monitor tabs for choosing a screen. Use the block and entity library buttons next to the selector fields to choose entries visually. The text fields remain available for modded registry IDs and custom tags.

## Selectors

- Block ID: `minecraft:diamond_ore`
- Block tag: `#minecraft:diamond_ores`
- Entity ID: `minecraft:player`
- Entity tag: `#minecraft:raiders`
- Every entity: `*`

Multiple selectors are separated with commas. Settings are stored in `config/multiscreenxray.json`.

## Supported branches

| Branch | Minecraft | Loaders |
|---|---|---|
| `1.20.1` | 1.20.1 | Fabric, Forge |
| `1.20.4` | 1.20.4 | Fabric, Forge, NeoForge |
| `1.21.1` | 1.21.1 | Fabric, Forge, NeoForge |
| `1.21.11` | 1.21.11 | Fabric, Forge, NeoForge |
| `26.2` | 26.2 | Fabric, NeoForge |
| `26.3` | 26.3 | Fabric, NeoForge |

Multiplayer support is client side and does not require installation on the server. Server anti X-ray systems can limit which hidden blocks the client receives. Follow the rules of the server you join.

## License

[MIT](LICENSE)
