# Changelog

All notable changes to **SFM Tab Assist** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-10-07

### Added
- **Semantic Label Locator (`Ctrl + Click`)**:
  - Hold `Ctrl` and left-click on any script label/identifier in the editor to resolve and highlight candidate blocks in the world.
  - **Multi-Factor Semantic Heuristic Engine**:
    - Matches disk labels (100%), block ID / name similarity (40%), container contents (35%), machine roles / recipe capabilities (25%), and flow direction (10%).
  - **Visual In-World Highlights**:
    - Dynamic color coding: Green ($\ge 80\%$), Yellow ($50\% - 79\%$), Orange ($25\% - 49\%$).
    - See-through bounding box outlines and floating 3D billboard name tags with confidence ratings.
    - Automatically fades out after 15 seconds (300 ticks).
- **Multi-Block Structure Support**:
  - Added support for Double Chests, Beds, and Doors.
  - Bounding box rendering outlines both halves using accurate voxel shapes.
  - 3D billboard tags are centered at the seam between halves.
  - Both halves of Double Chests are sampled together (54 slots) for label and inventory matching.
- **In-Hand vs. Factory Manager Mode Handling**:
  - Differentiates between editing a disk inside a Factory Manager block vs. holding the disk in hand:
    - **In-Hand Mode**: Autocompletes SFM language keywords (`INPUT`, `OUTPUT`, `EVERY`, `TICKS`, `FROM`, `TO`, `DO`, `END`, etc.) without suggesting or hallucinating unconnected network labels.
    - **Editor Header Banner**: Dynamically displays `[Ctrl + Click]: Locate label in world` when connected, or `[Ctrl + Click]: Offline (Insert disk into Factory Manager to locate)` when holding the disk in hand.
    - **Action Bar Feedback**: Informs the player if they `Ctrl + Click` while editing a disk outside a Factory Manager.

### Changed
- Refactored `WorldHighlightRenderer` to split voxel outline and text rendering into separate batches, preventing `BufferBuilder` state crashes.
- Removed fallback radius scanning and hand-held disk scanning from `CableContextScanner`, ensuring network data is strictly gathered from active Manager blocks.

---

## [1.0.0] - 2026-10-07

### Added
- **Inline Ghost Text Autocomplete**: Real-time inline ghost text suggestions in `SFMTextEditScreenV1`.
- **Context-Aware Cable Network Scanning**: Automatically inspects connected inventories, fluids, and disks in real-time.
- **Machine Profiles & Heuristics**: Built-in support for Vanilla, Mekanism, AE2, Thermal Series, and Ender IO slot routing.
- **In-Editor Toggle Button**: Added `[Tab: ON]` / `[Tab: OFF]` button with client configuration persistence.
