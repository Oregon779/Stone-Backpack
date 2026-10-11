# Changelog

All notable changes to StoneBackpack are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versions before 1.5.0 are listed in the Modrinth version history.

## [1.5.0]

> **StoneBackpack now requires Paper 26.2 or newer and Java 25.** Servers on older Paper or Java versions should stay on 1.4.3.

### Added
- **Backups:** the backpack of every online player is backed up every 60 seconds to `plugins/StoneBackpack/backups/`, including the final state of players who logged off since the last run. Only the latest state per player is kept. Configure it with `backups.enabled` and `backups.interval-seconds` (minimum 10).
- **Backup menu** `/stonebackpack backups` (permission `stonebackpack.admin`): lists all online players, shows each player's latest backup read-only (time, age, used slots) and copies its contents into shulker boxes in your inventory, ready to hand back to the player. Every copy is logged in the console.
- The startup log now shows whether backups are active and how often they run.
- New message `general.load-failed`, shown when a backpack file can't be read.

### Changed
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
- Replace the jar and restart. Backpacks and settings are kept; the new `backups` options and texts are added to `config.yml` and the English or German `messages.yml` automatically.
- When options are added, these files are rewritten in the default layout: your values stay, but your own comments are removed.
- Using your own translation? Copy the new `backups.*` and `general.load-failed` texts from `languages/en/messages.yml`.
