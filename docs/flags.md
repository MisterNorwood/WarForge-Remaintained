# Faction Flags

## Overview

Every faction can choose a flag that is displayed on the waving banner attached to each of their claim poles, citadel poles, and FOB poles. The banner is visible to all players with line of sight to the pole.

There are two kinds of flags:

- Default flags: solid-colour flags generated from a colour name or hex value (for example, red or ff8800).
- Custom flags: PNG images that a server admin places on the server. They are automatically sent to every connecting client. Players do not need to install anything on their side.

Once a faction chooses a flag the choice is permanent and cannot be changed.

---

## Choosing a flag in-game

Only faction officers can set the faction flag, and it can be chosen only once per faction.

1. Open your citadel block's UI.
2. In the "Faction Flag" panel, click "Choose".
3. A flag selection screen opens, listing all available flags with a live preview of each.
4. Click the flag you want, then confirm. The flag is applied to every pole owned by your faction immediately.

---

## Adding a custom flag (server admin guide)

Custom flags are server-side files. You place the image on the server and the server sends it to every connecting client automatically. Players do not need to install or download anything manually.

### Step 1 - Create the flags folder

In the server's working directory (the folder that contains `server.properties`), create this folder if it does not exist:

```
resources/warforge/flags/
```

### Step 2 - Add your PNG file

Place a PNG image in that folder. The filename without the `.png` extension becomes the flag's name. For example:

```
resources/warforge/flags/my_clan.png
```

This flag will appear in-game as `my_clan`.

Rules for the image:

- Format: PNG is strongly recommended.
- Maximum file size: 2 MB (measured after the server processes it, so keep your images well under this).
- Minimum dimension: 8 pixels on each side.
- Maximum dimension: 512 pixels on each side.
- Aspect ratio: neither side may be more than twice the length of the other (so a 256x128 px image is fine, but a 512x64 px image is not).
- Recommended size: 64x64 px to 256x128 px. A standard horizontal flag shape such as 128x64 px works well and produces the largest visible banner on the pole.
- All image files must be placed directly inside `resources/warforge/flags/`. Sub-folders are not scanned.

Images that do not meet these requirements are skipped and will not appear in-game. See the Troubleshooting section for how to diagnose this.

### Step 3 - Allow the flag in the config

Open `warforge-server.toml`. Find (or add) the following section:

```toml
[Claims]
"Available Custom Flags" = ["*"]
```

The default value `["*"]` allows all valid images in the flags folder automatically, so if this is already set you do not need to change anything.

If your server uses a restricted list instead of `"*"`, add your flag's base name (without `.png`) to the list:

```toml
[Claims]
"Available Custom Flags" = ["my_clan", "another_flag"]
```

Glob wildcards are supported (for example, `"my_*"` allows all flags whose names start with `my_`). The check is case-insensitive.

### Step 4 - Restart the server

The flags folder is read once when the server starts. There is no live-reload command. After placing the file and updating the config, restart the server.

Players who were already connected before the restart will receive the new flag the next time they log in.

### Step 5 - Select the flag in-game

Follow the in-game steps described in the "Choosing a flag" section above. Your new flag will appear in the selection list alongside any other available flags.

---

## Worked example

You want to add a flag called `red_dragon` using a 128x64 pixel PNG.

**On the server, place the file here:**

```
server/
  resources/
    warforge/
      flags/
        red_dragon.png
```

**In `warforge-server.toml`** (only needed if your allowlist is not `"*"`):

```toml
[Claims]
"Available Custom Flags" = ["red_dragon"]
```

Restart the server. If the image passed validation, no warning will appear in the server log for `red_dragon.png`.

**In-game:** open the citadel UI, click "Choose" in the flag panel, and select `red_dragon` from the list. The banner on every pole owned by your faction will immediately display the flag.

---

## Troubleshooting

### The flag does not appear in the selection list

- The server has not been restarted since the file was added. Restart the server.
- The file is not directly inside `resources/warforge/flags/`. Move it out of any sub-folder.
- The filename has a typo, or uses a different extension. Only PNG files with a `.png` extension are picked up.
- The flag's name is not in `Available Custom Flags` in `warforge-server.toml` (when the list is not `"*"`). Add the name (without `.png`) and restart.
- The image failed validation. Check the server log for a warning about the flag's filename being skipped. Common causes: the image is too large, a dimension is outside the 8-512 px range, or the aspect ratio exceeds 2:1.

### The flag appears in the list but the banner is invisible

- The player connected before the last server restart and did not receive the new flag. The player should disconnect and reconnect.

### The flag is invisible on the pole even after selection

- The player who placed the claim may have done so before the flag was chosen, and the pole may need a resync. The player should relog, which triggers a resync from the server.
- If the player joined before the flag file was added to the server, they must reconnect so the server can send them the flag image.

### The banner shape looks unexpected

The banner on the pole fits the image proportionally. A square image produces a square banner, and a wide image produces a wide, short banner. For the largest banner area on the pole, use an image with an aspect ratio close to 3:2 (for example, 192x128 px or 128x85 px).

### The image passes the dimension check but is still rejected

The server re-encodes the image after processing it. If a large image with many unique colours produces a processed file larger than 2 MB, it is rejected. Reduce the image dimensions or simplify the colours.
