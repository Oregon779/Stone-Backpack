# Changelog

All notable changes to StoneBackpack are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions before 1.5.0 are listed in the Modrinth version history.

## [1.5.0] - 2026-10-31

> **StoneBackpack now requires Paper 26.2 or newer and Java 25.** Servers on older Paper or Java versions should stay on 1.4.3.

### Added
- **Backpack pages:** up to 10 pages per backpack, unlocked with the new permissions `stonebackpack.pages.2` to `stonebackpack.pages.10` (configurable under `backpack.page-permissions`, default for everyone `backpack.default-pages: 1`). The arrow in the bottom right slot opens the next page, the one in the bottom left slot goes back. The title shows the page (`backpack.page-title`), and an item on the cursor comes along to the other page.
- No item is lost through the arrows: an item lying where an arrow appears, such as the bottom right slot of an existing backpack when a second page is unlocked, moves to the next page.
- Changing a page permission applies on the next open, without rejoining. When pages are removed, their items go to the new **item storage**, and the player is told how many item stacks were stored.
- **Item storage** `/stonebackpack storage` (permission `stonebackpack.admin`): lists every player with stored items, online or offline, and shows their items. Admins can take single items, give everything straight back to an online player (what doesn't fit stays stored) or take everything as shulker boxes. Items are always moved, never copied, and every action is logged in the console.
- **Backups:** the backpack of every online player is backed up every 60 seconds to `plugins/StoneBackpack/backups/`, including all pages and the final state of players who logged off since the last run. Only the latest state per player is kept. Configure it with `backups.enabled` and `backups.interval-seconds` (minimum 10).
- **Backup menu** `/stonebackpack backups` (permission `stonebackpack.admin`): lists all online players, shows each player's latest backup read-only (time, age, used slots) and copies its contents into shulker boxes in your inventory, ready to hand back to the player. Every copy is logged in the console.
- The startup log now shows whether backups are active and how often they run.
- New options `backpack.default-pages`, `backpack.page-permissions`, `backpack.page-title`, `backpack.page-button-material`, `backpack.sounds.page` and `backups.*`, and new texts for pages, the item storage, backups and `general.load-failed` (shown when a backpack file can't be read). All of them are added to existing files automatically.

### Changed
- **Config updates keep your file as it is:** new options are inserted into `config.yml` and `messages.yml` at the same place as in the default file, together with their explanation comments. Your values, comments, blank lines, option order and unknown options are no longer touched, and entries you removed from `backpack.size-permissions` or `backpack.page-permissions` stay removed.
- The bundled `config.yml` now explains every option in comments, and has a `config-version` so future versions can carry renamed or moved options over automatically.
- Before a config or message file is changed, the previous version is saved as `<file>.bak`. The file is written safely, and a file with a syntax error is left untouched (the console names the line).
- Own translations get new texts added in English instead of showing the bare message key.
- `/backpack <your own name>` now opens your own backpack without needing `stonebackpack.others`.
- The item blocklist now also covers bundles: blocked items and the backpack item can no longer be smuggled in inside a bundle or placed out of one.
- Text that comes from players or from Modrinth (for example a typed player name) is shown as plain text in messages, so it can't add colors or click actions.
- Backpacks of offline players are unloaded from memory as soon as nobody has them open, instead of staying loaded until the next restart.
- Better performance on large servers: disk reads and writes run on two dedicated background threads, autosaves and backups are spread over one second instead of a single tick, inventory clicks no longer copy chest contents to check them, and recognizing the backpack item on right-click is cheaper.

### Fixed
- **Item loss on crash:** a crash or kill while a backpack was being saved could leave an empty file behind. Files are now written to a temporary file first and then swapped in.
- **Item duplication after `/reload`:** backpacks that stayed open during a reload were no longer protected or saved, so items could be duplicated. Open backpacks are now closed and saved first, and a shutdown waits until every save is written.
- A save could capture a backpack while a player was still moving items, resulting in an inconsistent save.
- A corrupt backpack file was silently replaced by an empty backpack. It is now kept as `<uuid>.yml.corrupt-<timestamp>` and logged, so the items can be restored from the backup. Files that can't be read at all are never overwritten.
- `/backpack` sometimes did nothing when two players opened the same backpack at the same moment.
- With `give-on-first-join`, players who never opened their backpack received a new backpack item on every join.
- Loading a backpack and the update notification checked permissions off the main thread, and the update checker's connection was never closed on reload.

### Upgrading from 1.4.3
- Replace the jar and restart. Backpacks and settings are kept. The new options and texts are added to `config.yml` and `messages.yml` automatically, with your values and comments untouched; the previous files are kept as `.bak`.
- Existing backpacks become page 1 with every item where it was. Nobody gets extra pages until you hand out `stonebackpack.pages.*`. When a player gets a second page, the item in the bottom right slot moves to page 2 to make room for the arrow.
- Going back to an older version keeps only page 1 - have players empty their other pages first.
