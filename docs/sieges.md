# The Siege System

Sieges are how factions take territory from each other. A claimed chunk cannot simply be broken into — to capture it, an attacking faction places a **Siege Camp** next to it and wins a timed, PVP-driven contest over that chunk. Sieges are the only sanctioned way to fight over and seize claimed land.

This guide covers placing a camp, starting and fighting a siege, what attackers can and can't do inside the battle zone, how a siege is won or lost, and the admin controls.

## Overview

- One siege targets **one defending chunk**.
- The **attacker** places a Siege Camp adjacent to the target and starts the siege.
- A siege has an integer **attack progress** that swings up (toward the attacker) and down (toward the defender).
- The attacker wins when progress reaches the chunk's **difficulty threshold**; the defender wins when progress drops to the negative **defence threshold** (default **-5**, set by `Siege Defence Threshold`).
- Progress is driven by **PVP kills inside the battle zone** and by a **timer** that ticks progress toward the attacker.
- Winning captures (or clears) the chunk; the camp is consumed either way.

## The Siege Camp

The Siege Camp block is `warforge:siegecampblock`. Its recipe (overworld) is:

```
 N      N = Banner
FBC     F = Furnace   B = Bed   C = Chest
OOO     O = Cobblestone
```

Like the citadel, the camp is a multiblock column (camp + statue + a translucent marker on top) and is **effectively unbreakable and explosion-proof** — it is removed by being picked up, or automatically when the siege ends.

### Placing a camp

To place a camp you must:

- be an **Officer** or higher of your faction,
- have fewer than the maximum number of active siege blocks (hard limit: **3 per faction**),
- place it in an **unclaimed, uncontested** chunk on a solid surface,
- place it so there is **at least one adjacent enemy claim** to siege, within the vertical siege distance (default **40** blocks of Y difference).

The camp claims its own chunk for your faction while it stands. To pick a camp back up, **sneak + right-click** it.

## Starting a siege

There is no separate war declaration — placing a camp and choosing a target *is* the declaration.

1. **Right-click the placed camp** (you must be an Officer+). This opens the **Siege Target Map**.
2. The map shows a 5×5 grid of chunks around the camp. Only chunks **directly adjacent** to the camp (including diagonals) that contain an enemy claim are attackable. Tooltips show ownership, claim type, vein/ore data, and the total attack time.
3. **Click an attackable chunk** to start the siege.

The server then checks, among other things, that:

- you are an Officer+ and your faction owns the camp,
- your faction is not on **siege cooldown** (after a failed siege; skipped if your faction has momentum),
- the target chunk has a defending faction with a real claim block,
- the defender is **not offline-protected** (offline raid protection, default on, default 24 hours),
- the target is not already under siege,
- the target is not inside a **conquered-chunk grace period**,
- the target faction is **not an ally**, and you are not in an active **truce** with them (see [Alliances](alliances.md)).

If all checks pass, the siege starts and a server-wide message announces "X started a siege against Y."

## Declaring a siege from the map (no camp)

Placing a physical camp is no longer the only way to start a siege. You can also **declare** one straight from the **Territory Map**, which consumes a Siege Camp block from your inventory instead of placing it. Both methods coexist; this one is handy when you'd rather not haul a camp to the front line.

1. Open the Territory Map and click **Declare Siege**. The map enters target-selection mode.
2. **Click an enemy claim** to choose the chunk you want to siege. A second map opens, **centered on that target**.
3. On the second map, **click the chunk you want to launch the siege from**. Valid launch chunks are highlighted; they must be within a configurable range of the target (**UI Siege Declaration Max Range**, default **4** chunks).
4. One **Siege Camp block** is consumed from your inventory and the siege begins, anchored logically at your chosen launch chunk — no block is placed in the world.

A declared siege runs through the **same checks** as a camp siege (faction ownership, offline protection, already-sieged, conquered grace, ally/truce immunity) and the target must be a sieg-able claim. Its **battle zone** is centered on the launch chunk, and progress works exactly like a camp siege (timer + PVP).

Because the second map can be centered on terrain you have never visited, the server streams the surrounding terrain colours to you so the map is still readable for distant targets.

A few server toggles shape this flow:

- **Allow UI Siege Declaration** (default **on**) — turn the whole feature off to force physical camps.
- **UI Siege Declaration Max Range** (default **4**) — how far the launch chunk may be from the target, in chunks.
- **UI Siege Requires Attacker Presence** (default **on**) — when on, a declared siege still needs an attacker to stay near the launch chunk; if the area is abandoned for the attacker desertion timer, the siege fails, just like a camp.

> A camp-less siege has no physical camp to defend, so the attacker-presence rule above is what keeps "fire-and-forget" sieges in check. Tune it (and the range) to taste.

## Fighting the siege

### Win and loss conditions

Progress is a single signed number:

- **Attacker wins** when attack progress reaches the chunk's **difficulty threshold**.
- **Defender wins** when attack progress falls to **-(defence threshold)** — the defenders must swing the siege that many points in their favour (default **5**, set by `Siege Defence Threshold`).

By default a side wins **the instant** its goal is reached, so a single kill can end a siege mid-timer. Turn `Siege Ends On Goal Reached` off to instead settle the outcome only when the **siege timer next elapses**: reaching the goal early no longer ends the siege, so a lead can still be contested until the tick.

The attacker threshold is the chunk's **base difficulty** plus **extra difficulty**:

- **Base difficulty** = the defending claim's defence strength (Basic 5, Reinforced 10, Citadel 15).
- **Extra difficulty** comes from the defenders' strength around the chunk:
  - **+1 per online defender** (capped at +5),
  - **+** attacker claim attack-strength and **-** defender claim support-strength from the four chunks adjacent to the target,
  - plus contributions from any special claim-strength tiles the defender has nearby.

So a Reinforced or Citadel chunk surrounded by defender support claims and defended by online players is far harder to take than an isolated basic claim.

### The on-screen siege bar

While you're near an active siege a status bar appears (position set by `Siege status position`). It shows both factions in their own colours — **defender** (shield) on the left, **attacker** (axe) on the right — with a point track running from the defender's win threshold on the left, through the centre, to the attacker's win threshold on the right. A marker shows the current standing, notches mark each point, and the readouts show **"N to defend" / "N to win"** and the time until the next timer tick. On very long sieges, when there are too many points to show individually, the track collapses to a compact **counter** (e.g. `12 / 30`).

### What pushes progress

**PVP kills inside the battle zone:**

- An **attacker killing a defender** moves progress **+1** toward the attacker.
- A **defender killing an attacker** moves progress **-1** toward the defender.

Kills only count when they happen inside the siege's battle zone (see below).

**Kill detection mode** (`Kill Detection Mode`, default `PRECISE`) controls how strict this is. In `PRECISE` mode an opposing player must land the kill in the zone for it to count. In `SIMPLE` mode **any** participant death inside the zone counts toward the siege — including fall damage, mobs and other environmental deaths — so a defender who dies to fall damage hands the attackers a point.

**The siege timer** (enabled by default):

- Each siege has a countdown. When it elapses, progress ticks **+1** toward the attacker and the timer resets.
- The timer length depends on your faction's **siege momentum** — see below. Higher momentum means a shorter timer, so progress accrues faster.

This means an attacker who keeps a presence and wins fights will steadily advance even if the defenders log off; conversely, defenders who win the fights can push progress to the defence threshold and break the siege.

**Anti-stall escalation** (`Siege Stall Escalation Threshold`, default 10): on long sieges (a high `Siege Defence Threshold`), if the defenders build a big lead and then stop fighting, the timer punishes the stall. Once the defenders' lead passes the escalation threshold, each consecutive timer tick **with no kills** doubles the attacker's swing (1 → 2 → 4 → 8 …). A kill by either side resets it to the base swing. This stops defenders from parking on a lead and running out the clock; it never triggers on short/default sieges, where the defence threshold is at or below the escalation threshold and the lead can't reach it.

### Presence and desertion

Both sides must keep someone in the fight:

- If **no attacker** is within the attacker radius of the camp, an attacker-desertion timer runs. If it fills (default ~180 s), the siege **fails** (attacker loss).
- If **no defender** is within the defender radius, a defender-desertion timer runs. If it fills (default ~300 s), the siege **passes** (attacker win).

Players popping in and out don't instantly reset these timers — they drain gradually, to prevent abuse. There is also **live-quit** handling: if the defenders who were present all go offline mid-siege, an offline timer (default 15 minutes) runs out and the attacker wins.

> An attacker win is held back while any attacking camp still has a running desertion timer — you have to actually be there to claim the win.

### Siege momentum

Momentum is a per-faction state (0–4) that rewards winning streaks:

- Winning an attack **increases** your momentum (up to 4); losing **resets** it to 0. It also expires after a duration (default 60 minutes) of not being refreshed.
- Higher momentum shortens the per-siege timer, so each siege resolves faster:

| Momentum | Timer per cycle |
|---|---|
| 0 | 15:00 |
| 1 | 10:00 |
| 2 | 8:30 |
| 3 | 5:00 |
| 4 | 2:30 |

A faction with momentum can also start new sieges without waiting out the post-failure cooldown.

## The battle zone and its protections

Each siege has a **battle zone** — a square area of chunks around the attacking camp. Its radius is configurable (`Battle Square Chunk Radius From Siege`, default 1 chunk) and is now stored **per siege**, so it can vary between sieges. PVP kills only count toward the siege inside this zone.

Inside the battle zone, attackers operate under restricted permissions rather than a free-for-all:

- Attackers **cannot freely break or place** normal blocks.
- Attackers **can interact with and use** things (buttons, items, etc.).
- **Explosions are allowed**, and attackers may place breaching tools: **TNT, end crystals, cobwebs, and torches**.
- Even with block-breaking off, attackers may break a small whitelist: **torches, the siege camp block, and `gregtech:machine`** blocks. (GregTech machines are a deliberate exception so they can be taken/disabled during a siege.)

Defenders, by contrast, may freely modify their own claims that are inside the battle zone but not the directly besieged chunk, so they can fortify while defending.

The net effect: attackers breach with explosives and the few whitelisted blocks rather than by strip-mining the defender's base, while the rest of the defender's territory stays protected.

## How a siege ends

### Attacker wins

- The defender **loses the claimed chunk**.
- The besieged chunk and the camp chunks enter the attacker's **conquered-chunk grace period** (default 1 hour), during which only the winning faction may build/claim there.
- If **Siege Captures** is enabled (default off), the lost chunk is **converted** into a claim for the attacker. With it off, the chunk simply becomes unclaimed wilderness.
- The attacker gains notoriety (+7 by default) and momentum.

> If the chunk lost is the defender's **Citadel** chunk, the whole defending faction is **defeated and disbanded**, and its insurance is unlocked to that faction's leader.

### Defender wins

- The defender **keeps the chunk**.
- The chunk and camp chunks enter the defender's **conquered-chunk grace period** (default 2 hours), during which the chunk can't be re-sieged or claimed.
- The defender gains notoriety (+10 by default); the attacker's momentum is reset to 0.

Either way, the attacking camp(s) are **consumed** when the siege concludes.

### Conquered chunks

Freshly contested chunks enter a grace period after the siege ends. While a chunk is "conquered":

- other factions cannot place claims there,
- no new siege can be started against it.

Conquered chunks are rendered with their own distinct border on the map so players can see the contested area and who currently holds the grace.

## Admin commands

Siege commands are **operator only**, under `/f siege` (alias `/f sieges`):

- `/f siege list` (alias `l`) — list active sieges as `chunkX, chunkZ, dim; Attacker-Defender`.
- `/f siege terminate <chunkX> <chunkZ> <dim> [WIN|LOSE|NEUTRAL]` (alias `t`) — force-end the siege on that defending chunk. The outcome is from the **attacker's** perspective; `WIN`/`LOSE` apply the normal rewards and grace periods, `NEUTRAL` just cancels it with no rewards. Default is `NEUTRAL`.

`/f time` shows how long until the next siege progress advance.

## Related configuration

All in `config/warforge.cfg`, category **Sieges** (full list in the [Configuration Guide](configuration.md)). Key defaults:

| Option | Default | Effect |
|---|---|---|
| `Battle Square Chunk Radius From Siege` | 1 | Battle-zone radius (per-siege). |
| `Attacker Square Chunk Radius From Siege` | 1 | How close attackers must stay to avoid desertion. |
| `Defender Square Chunk Radius From Siege` | 15 | How close defenders must stay to count as defending. |
| `Maximum Vertical Siege Radius` | 40 | Max Y difference between camp and target. |
| `Siege Camp Max Count Per Faction` | 3 | Configured camp cap (placement is enforced at 3). |
| `Attacker / Defender Desertion Timer` | 180 s / 300 s | Time before an absent side loses/wins. |
| `Attacker / Defender Conquered Chunk Grace Period` | 1 h / 2 h | Grace period after an attacker / defender win. |
| `Siege Swing Per Defender/Attacker Death` | 1 / 1 | Progress per kill. |
| `Siege Defence Threshold` | 5 | Points the defenders must swing to win (attacker wins at the chunk difficulty). |
| `Siege Ends On Goal Reached` | on | End the instant a goal is reached, vs only at the next timer tick. |
| `Kill Detection Mode` | PRECISE | `PRECISE` = confirmed PVP kills only; `SIMPLE` = any death in the zone counts. |
| `Siege Stall Escalation Threshold` | 10 | Defender-lead point past which kill-less ticks double the attacker swing. |
| `Enable Per-Siege timer` | on | Use the per-siege countdown timer. |
| `Siege Captures` | off | Whether an attacker win converts the chunk into their claim. |
| `Siege momentum duration` | 60 min | How long momentum lasts. |
| Offline raid protection | on, 24 h | Blocks sieges against fully-offline factions. |
| `Allow UI Siege Declaration` | on | Allow declaring camp-less sieges from the map. |
| `UI Siege Declaration Max Range` | 4 | Max chunks between the launch chunk and the target. |
| `UI Siege Requires Attacker Presence` | on | Camp-less sieges still need an attacker near the launch chunk. |

Sieges interact heavily with the protection config sections (`SiegedFriend`, `SiegedFoe`, `WarFriend`, `WarFoe`, `ClaimDefended`, etc.), which control exactly what each side can break, place, and interact with inside the battle zone. Restart the server after changing any of these.

See also: [Factions, Citadels and Upgrades](factions.md), [Alliances](alliances.md), [Command Reference](commands.md), and [The Vein System](veins.md).
