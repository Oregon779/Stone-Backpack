# StoneBackpack Wiki

Personal backpacks with permission-based sizes for Paper servers, by **Stone Plugins**.

> **Requirements:** Paper (or a Paper fork) **26.2 or newer** and **Java 25**. Spigot and Folia are not supported.

---

## Contents

1. [Overview](#overview)
2. [Installation](#installation)
3. [Updating](#updating)
4. [Commands](#commands)
5. [Permissions](#permissions)
6. [Backpack Sizes](#backpack-sizes)
7. [The Backpack Item](#the-backpack-item)
8. [Blocked Items](#blocked-items)
9. [Backups and Restoring Items](#backups-and-restoring-items)
10. [World Restrictions](#world-restrictions)
11. [Configuration Reference](#configuration-reference)
12. [Messages and Languages](#messages-and-languages)
13. [Data Storage and Safety](#data-storage-and-safety)
14. [Update Checker](#update-checker)
15. [Performance Tips for Large Servers](#performance-tips-for-large-servers)
16. [FAQ and Troubleshooting](#faq-and-troubleshooting)
17. [Building from Source](#building-from-source)

---

## Overview

StoneBackpack gives every player a personal backpack that is saved permanently. It lets you:

- open your own backpack with `/backpack` (or `/bp`, `/rucksack`)
- give players **1 to 6 rows** (9 to 54 slots) through **permissions** - the backpack grows automatically when a player gets a bigger size
- hand out a **physical backpack item** that opens the backpack with a right-click
- block items that must not go into a backpack - by default all **shulker boxes**, and always the backpack item itself
- let admins **open, inspect and edit** the backpack of any player, online or offline
- keep a **backup** of every online player's backpack every 60 seconds and restore it from a **GUI**, packed into shulker boxes
- limit backpacks to certain worlds, add a command cooldown and choose the open/close sounds
- get notified in-game and in the console when a **new plugin version** is available

All texts ship in **English and German**, and colors work with `&` codes, hex colors and MiniMessage at the same time.

---

## Installation

1. Download the latest `StoneBackpack-X.X.X.jar`.
2. Put it into your server's `plugins/` folder.
3. Start (or restart) the server.
4. On the first start, these files and folders are created in `plugins/StoneBackpack/`:
   - `config.yml` - all settings
   - `languages/en/messages.yml` and `languages/de/messages.yml` - all texts
   - `playerdata/` - one file per player's backpack
   - `backups/` - the latest backup of each backpack

The console prints these lines on startup, for example:

```
Backups: saving every online player's backpack every 60s to backups/.
StoneBackpack v1.0.0 enabled (language: en).
```

**Quick start**

```
/backpack                          open your own backpack (3 rows by default)
/lp group vip permission set stonebackpack.size.6 true
                                   VIPs get the biggest backpack (6 rows)
/stonebackpack backups             see the latest backup of every online player
/stonebackpack reload              after editing config.yml or messages.yml
```

New installations use English. Switch to German with `language: de` in `config.yml`.

---

## Updating

Updating is a file swap:

1. Stop the server.
2. Delete the old jar from the `plugins/` folder.
3. Put the new jar in.
4. Start the server.

Your settings and all backpacks are kept. On startup, StoneBackpack checks `config.yml` and the `messages.yml` of the selected language for options that were added in the new version and adds only those. Existing values are never changed.

When options are added, the file is rewritten in the default layout: your values stay, but **your own comments and options the plugin doesn't know are removed**. Keep notes outside these files.

---

## Commands

**Players**

| Command | Description | Permission |
|---|---|---|
| `/backpack` | Open your own backpack | `stonebackpack.use` |
| `/backpack <player>` | Open another player's backpack (online or offline) to view and edit it | `stonebackpack.others` |

Aliases: `/bp`, `/rucksack`

**Admin** - base command `/stonebackpack` (aliases: `/sbp`, `/sb`)

| Command | Description | Permission |
|---|---|---|
| `/stonebackpack give <player>` | Give the physical backpack item to an online player | `stonebackpack.admin` |
| `/stonebackpack inspect <player>` | Open any player's backpack (online or offline) to view and edit it | `stonebackpack.admin` |
| `/stonebackpack backups` | Open the backup menu - see [Backups and Restoring Items](#backups-and-restoring-items) | `stonebackpack.admin` |
| `/stonebackpack reload` | Reload `config.yml` and the messages, restart autosave, backups and the update checker | `stonebackpack.admin` |
| `/stonebackpack checkupdate` | Check Modrinth for a new version right now | `stonebackpack.admin` |
| `/stonebackpack help` | Show the commands you are allowed to use | everyone |

**Good to know**

- Opening someone else's backpack shows the **same, live inventory** as the owner sees. Changes apply immediately, even when both have it open at the same time.
- `/backpack <your own name>` simply opens your own backpack and does not need `stonebackpack.others`.
- `/backpack` itself needs `stonebackpack.use` - also for opening other players' backpacks. OPs can use everything.
- Offline players can be opened by name as long as they have joined the server before.
- The cooldown (`command.cooldown-seconds`, default 2) only applies to `/backpack`. `stonebackpack.bypass.cooldown` and `stonebackpack.admin` skip it.
- `/stonebackpack inspect` has no cooldown and ignores [World Restrictions](#world-restrictions), so admins can check backpacks everywhere. Apart from that, it opens the backpack exactly like `/backpack <player>`.
- The console can use `give`, `reload`, `checkupdate` and `help`. `/backpack`, `inspect` and `backups` need a player.
- Unknown subcommands show the help. Tab-completion only suggests what you may use.

**Examples**

```
/backpack
/bp Notch
/stonebackpack give Notch
/stonebackpack inspect Notch
/stonebackpack backups
/stonebackpack reload
```

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `stonebackpack.use` | Open your own backpack with `/backpack` or the backpack item | everyone |
| `stonebackpack.others` | Open and edit other players' backpacks with `/backpack <player>` | OP |
| `stonebackpack.admin` | All admin commands (`give`, `inspect`, `backups`, `reload`, `checkupdate`), update notifications, and it also acts as `others`, `bypass.cooldown` and `bypass.blocklist` | OP |
| `stonebackpack.bypass.cooldown` | Skip the `/backpack` cooldown | OP |
| `stonebackpack.bypass.blocklist` | Put [blocked items](#blocked-items) into a backpack anyway | OP |
| `stonebackpack.size.1` ... `stonebackpack.size.6` | Backpack with 1 to 6 rows (9 to 54 slots) - see [Backpack Sizes](#backpack-sizes) | nobody |

Examples with LuckPerms:

```
/lp group default permission set stonebackpack.size.2 true      everyone gets 2 rows
/lp group vip permission set stonebackpack.size.6 true          VIPs get 6 rows
/lp group mod permission set stonebackpack.others true          moderators can open other backpacks
/lp group default permission set stonebackpack.use false        disable backpacks for normal players
```

---

## Backpack Sizes

- Each size permission unlocks a number of rows: `stonebackpack.size.1` = 1 row (9 slots) up to `stonebackpack.size.6` = 6 rows (54 slots).
- If a player has several size permissions, the **biggest** one counts.
- Without any size permission, a player gets `backpack.default-rows` (default **3 rows**). Everybody always has a backpack.
- When a player gets a bigger size, their backpack **grows** the next time it is opened while they are online. All items stay where they are.
- Backpacks **never shrink** automatically. If a player loses a size permission, they keep their bigger backpack, so no items are lost.
- The permission names can be changed under `backpack.size-permissions` in `config.yml`, for example to use your rank permissions directly:

```yaml
backpack:
  size-permissions:
    2: rank.member
    4: rank.vip
    6: rank.mvp
```

---

## The Backpack Item

Besides `/backpack`, players can open their backpack with an item.

- **Right-click** (in the air or on a block) with the item in the main hand opens your own backpack. This needs `stonebackpack.use` and respects [World Restrictions](#world-restrictions). There is no cooldown for the item.
- Admins give the item with `/stonebackpack give <player>`. If the inventory is full, it is dropped at the player's feet.
- **Automatic item for new players:** set both `backpack.item.enabled: true` and `backpack.item.give-on-first-join: true`. Players who have never used a backpack get the item one second after joining - **only once**, even if they never open it.
- Look of the item: `backpack.item.material` (default `BUNDLE`), `name`, `lore` and `custom-model-data` (for resource packs, `0` = none).
- The item is recognized by an invisible tag, not by its name or material. Renaming it in an anvil doesn't break it, and changing `material` later only affects newly created items - old items keep working.
- `backpack.item.enabled` only controls the automatic item for new players. `/stonebackpack give` and right-clicking work in any case.

---

## Blocked Items

Items listed in `backpack.blocked-items` can't be put into a backpack. By default these are **all 17 shulker box colors**, so players can't store boxes full of items inside their backpack.

- The **backpack item itself** is always blocked, so a backpack can't contain a backpack.
- Blocked are all ways of putting items in: clicking, shift-clicking, number keys, the offhand key (F), dragging, and **bundles** that contain a blocked item.
- Players see a message instead. Players with `stonebackpack.bypass.blocklist` or `stonebackpack.admin` can store blocked items anyway.
- Use the item names from Minecraft (for example `TNT`, `ELYTRA`, `ENDER_CHEST`). Unknown names are ignored with a warning in the console.

```yaml
backpack:
  blocked-items: ["SHULKER_BOX", "RED_SHULKER_BOX", "TNT", "ENDER_CHEST"]
```

Items that were already in a backpack before you blocked them stay there and can be taken out.

---

## Backups and Restoring Items

StoneBackpack keeps a backup of every backpack, so admins can give players their items back after a mistake, a scam or a lost item.

**How backups are made**

- Every `backups.interval-seconds` (default **60**, minimum 10), the backpack of **every online player** is saved to `backups/<uuid>.yml` - also when the player hasn't opened it.
- Players who logged off since the last run are backed up with the state they left with.
- Each player has **one backup: the latest state**. Every run replaces the previous one. If a player loses items and more than one interval passes, the backup shows the backpack without them, so act quickly.
- Players who have never used their backpack have no backup (there is nothing to save).
- The first backup is made one interval after the server start or `/stonebackpack reload`.

**Restoring items**

1. Run `/stonebackpack backups`. A menu lists all **online players** (45 per page, sorted by name).
2. Click a player's head to see their latest backup. You can only look - items can't be taken out. The clock at the bottom shows when the backup was made and how many slots are used. Backpacks with 6 rows have a second page.
3. Click **Copy as shulker box**. The whole content is put into your inventory in shulker boxes (27 items each, so up to two boxes). If your inventory is full, the rest is dropped at your feet.
4. Give the shulker box(es) to the player.

Good to know:

- Copying doesn't change the player's backpack or the backup - it creates new items. Every copy is logged in the console with admin, player and backup time.
- The menu closes after copying, so a double-click can't create two copies.
- Shulker boxes that were in the backup (only possible with the blocklist bypass) are given to you directly, not packed into another shulker box.
- To turn backups off, set `backups.enabled: false` and run `/stonebackpack reload`. Existing backups can still be viewed.

---

## World Restrictions

Limit where backpacks can be opened with `worlds.mode` and `worlds.list`:

| Mode | Effect |
|---|---|
| `NONE` | Backpacks work everywhere (default) |
| `WHITELIST` | Backpacks only work in the worlds in `worlds.list` |
| `BLACKLIST` | Backpacks work everywhere except in the worlds in `worlds.list` |

```yaml
worlds:
  mode: BLACKLIST
  list: ["pvp_arena", "event_world"]
```

World names are not case-sensitive. The restriction applies to `/backpack` (also for other players' backpacks) and the backpack item. `/stonebackpack inspect` and the backup menu always work.

---

## Configuration Reference

All files are in `plugins/StoneBackpack/`. After changes, run `/stonebackpack reload`. Invalid values fall back to their defaults; misspelled item, sound, row and world-mode names are also reported in the console.

**`config.yml`**

| Setting | Default | Description |
|---|---|---|
| `language` | `en` | Language folder for all texts (`en`, `de` or your own) |
| `backpack.title` | `&8&l» &7{player}'s Backpack &8&l«` | Title of the backpack window, `{player}` = owner |
| `backpack.default-rows` | `3` | Rows for players without a size permission (1-6) |
| `backpack.size-permissions` | `1` to `6` → `stonebackpack.size.1` to `.6` | Which permission unlocks how many rows |
| `backpack.blocked-items` | all shulker boxes | Items that can't be stored - see [Blocked Items](#blocked-items) |
| `backpack.item.enabled` | `false` | Must be `true` for the automatic item on first join |
| `backpack.item.material` | `BUNDLE` | Material of newly created backpack items |
| `backpack.item.name` | `&6&lBackpack` | Display name of the item |
| `backpack.item.lore` | `["&7Right-click to open your backpack"]` | Lore lines of the item |
| `backpack.item.custom-model-data` | `0` | Custom model data for resource packs, `0` = none |
| `backpack.item.give-on-first-join` | `false` | Give new players the item once (needs `item.enabled: true`) |
| `backpack.sounds.open` / `close` | `BLOCK_ENDER_CHEST_OPEN` / `BLOCK_ENDER_CHEST_CLOSE` | Sounds when opening and closing, heard only by that player |
| `backpack.sounds.volume` / `pitch` | `1.0` / `1.0` | Volume and pitch of both sounds |
| `command.cooldown-seconds` | `2` | Cooldown between `/backpack` uses, `0` = none |
| `worlds.mode` | `NONE` | `NONE`, `WHITELIST` or `BLACKLIST` - see [World Restrictions](#world-restrictions) |
| `worlds.list` | `[]` | World names for `WHITELIST` / `BLACKLIST` |
| `data.autosave-interval-minutes` | `5` | Time between automatic saves of all loaded backpacks, minimum 1 |
| `data.save-on-close` | `true` | Save a backpack every time it is closed |
| `data.save-on-quit` | `true` | Save a backpack when its owner leaves |
| `backups.enabled` | `true` | Make regular backups - see [Backups and Restoring Items](#backups-and-restoring-items) |
| `backups.interval-seconds` | `60` | Time between backups, minimum 10 |
| `update-checker.enabled` | `true` | Check Modrinth for new versions |
| `update-checker.check-interval-minutes` | `60` | Time between checks, minimum 5 |

Leave `data.save-on-close` and `data.save-on-quit` on unless you have a reason. Without them, changes are only saved by the autosave, and a crash can lose up to `autosave-interval-minutes` of changes.

---

## Messages and Languages

- `languages/<language>/messages.yml` contains **all texts**: chat messages, help, backup menu and item names. English (`en`) and German (`de`) are included; `language` in `config.yml` selects the one that is used.
- If the selected language folder doesn't exist, English is used and a warning is logged.
- **New language:** copy `languages/en/messages.yml` to e.g. `languages/fr/messages.yml`, translate it, set `language: fr` and run `/stonebackpack reload`. New texts of future versions are only added automatically to `en` and `de`, so compare your file with the English one after updating. A missing text shows its key (for example `backups.empty`) instead.

**Colors** - all of these work in every text, even mixed:

| Format | Example |
|---|---|
| Legacy codes | `&a`, `&l`, `&r` |
| Hex colors | `&#FF00AA` |
| MiniMessage | `<red>`, `<bold>`, `<gradient:#9AA5B1:#FFB055>text</gradient>` |

**Placeholders**

| Placeholder | Used in |
|---|---|
| `{player}` | `general.player-not-found`, `backpack.opened-other`, `backpack.inspecting`, `backpack.gave-item`, `backups.none-yet`, `backups.empty`, `backups.copied`, `backups.shulker-name`, `backups.gui.backup-title`, `backups.gui.player-name`, and `backpack.title` in `config.yml` |
| `{seconds}` | `backpack.cooldown` |
| `{interval}` | `backups.none-yet` |
| `{stacks}` | `backups.copied` |
| `{index}` `{total}` | `backups.shulker-name` |
| `{page}` `{pages}` | `backups.gui.players-title`, `backups.gui.backup-title` |
| `{time}` `{age}` | `backups.gui.info-time`, `backups.gui.info-age` |
| `{used}` `{slots}` | `backups.gui.info-slots` |
| `{version}` `{current}` `{behind}` | `update.available` |
| `{count}` | `update.versions-behind` |

`prefix` is put in front of chat messages, except the help lines and the update notice (which has its own prefix in the text). Menu titles and item names are shown without it. When a message repeats something a player typed (for example an unknown player name), color codes and tags in it are shown as plain text, so nobody can add formatting or click actions to messages.

---

## Data Storage and Safety

Every backpack is stored in `playerdata/<uuid>.yml`, the backups in `backups/<uuid>.yml`. Don't edit these files while the server is running - a backpack that is loaded (its owner is online) would overwrite your edit.

- Backpacks are saved when they are closed, when the owner leaves and every `data.autosave-interval-minutes`. All disk work runs in the background, never on the main thread.
- A backpack stays in memory while its owner is online, so opening it again is instant. Backpacks of offline players are removed from memory as soon as nobody has them open (at the latest with the next autosave).
- Files are written to a temporary file first and then swapped in, so a crash or power loss can't leave a half-written, empty backpack behind.
- When the server stops or the plugin is reloaded, open backpack windows are closed and the plugin **waits until everything is saved**.
- If a backpack file can't be read because it is broken (for example after editing it by hand), StoneBackpack **keeps it** as `<uuid>.yml.corrupt-<timestamp>`, logs an error and gives the player an empty backpack instead of overwriting the data. Restore the items with `/stonebackpack backups`, or stop the server and repair the file.
- If a file exists but can't be read at all (for example missing file permissions), the player gets the message "This backpack could not be loaded" and nothing is overwritten.

---

## Update Checker

StoneBackpack checks [Modrinth](https://modrinth.com/project/stone-backpack) for newer versions: 5 seconds after startup and then every `update-checker.check-interval-minutes` (default 60). Everyone who is OP **or** has `stonebackpack.admin` is notified:

- **in chat** when a newer version is found and every time they join, as long as the plugin isn't updated
- **in the console** after every check

Console messages:

- Update found: `Update checker: a new version is available: 1.1.0 (you're on 1.0.0, 1 version(s) behind). Get it at https://modrinth.com/project/stone-backpack`
- No update: `Update checker: no new version available.`
- Modrinth not reachable: `Update checker: Modrinth responded with status 404 for project 'stone-backpack'.` or `Update checker: check failed (...)`

`/stonebackpack checkupdate` checks immediately. Turn the checker off with `update-checker.enabled: false`; the change applies after `/stonebackpack reload`.

---

## Performance Tips for Large Servers

StoneBackpack is designed for busy servers with several hundred players: all disk work runs on two background threads, autosaves and backups are spread over one second instead of hitting a single tick, and inventory clicks are checked without copying chest contents. The settings with the most effect:

- **Backups:** each run writes one small file per online player. On large servers or slow disks (HDD), raise `backups.interval-seconds` (e.g. to `120`-`300`). Keep in mind that a longer interval also means a longer window in which a lost item is still in the backup.
- **Autosave:** `data.autosave-interval-minutes` (default 5) is mainly a safety net for backpacks that stay open for a long time, and the only regular save if `save-on-close`/`save-on-quit` are turned off.
- **Cooldown:** `command.cooldown-seconds` limits how often a player can open the backpack by command.

To measure the real impact on your server, use the [spark](https://spark.lucko.me/) profiler.

---

## FAQ and Troubleshooting

**Will I lose backpacks or settings when updating?**
No. Backpacks are untouched, and new versions only add missing options. See [Updating](#updating).

**How do I give players a bigger backpack?**
Give them `stonebackpack.size.<rows>`, e.g. `stonebackpack.size.6` for 54 slots. See [Backpack Sizes](#backpack-sizes).

**I removed a size permission, but the player still has the big backpack.**
Backpacks never shrink, so no items get lost. To make one smaller, empty it first, stop the server and set `rows` in the player's file in `playerdata/`.

**A player lost items from their backpack. Can I get them back?**
Yes, if you are quick: `/stonebackpack backups`, click the player, then "Copy as shulker box". The backup only holds the latest state, so it has to happen within one backup interval (default 60 seconds) after the loss. See [Backups and Restoring Items](#backups-and-restoring-items).

**The backup menu says there is no backup for a player.**
Either the player has never used their backpack, or the first backup since the server start or `/stonebackpack reload` hasn't run yet. Wait one interval (default 60 seconds).

**Why doesn't a player appear in the backup menu?**
The menu only lists players who are online. Offline players' backups are kept in `backups/` and show up again when they join.

**Why can't players put shulker boxes into their backpack?**
They are blocked by default. Remove them from `backpack.blocked-items`, or give trusted players `stonebackpack.bypass.blocklist`. See [Blocked Items](#blocked-items).

**Can two people edit the same backpack at the same time?**
Yes. An admin and the owner see the same, live inventory - nothing gets duplicated or lost.

**The backpack item is a bundle - can players store items in it like a normal bundle?**
Yes, the default material `BUNDLE` keeps its normal bundle function in the inventory (right-clicking to use it is replaced by opening the backpack). If you don't want that, set `backpack.item.material` to another item, e.g. `CHEST` or `LEATHER`.

**New players don't get the backpack item.**
Both `backpack.item.enabled` and `backpack.item.give-on-first-join` must be `true`. Players who already have a backpack or already got the item don't get another one.

**Why can a player not open their backpack in some worlds?**
[World Restrictions](#world-restrictions) are active (`worlds.mode`). The message is "This feature is disabled in this world."

**The console says "Unknown material", "Unknown sound" or "Invalid row count".**
A name in `config.yml` is misspelled. The default is used until you fix it.

**The console says a backpack file "was corrupt and has been moved".**
See [Data Storage and Safety](#data-storage-and-safety) - the old file was kept as `<uuid>.yml.corrupt-<timestamp>`.

**Can I use `/reload` or plugin managers?**
It works - open backpacks are closed and saved first. A full restart is still the cleanest way to update.

**How do I translate the plugin?**
See [Messages and Languages](#messages-and-languages).

**Does this work on Spigot or Folia?**
No. StoneBackpack needs Paper 26.2 or newer.

---

## Building from Source

Requirements: **Java 25** and **Maven**.

```
mvn package
```

The plugin jar is created as `target/StoneBackpack-<version>.jar`. The build also runs the automated tests (JUnit and MockBukkit); `mvn package -DskipTests` skips them. The Paper API is downloaded from `repo.papermc.io`, the test libraries from Maven Central.
