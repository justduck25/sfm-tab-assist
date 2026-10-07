# SFM Tab Assist

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)

**SFM Tab Assist** is a standalone NeoForge addon mod for **Super Factory Manager (SFM)** on **Minecraft 1.21.1 / NeoForge 26.1.2**.

It brings intelligent, real-time **Ghost Text Autocomplete & Tab Assist** to the SFM script editor — offering a development experience akin to GitHub Copilot or modern code editors directly inside Minecraft!

---

## ✨ Features

### 1. Inline Ghost Text Autocomplete
- Suggestions are rendered as dimmed ghost text right beside your text cursor without obstructing your code.
- Smart activation: Ghost text only triggers when typing SFML syntax (`EVERY`, `INPUT`, `OUTPUT`, `FOR`, `IF`, etc.), avoiding unwanted spam on empty lines.
- Press **`TAB`** to accept the suggestion instantly, or press **`ESC`** / continue typing to dismiss it.

### 2. Context-Aware Cable Network Scanning
- Automatically detects and inspects the cable network connected to the Factory Manager when the GUI opens:
  - Discovers connected inventories, furnaces, tanks, and machines.
  - Reads real-time contents (items, quantities, and fluids) in connected containers.
  - Scans user labels assigned across disks and cable attachments.
- Tailors autocomplete suggestions to what is actually available (e.g. typing `INPUT ` suggests items currently present in connected chests along with their source labels).

### 3. Comprehensive Multi-Mod & Machine Profiles
Equipped with built-in machine heuristics and profile definitions for accurate input/output slot and side routing:
- **Vanilla Minecraft**: Furnaces, Smokers, Blast Furnaces (smelting input, fuel, output slots), Brewing Stands (potion slots, ingredients, blaze powder fuel).
- **Mekanism**: Metallurgic Infuser (infuse buffer vs. base metal vs. output), Crusher, Enrichment Chamber, Smelter, Osmium Compressor, and more.
- **Applied Energistics 2 (AE2)**: Inscriber (top press, silicon press, raw materials, processor output), Pattern Provider, ME Interface.
- **Thermal Series / Expansion**: Pulverizer, Redstone Furnace, Induction Smelter, Fluid Encapsulator, Magma Crucible, etc.
- **Ender IO**: SAG Mill, Alloy Smelter, Crafter, etc.
- **Dynamic Sided Capability Probing**: Automatically discovers sided input and output capabilities for unlisted mods.

### 4. Smart Slot & Side Mutual Exclusivity
- Adheres strictly to SFML grammar rules:
  - When specifying a `SLOTS <id>`, redundant `SIDE` clauses are omitted to avoid invalid syntax errors (e.g. `slots 0` instead of `top side slots 0`).
  - Contextual chained routing: after an `INPUT` from a machine output slot, the subsequent `OUTPUT` automatically directs towards storage chests rather than pushing back into the machine.

### 5. Semantic Label Resolver (Label Locator)
- Brings the power of **"Go to Definition" (Ctrl + Click)** to SFM scripting!
- Simply hold **`Ctrl` and click** on any label in your script (e.g. `iron_chest`, `"input_iron"`, `smelter`):
  - **Multi-Criteria Semantic Scoring Engine**: Analyzes exact disk labels (100%), lexical block ID / name similarity (40%), inventory contents (35%), machine roles / recipe types (25%), and flow direction (10%).
  - **In-World Visual X-Ray Outlines**: Highlights candidate blocks directly in the 3D world with color-coded bounding boxes and floating billboard tags:
    - 🟢 **High Confidence ($\ge 80\%$)**: Bright Green
    - 🟡 **Medium Confidence ($50\% - 79\%$)**: Gold / Yellow
    - 🟠 **Low Confidence ($25\% - 49\%$)**: Orange
  - Highlights multiple candidates simultaneously without taking control away from the player.
  - Automatically fades out after 15 seconds.

### 6. In-Editor GUI Toggle Button
- An interactive `[Tab: ON]` / `[Tab: OFF]` button is integrated directly into the script editor screen (`SFMTextEditScreenV1`).
- Positioned neatly in the top-right header area so it never overlaps existing action buttons.
- Player preference is automatically persisted to client configuration (`config/sfm_tab_assist-client.toml`).

---

## 🎮 Keybindings & Controls

| Action | Key / Control | Description |
|---|---|---|
| **Accept Suggestion** | `TAB` | Inserts the active ghost text into the script editor |
| **Dismiss Suggestion** | `ESC` | Clears the active ghost text |
| **Locate Label in World** | `Ctrl + Click` on label | Resolves and highlights matching blocks in the 3D world |
| **Toggle Tab Assist** | `[Tab: ON / OFF]` Button | Enables or disables autocomplete directly from the editor GUI |

---

## 📦 Requirements & Installation

### Requirements
- **Minecraft**: `1.21.1`
- **NeoForge**: `26.1.2` or later
- **Super Factory Manager (SFM)**: `26.1.2` or later

### Installation
1. Download the latest `.jar` from [GitHub Releases](https://github.com/justduck25/sfm-tab-assist/releases).
2. Place the `.jar` into your Minecraft instance's `mods/` directory.
3. Launch Minecraft and start scripting!

---

## 🛠️ Building from Source

To compile the mod from source code:

```bash
# Clone the repository
git clone https://github.com/justduck25/sfm-tab-assist.git
cd sfm-tab-assist

# Run unit tests
./gradlew test

# Build the mod JAR
./gradlew build
```

The resulting mod jar will be located in `build/libs/`.

---

## 📄 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
