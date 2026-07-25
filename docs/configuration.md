# WarForge Configuration Guide

This document explains the three configuration layers used by WarForge:

1. The main Forge config: `config/warforge.cfg`
2. The citadel upgrade config: `config/warforge/upgrade_levels.toml`
3. The vein config: `config/warforge/veins.toml`

The goal here is practical administration. This is written around how the current code actually loads and validates these files.

> Format note: the upgrade and vein configs use **TOML** (parsed by Forge's bundled night-config library, the same parser behind `ForgeConfigSpec`). Earlier versions used YAML; if you are coming from an old `.yml` file, recreate it as `.toml` using the examples below.

## Overview

WarForge uses three different config systems for three different jobs:

- `warforge.cfg` controls the main gameplay rules, protections, timers, UI behavior, whitelist and blacklist settings, and most siege and claim behavior.
- `upgrade_levels.toml` controls citadel progression, claim limits per level, insurance slots per level, and upgrade material requirements.
- `veins.toml` controls generated vein definitions, dimension weights, quality overrides, and component yields.

## File Locations

In the current project, the files are loaded from these locations:

- Main config: `config/warforge.cfg`
- Upgrade config: `config/warforge/upgrade_levels.toml`
- Legacy upgrade config: `config/warforge/upgrade_levels.cfg`
- Vein config: `config/warforge/veins.toml`

Notes:

- The main config is loaded from Forge's suggested config path for the `warforge` mod id.
- Upgrade config is only loaded if `Enable Citadel Upgrade System` is enabled in the main config.
- If `upgrade_levels.toml` does not exist and the legacy `upgrade_levels.cfg` exists, the legacy file is migrated to TOML and renamed to `upgrade_levels.cfg.migrated`.
- If `upgrade_levels.toml` is missing or empty, WarForge writes a stub file automatically.
- Legacy vein config path: `config/warforge/veins.cfg`
- If `veins.toml` does not exist and `veins.cfg` does, WarForge migrates the old file to the new `.toml` name automatically.
- If `veins.toml` is missing or empty, WarForge writes a stub file automatically.

## When Changes Apply

WarForge loads these configs during startup, not live:

- The main config is loaded during pre-init.
- Upgrade config is loaded during post-init.
- Vein config is initialized during startup and then synced to clients.

Practical rule:

- Restart the server after changing any of these files.
- Treat vein changes as restart-required and cache-sensitive.

## Part 1: Main Config (`config/warforge.cfg`)

### What It Covers

The main config contains:

- Claim rules
- Siege rules
- Yield timing and global vein quality multipliers
- Notoriety and legacy values
- Warp settings
- Client-side display settings
- Protection sections for different territory types
- Insurance stash blacklist rules
- Flag availability rules

### Main Categories

The important categories and sections are:

- `Claims`
- `Sieges`
- `Yields`
- `Notoriety`
- `Legacy`
- `Warps`
- `Client`
- `Alliances`
- `Debug`
- Protection sections:
  - `Unclaimed`
  - `SafeZone`
  - `WarZone`
  - `CitadelFriend`
  - `CitadelFoe`
  - `ClaimFriend`
  - `ClaimAlly`
  - `ClaimFoe`
  - `Sieger`
  - `SiegeOther`
  - `ClaimDefended`

### High-Impact Settings

These are the settings server owners usually touch first.

### Claims

- `Claim Dimension Whitelist`
  - Which dimensions can be claimed.
- `Claim Manager Radius`
  - The logical square radius requested by the claim manager UI.
- `Enable Citadel Upgrade System`
  - Turns upgrade-based claim progression on or off.
- `Enabled Isolated Claims`
  - If true, most claim blocks must be adjacent to an existing claim.
- `Insurance Blacklist`
  - Wildcard patterns blocked from the faction insurance stash.
- `Available Default Flags`
  - Built-in flags allowed for faction selection.
- `Available Custom Flags`
  - Custom server-side flag ids allowed from resources.

### Sieges

- `Battle Square Chunk Radius From Siege`
  - The active battle area around each siege camp.
- `Attacker Square Chunk Radius From Siege`
  - How far attackers can move before desertion logic matters.
- `Defender Square Chunk Radius From Siege`
  - How far defenders can move before desertion logic matters.
- `Enable Per-Siege timer`
  - Enables the newer per-siege timing system.
- `Siege Defence Threshold`
  - How many points the defenders must swing the siege in their favour to win (attackers win at the chunk's own difficulty threshold). Default 5.
- `Siege Ends On Goal Reached`
  - If on, a siege ends the instant a side reaches its point goal (e.g. via a kill), even mid-timer. If off, the outcome is only settled when the siege timer next elapses. Default on.
- `Kill Detection Mode`
  - `PRECISE` (default) counts only confirmed kills by an opposing player inside the battle zone. `SIMPLE` counts any participant death in the zone toward the siege, including fall/mob/environmental deaths.
- `Siege Stall Escalation Threshold`
  - Anti-stall: once the defenders' lead exceeds this many points, each consecutive siege-timer tick with no kills doubles the attackers' swing (reset by any kill). Only reachable when the Siege Defence Threshold is higher than this value. Default 10.
- `SiegeMomentumMultipliers`
  - Time values per momentum level, written as `level=time`.
- `Cooldown between sieges after failure`
  - Cooldown in minutes.
- `Allow UI Siege Declaration`
  - Allow officers to declare a camp-less siege from the Territory Map, consuming a Siege Camp block. Default on.
- `UI Siege Declaration Max Range`
  - Maximum chunk distance between the chosen launch chunk and the target. Default 4.
- `UI Siege Requires Attacker Presence`
  - Whether a camp-less siege still requires an attacker near the launch chunk to avoid desertion. Default on.

### Alliances

- `Alliance Truce Duration [min]`
  - After an alliance is broken, how many minutes the two factions are blocked from sieging or damaging each other. `0` disables the truce. Default 60.
- `Max Allies Per Faction`
  - Maximum number of simultaneous alliances per faction. `-1` for unlimited. Default 10.

See the [Alliances guide](alliances.md) for how these behave in-game. The `ClaimAlly` protection section (below) controls what allies may do in a faction that has enabled ally access.

### Yields

- `Yield Day Length`
  - Real-world hours between yield ticks.
- `Global Poor Quality Multiplier`
- `Global Fair Quality Multiplier`
- `Global Rich Quality Multiplier`

These three are the fallback multipliers for vein qualities when a specific vein does not override them.

### Client

- `Show yield timers`
- `Show Opponent Chunk Borders`
- `Show Ally Chunk Borders`
- `Chunk vein indicator position`
- `Yield timer position`
- `Siege status position`

These mostly affect local presentation. Server owners should still document expected values for players if they rely on a standard pack or shared screenshots.

### Debug

- `Trace setBlockState In Claimed Chunks`
  - Diagnostic logging only. When enabled, every server-side block change inside a claimed chunk is logged with its position, the owning claim, the block placed, and the calling code. It is meant for tracking down mods that mutate protected terrain outside the normal explosion-protection path. **Very spammy — leave off in production.** Default off.

### Protection Sections

The protection sections are the most important part of `warforge.cfg` if you want to tune what players can do in each territory type.

Each protection section can control:

- Breaking blocks
- Placing blocks
- Interacting
- Using items
- Explosion damage
- Mob spawning and entry
- PVP and other damage
- Mount and dismount behavior
- Action whitelists
- Action blacklists

### Key Rule: Whitelist vs Blacklist

WarForge treats these differently:

- Whitelists allow specific blocks or items even when the general action is denied.
- Blacklists deny specific blocks or items even when the general action is allowed.

So:

- If `Break Blocks = false`, the break whitelist is what still gets through.
- If `Break Blocks = true`, the break blacklist is what still gets blocked.

### Example: Tight Siege Battle Area

This is a practical example if you want siege zones to allow combat and interaction, but still prevent normal block griefing apart from exceptions like `gregtech:machine`.

```properties
Sieges {
    I:"Battle Square Chunk Radius From Siege"=2
    I:"Attacker Square Chunk Radius From Siege"=1
    I:"Defender Square Chunk Radius From Siege"=15
}

Sieger {
    B:"Sieger - Break Blocks"=false
    B:"Sieger - Place Blocks"=false
    B:"Sieger - Interact"=true
    B:"Sieger - Use Items"=true
    B:"Sieger - Block Removal"=true
    B:"Sieger - Explosion Damage"=true

    S:"Sieger - Break Whitelist" <
        minecraft:torch
        warforge:siegecampblock
        gregtech:machine
    >

    S:"Sieger - Place Whitelist" <
        minecraft:torch
        minecraft:web
        minecraft:tnt
        minecraft:end_crystal
    >
}
```

That matches the current default intent fairly closely:

- Normal breaking is blocked
- Specific utility targets can still be broken
- Interaction and item usage are allowed
- Explosions still work

### Example: Insurance Blacklist with Wildcards

The insurance blacklist supports `*` wildcards against:

- `modid:item`
- `modid:item:meta`

Example:

```properties
Claims {
    S:"Insurance Blacklist" <
        minecraft:*shulker_box
        appliedenergistics2:*cell*
        gregtech:meta_item_1:32700
    >
}
```

This is useful when:

- You want to stop nested storage
- You want to block batteries or cells
- You want to block specific metadata variants

### Example: Restricting Custom Flags

```properties
Claims {
    S:"Available Default Flags" <
        default:lion
        default:skull
        default:sun
    >

    S:"Available Custom Flags" <
        server:season1_*
        server:tournament
    >
}
```

Notes:

- `Available Default Flags` should list exact ids.
- `Available Custom Flags` supports wildcards.
- `*` allows all custom flags that pass validation.

### Example: Main Config Starter Pack

This is a reasonable small-server baseline:

```properties
Claims {
    I:"Claim Manager Radius"=4
    B:"Enable Citadel Upgrade System"=true
    B:"Enable Offline Raid Protection"=true
    I:"Offline Raid Protection Hours"=24
    B:"Enabled Isolated Claims"=false
}

Sieges {
    B:"Enable Per-Siege timer"=true
    I:"Battle Square Chunk Radius From Siege"=2
    I:"Attacker Square Chunk Radius From Siege"=1
    I:"Defender Square Chunk Radius From Siege"=15
    S:"SiegeMomentumMultipliers" <
        0=15:00
        1=10:00
        2=8:30
        3=5:00
        4=2:30
    >
}

Yields {
    D:"Yield Day Length"=1.0
    D:"Global Poor Quality Multiplier"=0.5
    D:"Global Fair Quality Multiplier"=1.0
    D:"Global Rich Quality Multiplier"=2.0
}
```

## Part 2: Citadel Upgrade Config (`config/warforge/upgrade_levels.toml`)

### When It Is Used

This file is only used if:

- `Enable Citadel Upgrade System = true`

If upgrades are disabled in `warforge.cfg`, this file is ignored.

### Format Overview

The file is TOML. It has one top-level key, `levels`, written as an **array of tables** — each `[[levels]]` block describes one citadel level:

```toml
[[levels]]
```

Required keys per level:

- `level`
- `claim_limit`

Optional keys per level:

- `insurance_slots`
- `requirements`

### Exact Rules Enforced by the Parser

WarForge currently enforces these rules:

- `claim_limit` must be greater than `0`, or exactly `-1` for unlimited.
- `insurance_slots` must be `>= 0`.
- Claim limits must not go backwards between levels, unless one of them is `-1`.
- `requirements` entries must be tables (inline `{ ... }` tables are the usual form).
- Requirement `type` must be either `ore` or `item`.
- `ore` ids are item **tags** (`namespace:path`).
- `item` ids are item ids (`modid:item`). A legacy `modid:item:meta` suffix is accepted but the `:meta` part is **ignored** — 1.20.1 has no item metadata.
- A requirement whose tag/item does not resolve is logged and skipped.
- `count` defaults to `1` if omitted.

Best practice:

- Define levels consecutively from `0` upward.
- Always define level `0`.
- Keep `level 0` requirements empty unless you intentionally want a base level gate.

### Requirement Types

### Tag (`ore`) Requirement

```toml
requirements = [
    { type = "ore", id = "forge:ingots/iron", count = 64 },
]
```

This accepts any item in that item tag.

### Item Requirement

```toml
requirements = [
    { type = "item", id = "minecraft:diamond", count = 4 },
]
```

### Example: Simple Three-Level Upgrade Tree

```toml
[[levels]]
level = 0
claim_limit = 5
insurance_slots = 0
requirements = []

[[levels]]
level = 1
claim_limit = 10
insurance_slots = 9
requirements = [
    { type = "ore", id = "forge:ingots/iron", count = 64 },
    { type = "item", id = "minecraft:diamond", count = 2 },
]

[[levels]]
level = 2
claim_limit = 15
insurance_slots = 18
requirements = [
    { type = "ore", id = "forge:ingots/steel", count = 128 },
    { type = "item", id = "minecraft:nether_star", count = 1 },
]
```

### Example: Unlimited Final Level

```toml
[[levels]]
level = 0
claim_limit = 5
insurance_slots = 0
requirements = []

[[levels]]
level = 1
claim_limit = 10
insurance_slots = 9
requirements = [
    { type = "ore", id = "forge:ingots/iron", count = 64 },
]

[[levels]]
level = 2
claim_limit = -1
insurance_slots = 27
requirements = [
    { type = "item", id = "minecraft:dragon_egg", count = 1 },
]
```

### Migration from Legacy `.cfg`

If these conditions are true:

- `config/warforge/upgrade_levels.toml` does not exist
- `config/warforge/upgrade_levels.cfg` does exist

Then WarForge will:

1. Parse the legacy file
2. Write a TOML replacement
3. Rename the old file to `upgrade_levels.cfg.migrated`

This migration happens automatically at startup.

### Common Upgrade Config Mistakes

### Claim limits go backwards

Bad:

```toml
[[levels]]
level = 0
claim_limit = 10

[[levels]]
level = 1
claim_limit = 5
```

This fails validation.

### Wrong id type

A `type = "ore"` id must be an item **tag** that exists (e.g. `forge:ingots/iron`), and a `type = "item"` id must be a registered item (e.g. `minecraft:diamond`). A bare `minecraft` with no `:path` is invalid; an unresolved tag/item is logged and skipped.

### Missing `levels`

Bad:

```toml
[[upgrade_levels]]
level = 0
```

The array of tables must be named exactly `levels` (`[[levels]]`).

## Part 3: Vein Config (`config/warforge/veins.toml`)

### Important: This File Is TOML

The canonical file is:

- `config/warforge/veins.toml`

An older install may still have:

- `config/warforge/veins.cfg`

If that old file exists and the new one does not, WarForge renames it to `veins.toml` during startup.

### Startup Behavior

If the file is missing or empty, WarForge writes a stub example automatically.

If parsing fails badly, WarForge logs an error and falls back to safe defaults for the handler. In practice, treat malformed vein configs as startup-breaking until fixed.

### Root Keys

The file must contain:

- `iteration`
- `megachunk_length`
- `veins` — an array of tables (`[[veins]]`), one block per vein

Example:

```toml
iteration = 0
megachunk_length = 32

[[veins]]
key = "warforge.veins.iron_mix"
dims = [
    { id = "minecraft:overworld", weight = 1.0 },
]
components = [
    { item = "minecraft:iron_ore", yield = 2.0 },
]
```

### What `iteration` Means

`iteration` is stored alongside discovered vein ids. If you make a major structural change to vein ids or layout logic, increasing `iteration` is the clean way to invalidate old stored ids.

Use cases:

- You reordered or replaced many vein entries
- You want old discovered vein cache/state to stop matching

### What `megachunk_length` Means

This controls the side length of the larger regions used for vein generation guarantees.

Allowed range:

- `4` to `180`

Practical effect:

- Larger values make each megachunk cover more chunks
- The code uses this with dimension weights to compute expected occurrences

If the value is invalid, WarForge logs an error and falls back to `32`.

### Vein Entry Structure

Each vein entry supports:

- `id`
- `key`
- `quals`
- `dims`
- `components`

### `id`

- Valid explicit ids are `0` to `8191`
- **Omit the `id` key** to auto-generate an id

Important warning:

- If auto-generated ids are used, WarForge writes the resolved ids back into the file
- The code comments explicitly warn that comments may be removed during that rewrite
- Back up your vein config before doing heavy edits

### `key`

A unique translation key for the vein name, for example:

```toml
key = "warforge.veins.iron_mix"
```

### `quals`

This is optional. It is an **inline table** mapping quality names to multipliers.

Correct shape:

```toml
quals = { POOR = 0.5, RICH = 2.5 }
```

If a quality is omitted:

- It falls back to the global multiplier from `warforge.cfg`

Valid qualities in current code:

- `POOR`
- `FAIR`
- `RICH`

### `dims`

`dims` is required and is an **array of inline tables**, one per dimension.

Each dimension entry supports:

- `id` — the dimension ResourceLocation (e.g. `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end`, or a modded dimension id)
- `weight`
- `mult` (optional, defaults to `1`)

Example:

```toml
dims = [
    { id = "minecraft:the_nether", weight = 0.25, mult = 2.0 },
    { id = "minecraft:overworld", weight = 1.0 },
    { id = "minecraft:the_end", weight = 0.15 },
]
```

Meaning:

- `weight` controls how strongly that vein participates in generation in that dimension
- `mult` scales component yield in that dimension if a component does not override it

### `components`

`components` is an **array of inline tables**. Each component entry supports:

- `item`
- `yield`
- `weights` (optional)
- `mults` (optional)

Example:

```toml
components = [
    { item = "minecraft:iron_ore", yield = 2.0, weights = [ { id = "minecraft:overworld", weight = 1.0 }, { id = "minecraft:the_nether", weight = 0.3 } ], mults = [ { id = "minecraft:the_nether", mult = 2.0 } ] },
    { item = "minecraft:coal_ore", yield = 1.0 },
]
```

Notes:

- `weights` is an array of `{ id = <dimension>, weight = <0..1> }` tables
- Omitted component weights default to `1.0`
- `mults` is an array of `{ id = <dimension>, mult = <float> }` tables
- Omitted component multipliers fall back to the dimension `mult`
- If both are omitted, the component just uses its base `yield`

### What Can `item` Be

A component `item` is resolved with auto-detection, so it accepts:

- A normal item registry id like `minecraft:iron_ore`
- An item **tag** like `forge:ores/copper`, if that tag exists (it is preferred when both a tag and an item share the id)

A legacy `modid:item:meta` suffix is accepted but the `:meta` part is ignored — 1.20.1 has no item metadata. Unresolved ids are logged and the component is skipped.

Best practice:

- Use explicit item ids unless you intentionally want tag-based matching

### Example: Simple Overworld Vein

```toml
iteration = 0
megachunk_length = 32

[[veins]]
key = "warforge.veins.starter_iron"
dims = [
    { id = "minecraft:overworld", weight = 1.0 },
]
components = [
    { item = "minecraft:iron_ore", yield = 2.0 },
    { item = "minecraft:coal_ore", yield = 1.0 },
]
```

This creates a simple Overworld-only vein with no special quality or dimension behavior.

### Example: Multi-Dimension Vein with Quality Overrides

```toml
iteration = 1
megachunk_length = 32

[[veins]]
key = "warforge.veins.nether_mixed_metals"
quals = { POOR = 0.4, FAIR = 1.0, RICH = 3.0 }
dims = [
    { id = "minecraft:the_nether", weight = 0.8, mult = 2.0 },
    { id = "minecraft:overworld", weight = 0.2, mult = 0.75 },
]
components = [
    { item = "minecraft:gold_ore", yield = 2.0, weights = [ { id = "minecraft:the_nether", weight = 1.0 }, { id = "minecraft:overworld", weight = 0.4 } ] },
    { item = "minecraft:nether_quartz_ore", yield = 4.0, mults = [ { id = "minecraft:the_nether", mult = 2.5 } ] },
]
```

This does all of the following:

- Uses custom POOR/FAIR/RICH multipliers
- Spawns mainly in the Nether
- Gives the whole vein a higher yield multiplier in the Nether
- Makes gold less common in the Overworld
- Gives nether quartz an even bigger Nether-only yield multiplier

### Example: Tag Component

```toml
iteration = 0
megachunk_length = 32

[[veins]]
key = "warforge.veins.any_copper_mix"
dims = [
    { id = "minecraft:overworld", weight = 1.0 },
]
components = [
    { item = "forge:ores/copper", yield = 2.0 },
]
```

Use this only if you intentionally want the vein to resolve through tag-based matching instead of a specific item.

### Common Vein Config Mistakes

### Wrong shape for `quals`

`quals` must be an inline table whose keys are exactly `POOR`, `FAIR`, and/or `RICH`.

Bad:

```toml
quals = [ { POOR = 0.5, RICH = 2.0 } ]
```

Correct:

```toml
quals = { POOR = 0.5, RICH = 2.0 }
```

### `weights` / `mults` keyed by dimension instead of being tables

Bad:

```toml
weights = { "minecraft:overworld" = 1.0, "minecraft:the_nether" = 0.2 }
```

Correct — an array of `{ id, weight }` tables:

```toml
weights = [
    { id = "minecraft:overworld", weight = 1.0 },
    { id = "minecraft:the_nether", weight = 0.2 },
]
```

### Reordering auto-generated ids without backing up

If you omit `id`, WarForge may rewrite the file with assigned ids. The code explicitly warns that comments may be stripped during this rewrite.

Best practice:

- Keep backups before major edits
- Prefer stable ids once your vein set is mature

### Recommended Workflow

For safe config changes:

1. Edit `warforge.cfg` first if you are changing global rules, quality multipliers, or enabling upgrades.
2. Edit `upgrade_levels.toml` if you are changing progression.
3. Edit `veins.toml` if you are changing ore generation and yield behavior.
4. Restart the server.
5. Watch the startup log for:
   - unknown item or block warnings
   - TOML parse errors
   - invalid claim limit or requirement format errors
6. Join with a client and verify:
   - claim limits
   - insurance capacity
   - siege behavior
   - vein overlays and collector output

### Practical Admin Advice

- Keep `upgrade_levels.toml` and `veins.toml` under version control.
- Back up `veins.toml` before relying on auto-generated ids (omitted `id`) heavily.
- Prefer exact item ids unless you truly want tag-based matching.
- Use `mm:ss` for siege momentum times, because that is what the shipped defaults use.
- Document any custom protection overrides for your players and staff, especially `Sieger`, `SiegeOther`, and `ClaimDefended`.

### Quick Reference

### Main config

- Path: `config/warforge.cfg`
- Syntax: Forge config
- Controls: global gameplay and protection rules

### Upgrade config

- Path: `config/warforge/upgrade_levels.toml`
- Syntax: TOML
- Controls: claim limit progression, insurance slot progression, level requirements

### Vein config

- Path: `config/warforge/veins.toml`
- Syntax: TOML
- Controls: vein generation, dimension weights, qualities, and yields
