# FOB System

## Overview

A Forward Operating Base (FOB) is a special one-chunk structure your faction deploys in enemy or
neutral territory during a siege. Unlike a normal claim, a FOB does not give your faction ownership
of that land. Instead it provides a warp point that any member of your faction can use to teleport
directly to the FOB while your faction is an active participant in a siege (either as attacker or
defender).

FOBs are tightly tied to the siege system: warp tickets refill each time the siege timer resets,
and enemies who hold your FOB long enough can destroy it.

---

## Placing and Establishing a FOB

### What block to use

The FOB block is found in the WarForge creative tab. Any player can carry and place it like a
normal block, but the server enforces these conditions at placement:

- The chunk must be in a dimension that allows claiming.
- The chunk must not already be claimed by any faction.
- The chunk must not be currently contested.
- The chunk must not already contain a FOB.

### Activating the FOB

Placing the block alone does not activate it. You must establish it through its GUI:

1. Right-click the FOB block as an Officer (or server operator) of your faction. Regular members
   cannot establish a FOB.
2. Enter a name for the FOB (up to 32 characters) in the text field.
3. Click "Establish."

On establishment, a decorative structure spawns around the block: a cross-shaped stone-brick floor
with sandstone corner pillars and a faction flag pole. The chunk cannot be claimed by anyone else
while the FOB exists.

After establishment, only members of the owning faction can open the FOB's GUI. Other players are
turned away silently.

Your FOB and its remaining tickets are saved automatically.

---

## Warp Tickets

Each FOB has a pool of warp tickets. Tickets are consumed when you warp and refilled over the
course of a siege.

### Spending tickets

- A basic warp costs 1 ticket.
- Warping while riding a vehicle costs 1 ticket plus an additional amount depending on the vehicle
  type (configured per vehicle by the server admin).
- Tickets are deducted the moment you start the warp countdown. If the warp is cancelled because
  you moved, your tickets are refunded.

### Earning tickets

Tickets regenerate automatically each time the siege timer resets. The amount added per reset is
set by the server. Tickets can never exceed the FOB's maximum cap.

The maximum cap is also refreshed on each regen cycle, so any changes the server admin makes to
the cap take effect on the next siege tick.

---

## Warping to a FOB

### How to warp

Open the FOB block GUI by right-clicking it, or use the FOBs tab in your Faction Manager screen
(see below). Click the "Warp" button.

### Countdown

After a successful warp request, a countdown begins (default: 10 seconds). You receive a chat
message each second counting down. Do not move during the countdown. If you move, the warp is
cancelled and your tickets are refunded.

When the countdown completes, you (and any vehicle you are riding, along with its other passengers)
are teleported to the FOB. Cross-dimension warps are supported.

### Conditions that block a warp

The warp request will fail if any of the following are true:

- Your faction is not currently in an active siege (as attacker or defender).
- There are enemies standing on or above the FOB block at the time of your request.
- The FOB does not have enough tickets to cover the warp cost.
- You already have a warp pending.

You will receive a chat message explaining which condition blocked the warp.

---

## Enemy Hold and FOB Destruction

Enemies can destroy your FOB by standing on it and holding it long enough.

While enemies are standing above the FOB block and no member or ally of your faction is present,
a hold timer counts up each game tick. The hold threshold is tied to the siege momentum level: the
higher the attacker's momentum, the shorter the time needed to destroy the FOB.

If a member or ally of your faction returns to stand above the FOB block, the hold timer counts
back down toward zero.

When the hold timer reaches the threshold, the FOB is destroyed: the decorative structure is
removed, the chunk is freed, and the FOB entry disappears from your faction's list.

If your faction wins the siege as defender, all hold timers on your FOBs reset to zero.

Note: allies count as defenders for hold-timer purposes, but truce partners do not.

---

## FOBs Tab in the Faction Manager

Open your Faction Manager and switch to the FOBs tab. All faction members can view this tab
(not just officers).

The tab shows a scrollable list of your faction's FOBs. Each row displays:

- The FOB's name. Hover over it to see the block coordinates.
- The current ticket count out of the maximum (shown in green when tickets are available, orange
  when empty).
- A "Warp" button, enabled when you are eligible to warp to that FOB.

Click "Warp" to start the countdown for that FOB. The same eligibility checks described above
apply.

If your faction has no FOBs yet, the tab shows "No FOBs established yet."

---

## Server Admin Configuration

All FOB settings are in the `[Claims]` section of the server config. If per-level citadel upgrades
are enabled on your server, the upgrade level values override the flat settings below.

| Config key | Default | Effect |
|---|---|---|
| `Max FOBs Per Faction` | 3 | How many FOBs a faction can have at the same time. |
| `FOB Ticket Limit` | 5 | Maximum warp tickets each FOB can hold. |
| `FOB Ticket Regen Per Siege Tick` | 1 | Tickets added to each FOB per siege timer reset. |
| `FOB Warp Ticks` | 200 | Countdown length before a warp fires (200 ticks = 10 seconds). |
| `FOB Block Breakable` | false | If true, the central FOB block can be mined by hand at a fixed rate, regardless of tool. |
| `FOB Vehicle Ticket Cost` | (empty) | Extra ticket cost per vehicle type, in the format `namespace:vehicle_id=extraCost`. |

Example config snippet:

```toml
[Claims]
    "Max FOBs Per Faction" = 3
    "FOB Ticket Limit" = 5
    "FOB Ticket Regen Per Siege Tick" = 1
    "FOB Warp Ticks" = 200
    "FOB Block Breakable" = false
    "FOB Vehicle Ticket Cost" = ["superb_warfare:tank=2"]
```

FOB chunks also respect the minimum distance rule between opposing factions, the same as normal
claims.
