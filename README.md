# 3DGames

[English](README.md) | [简体中文](README.zh-CN.md)

3D tabletop games for Minecraft, with physical boards, pieces, cards and dice. Includes saved solo progress, multiplayer rooms, practice bots, spectating and private hands.

One JAR supports Paper, Purpur, Spigot, CraftBukkit and Folia, with native Dialog menus and no client mods. CraftEngine, Vault, PlaceholderAPI and the resource pack are optional.

[Compatibility](#compatibility) · [Install](#install) · [Controls and menus](#controls-and-menus) · [Games](#games) · [Configuration](#configuration-and-languages) · [Resource pack](#optional-resource-pack) · [Build](#build) · [Source layout](#source-layout) · [License](#license)

> The refreshed [v1.0.0 release](https://github.com/GoldenEggOVO/3DGames/releases/tag/v1.0.0) includes the compatibility update below. Download its JAR or build the current source; the release notes link the exact source snapshot matching the refreshed assets.

## Compatibility

| Server | Tested Minecraft versions | Result |
| --- | --- | --- |
| Paper | 26.1.1, 26.1.2, 26.2, 26.3 | Supported |
| Purpur | 26.1.2, 26.2, 26.3 | Supported |
| Spigot / CraftBukkit | 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Supported |
| Folia | 26.1.2, 26.2, 26.3 | Supported |

Requires **Java 25**. As of 2026-11-16, the same JAR passed creation, gameplay and restart recovery across 20 platform/version combinations, together with 1,467 Java tests and 147 asset checks.

Spigot / CraftBukkit 26.1 and 26.1.1 were rebuilt from exact official historical commits; the current BuildTools entries with those names resolve to 26.1.2. Paper currently provides no 26.1 build, and Purpur / Folia provide no 26.1 or 26.1.1 builds. This table covers specific builds, not every historical build, snapshot or future version.

Paper / Purpur checks use proxy players with real worlds and entities; Spigot / CraftBukkit use actual ServerPlayer objects and in-memory connections. Folia 26.3 additionally passed two graphical clients through menus, pack downloads, cameras, directional input, cross-region actions and reconnect/restart recovery. The other Folia versions cover actual region entities and saved-game recovery. Automated checks and visual, audio and interaction acceptance are separate.

Server differences live in `Core/platform/`; menus and game rules are shared. Folia routes table work to its owning region and player work to the player's scheduler. Optional plugins must also support the selected server.

Some native asynchronous teleports on Folia do not fire `PlayerTeleportEvent`, a reproduced [upstream limitation](https://github.com/PaperMC/Folia/issues/490). Other plugins that cancel teleports through this event need separate checks. Replace JARs with a full server stop and restart; third-party hot-unload tools are outside the matrix.

## Install

1. Prepare Java 25 and a server from the table above, then [build](#build) the current plugin. Published versions are available from [Releases](https://github.com/GoldenEggOVO/3DGames/releases).
2. Stop the server and back up worlds and `plugins/3DGames/`.
3. Put one `3DGames-*.jar` in `plugins/`; do not install `original-*.jar`.
4. Start the server and run `/3dgames`. Choose Single Player or Multiplayer. Set `language: zh_CN` for Chinese.

The plugin alone provides vanilla models. For resource-pack models, optionally install CraftEngine; default `auto` mode selects mixed rendering and joins its unified pack. See [Optional resource pack](#optional-resource-pack).

## Controls and menus

The home page offers Single Player and Multiplayer. Menu settings contain the pack toggle. Both model modes share native Dialog text buttons: player counts and descriptions appear in tooltips, lists use native scrolling, the home footer closes, and subpage footers go back. Amounts use validated native inputs.

Sneak and right-click a table to open its room menu. Operate pieces, cards, dice and scoring cells directly on the table. Multiplayer hosts can wait for friends to join and ready up, or fill empty seats with bots and start. Room rules lock after creation.

| Game | Main controls |
| --- | --- |
| Board games | Select a piece, then a legal target; pointer and button feedback are private to the player |
| Mahjong | Hold the sprint key (Ctrl by default) for a fixed table view; release to exit |
| 2048 | Press sprint once for the fixed overhead view, slide with movement keys W/A/S/D, and press sprint again to exit |
| Mastermind | Select a current-column hole and a tray colour; right-click or scroll cycles colours. Orange means exact match, white means right colour elsewhere |
| Battleship | Left-click to select/place, right-click to rotate/remove; after setup, click the opposing sea to fire |
| Monopoly trades | Click green-marked properties to select or remove; cyan marks selected properties. Confirm the table draft before submitting |

Continue saved games from Single Player. Each game has its own save; Save and leave releases the table. Disconnects and shutdowns also save. Starting another game of the same kind requires confirmation before replacing progress.

## Games

| Game | Players | Physical presentation |
| --- | --- | --- |
| Xiangqi, Chess | 2 | 3D pieces on a mapped board |
| Gomoku | 2 | Black and white stones |
| Reversi | 2 | Two-sided pieces with flip animation |
| Connect Four | 2 | Upright rack, round stepped discs and column drops |
| Go | 2 | 9×9, 13×13 and 19×19 boards |
| Chinese Checkers | 2, 3, 4 or 6 | Star board and colored pieces |
| Draughts | 2 | 8×8 board with crowned kings |
| Ludo | 2–4 | Cross board, pawn figures, private move previews and a physical die |
| Color Eight | 2–5 | Native round table, overlapping private hands, wild-color buttons, draw/discard piles |
| Doudizhu | 3 | Private multi-card selection, bidding controls, public bottom cards after bidding |
| Liar's Bar | 2–4 | Face-down plays, challenges and match-local roulette |
| Yacht Dice | 2–4 | Five dice, three rolls, keep slots and twelve scoring categories |
| Texas Hold'em | 2–6 | Community cards, betting controls, dealer button and compact chip stacks |
| Mahjong | 4 | Original tile patterns, standing private hands, public rivers and exposed melds |
| 2048, Klondike | 1 | Direction controls, card sequences and saved solo progress |
| Mastermind | 1–2 | Six repeatable colours, ten guesses and two-player role exchange |
| Memory | 1–4 | 36 picture cards, timed hiding and pair scores |
| Mancala, Nine Men's Morris, Hex | 2 | Kalah sowing, nine-piece mills and 11×11 connectivity with pie rule |
| Dots and Boxes | 2–4 | 6×6 dots, extra turns for completed boxes and shared wins |
| Backgammon | 2 | Complete dice use, bar entry, bearing off and optional doubling cube |
| Battleship | 2 | Private 10×10 boards, manual/automatic placement and alternating shots |
| Shogi, Mini Shogi | 2 | Promotion, hand drops, king safety and repetition decisions |
| Monopoly | 2–6 | 40 spaces, auctions, limited buildings, mortgages, trades and bankruptcy chains |

Classic games use dark wood frames and subdued green surfaces with vanilla and resource-pack presentations. Boards retain their traditional colors and markings. Click cells and near-side controls directly; for moves, select a source and then its destination. Multiple move choices also appear on the table. Sneak and right-click the table for room management. Monopoly auctions and trades use physical amount controls and asset selectors; confirm the draft on the table before sending an offer. Property money is separate from the external match pot.

One-player 2048, Klondike, Mastermind and Memory start immediately. Save and leave releases the table; the menu lets players choose a saved game, while `/3dgames resume` restores the most recently saved one. Each game has its own save; starting a new game of the same kind requires confirmation. Disconnects and shutdowns also save. Solo play has no entry fee, stake or economy reward. Progress lives in `solo/<player-UUID>.<game-ID>.json`; legacy saves remain readable and are backed up under `solo/legacy-backups/` before replacement. Unreadable originals are retained. Reaching the action-history safety budget retains progress instead of completing the game.

Rooms support readiness, bots, spectating, rematches and restart recovery. Rules lock when a room is created; create another room to change them. Mahjong offers Riichi, Guangdong, Sichuan and Taiwan profiles with explicit house rules. Poker chips and Mahjong scores remain match points; optional economy charges and rewards apply to whole games. In-game rule descriptions are available through the menu and `/3dgames rules`.

Vanilla poker and Color Eight faces use 24 thin player heads for 32×48 artwork, while concealed public backs use two blocks. Signed textures are bundled; no runtime MineSkin API key is needed. Resource-pack cards use one display per card. Only the owner sees a concealed hand's faces.

## Commands and permissions

| Command | Purpose |
| --- | --- |
| `/3dgames [menu]` | Open the native menu |
| `/3dgames create <kind> [players]` | Create a room |
| `/3dgames join <room-prefix>` | Join an open room |
| `/3dgames ready` / `bots` | Toggle readiness / fill bot seats as host |
| `/3dgames resume` / `leave` | Return to your table / leave the room |
| `/3dgames rematch` | Ready for a rematch |
| `/3dgames move <action>` | Play a legal rule action, e.g. `drop:3` |
| `/3dgames rules [kind]` | Show rules |
| `/3dgames reload-language` | Administrator language reload |
| `/3dgames reload-pack` | Administrator pack rebuild and resend |
| `3dgames status` | Inspect room count from the console |

Game IDs: `xiangqi`, `gomoku`, `chess`, `ludo`, `checkers`, `draughts`, `reversi`, `go`, `go9`, `go13`, `connectfour`, `color-eight`, `mahjong`, `yacht`, `doudizhu`, `liars-bar`, `texas-holdem`, `2048`, `mancala`, `nine-mens-morris`, `dots-and-boxes`, `mastermind`, `hex`, `memory`, `backgammon`, `battleship`, `klondike`, `shogi`, `mini-shogi`, `monopoly`. `checkers` selects Chinese Checkers; `go` selects 19×19 Go.

Example: `/3dgames create connectfour`, then `/3dgames bots`. Command creation uses default rules; use the menu to customize rules. Tab completion suggests games, player counts, rooms and legal actions. `3dgames.use` defaults to everyone. `3dgames.admin` defaults to operators and controls administration and language reloads.

## Configuration and languages

Settings live in `plugins/3DGames/config.yml`:

| Key | Default | Purpose |
| --- | --- | --- |
| `language` | `en_US` | Language catalogue under `languages/` |
| `max-rooms` | `12` | Maximum rooms |
| `reconnect-seconds` | `120` | Offline return grace period |
| `idle-room-minutes` | `30` | Idle room timeout |
| `turn-seconds` | `60` | Default turn timeout; games may apply their own limits |
| `sounds.enabled` | `true` | Enable game and interface sounds |
| `sounds.volume` | `1.0` | Volume multiplier from 0 to 1 |
| `rendering.mode` | `auto` | Mixed rendering with enabled CraftEngine, otherwise vanilla; optional overrides: `vanilla`, `mixed`, `resource-pack` |
| `rendering.resource-pack.source` | `craftengine` | CraftEngine's unified pack; optional `local` standalone hosting or `external` ZIP |
| `rendering.resource-pack.url` | Empty | External-mode URL for the ZIP matching the JAR |
| `rendering.resource-pack.local.port` | `8137` | Standalone download port used only by `local` delivery |
| `rendering.resource-pack.local.public-url` | Empty | Optional public base URL; empty uses the player's connection hostname and download port |

Set `language: zh_CN` for Simplified Chinese. Edit the generated `languages/en_US.yml` or `languages/zh_CN.yml`, or copy a catalogue to another language name. Preserve message keys and placeholders such as `{player}`. MiniMessage colors and legacy color codes are supported; language files cannot define callbacks or commands.

Run `/3dgames reload-language` after editing translations or the selected language. Other settings require a restart. Missing entries in the standard English and Chinese catalogues use their bundled translations without rewriting your files. Other languages fall back to English. Invalid reloads keep the previous language. Menu layouts are defined in code, while captions and descriptions come from language files.

`rooms.json` stores rooms, table locations and action history. `table-map-ids.yml` stores the plugin's map allocations. Optional paid games also create `economy.json`, the payment journal. Preserve these files when upgrading; do not edit generated records while the server is running.

## Optional economy and placeholders

Vault and PlaceholderAPI are optional server plugins, with no client installation required. Their APIs are compile-time dependencies and are not bundled in this JAR. Vault also needs an enabled economy provider. Standard Vault-compatible providers use the same economy interface; individual implementations still require compatibility testing.

Economy is disabled by default. To enable it, edit `config.yml` and restart:

```yaml
economy:
  enabled: true
  stakes:
    enabled: true
    minimum: 1.0
    maximum: 1000.0
```

All games share the administrator's positive amount limits. New rooms start at **0**. Hosts select the room amount; every player must confirm before the next start. Bots require amount 0. Tabletop distributes only the player-funded pot. Poker chips and Mahjong points remain in-game scores.

Leaving during play forfeits and ends the game; remaining opponents split the pot. Doudizhu forfeits follow teams after the landlord is selected, or individual opponents before bidding ends. Disconnecting or leaving the table world pauses play for `reconnect-seconds` (120 by default). Players who do not return in time forfeit together; if everyone expires, the game draws and refunds. Restored active games receive a fresh grace period after restart. Splits use the provider's currency precision, with leftover smallest units assigned in original seat order.

Draws, administrator cancellations and failed starts refund confirmed charges. Resigned players may join another room while opponents retain the finished table and receipts; incomplete tables must be recreated. Normal rematches require another confirmation from every player and charge as a new game. Listings show each player's stake and the full-table pot; balances and shortfalls are private. Receipts separate paid stakes, received refunds, received pot shares, confirmed net changes and pending payouts. Leaving a finished table does not reverse settled payments.

`economy.stakes.enabled` controls the Tabletop host amount editor. Legacy fixed-price rooms and their journals remain readable; every newly created room starts at amount 0.

Each game captures its amounts and participants. Known rejected payments retry every 30 seconds or after restart. Vault has no transaction idempotency key: crashes or provider exceptions can leave a payment uncertain. These records remain in `economy.json` and are **not automatically repeated**; an administrator must compare them with the provider's records. Corrupt or unwritable journals block paid games and retain the original data. Tabletop journals use schema 2 and can read legacy schema 1. Do not downgrade with payment records to a JAR without schema 2 support; it will reject the journal.

Language files use the keys from the current bundled catalogue. Leave notices use `menu.leave.lobby`, `menu.leave.active`, `menu.leave.finished` and `menu.leave.refund` for the corresponding room state.

With PlaceholderAPI installed, the built-in `3dgames` expansion registers automatically and survives `/papi reload`. Examples:

| Placeholders | Values |
| --- | --- |
| `%3dgames_rooms%`, `%3dgames_players%` | Room count and human seat count, including offline seats |
| `%3dgames_in_room%`, `%3dgames_room_id%`, `%3dgames_room_name%` | Membership, full room UUID and localized room name |
| `%3dgames_game%`, `%3dgames_game_id%` | Localized game name and stable game ID |
| `%3dgames_state%`, `%3dgames_state_id%` | Localized phase and stable lowercase phase ID |
| `%3dgames_room_players%`, `%3dgames_room_humans%`, `%3dgames_room_bots%`, `%3dgames_room_capacity%` | Total seats, human seats, bots and capacity |
| `%3dgames_ready%`, `%3dgames_your_turn%`, `%3dgames_turn_player%` | Readiness, active turn ownership and current player name |
| `%3dgames_economy_available%`, `%3dgames_balance%`, `%3dgames_balance_formatted%` | Provider availability, raw balance and provider-formatted balance |

Room values refresh once per second. Requested balances refresh through plugin-scheduled tasks at most once every five seconds; the first request returns an empty string until the next refresh. No provider returns an empty balance. Missing membership returns empty text, zero counts or `false`; unknown placeholders remain unresolved. Placeholder callbacks only read immutable snapshots and never expose hidden hands or run provider/database operations.

## Optional resource pack

For a fresh installation, place this plugin and CraftEngine's JARs in `plugins/` and start the server. Missing registration files and built-in item entries are installed under `plugins/CraftEngine/resources/3dgames/`, preserving existing custom entries. Tabletop assets join CraftEngine's unified pack and use its existing hosting. CraftEngine's default self-hosted `port: auto` shares the Minecraft game port; ordinary direct servers need no additional port 8137.

The default configuration is below. Existing explicit settings are preserved; restart after switching an older installation:

```yaml
rendering:
  mode: auto
  resource-pack:
    source: craftengine
```

Existing CraftEngine resources and hosting settings are preserved. Disabled hosting, Velocity/BungeeCord or special port mappings may require configuring a reachable address using CraftEngine's hosting guide. This plugin does not rewrite another plugin's network configuration.

Wait for a pack rebuild to finish before running other CraftEngine reload or generation commands.

On first use, bundled defaults are extracted into `plugins/3DGames/resource-pack/` and packaged as `plugins/3DGames/resource-pack.zip`, an input to the unified pack. Players download CraftEngine's complete generated pack. Plugin updates fill missing default files and preserve existing custom images.

**Routine asset changes need no compilation, Python, Maven or replacement JAR:**

1. Replace the image under the same name and path, such as `assets/3dgames/textures/item/tabletop/color_eight/color_eight_blue_2_face.png`, in the server's resource folder.
2. Run `/3dgames reload-pack` as an administrator or from the console.
3. The plugin validates assets in the background, rebuilds and publishes CraftEngine's unified pack, then requests it from online players who enabled tabletop resources. Invalid images or JSON retain the previous pack. Unchanged tabletop content is not resent. Rooms and player preferences are preserved. Use CraftEngine's own reload commands for changes to its other resources.

The source defaults to `craftengine`; a URL alone does not select external delivery. Choose `source: local` for standalone hosting, or `source: external` with the matching delivered ZIP's `url` to use an existing HTTP(S) host. External delivery uses the JAR's bundled checksum. Restart after changing the source.

Standalone `source: local` uses `local.bind-address`, `local.port` and `local.public-url`. It listens on `0.0.0.0:8137` by default; an empty public URL uses the player's connection hostname and download port. This hosting mode needs a reachable download port or an HTTP(S) reverse proxy forwarding `/3dgames/` to the standalone service.

The default `auto` detects CraftEngine at startup: enabled selects `mixed`, while absent or disabled selects `vanilla`. Existing explicit mode settings remain unchanged; set an older configuration to `auto` to opt in. Restart after installing or removing CraftEngine to detect it again.

`vanilla` needs neither CraftEngine nor the pack. `mixed` offers a per-player tabletop rendering switch and uses vanilla rendering while the pack is unavailable. Disabling it only removes this plugin's loading request; packs sent independently by CraftEngine or other plugins remain under their control. `resource-pack` requires successful pack loading and ready model registrations before play. These explicit modes remain available to override automatic selection. Game logic, new resource identifiers and native head textures still require a plugin update; this reload command handles client-pack textures, models and sounds.

## Updating

Stop the server and back up the complete plugin data directory before replacing the JAR. Preserve current configuration, custom translations, rooms and other plugins. Current schema 1 room files remain supported; retired game formats are not automatically converted. Validate a backup against the candidate JAR before updating:

```sh
java -cp target/3DGames-1.0.0.jar GoldenEggOVO.TDGames.Multiplayer.room.RoomReplayVerifier /backup/3DGames/rooms.json
```

For external delivery, update the matching ZIP and its URL as well. Unified and local delivery fill missing files from the JAR. After startup, check restored rooms, menus and table actions before removing the backup.

## Build

Install **JDK 25, Maven 3.9+ and Python 3.12+**. The first build needs access to dependency repositories; a populated cache allows offline builds. Packaging uses only Python's standard library; asset checks and native-head tools also need Pillow.

```sh
python tools/build.py
```

Use `python3` on Linux if needed. The script cleans old output, packages the default pack, compiles and runs Java tests, then verifies the JAR/ZIP checksums. It uses adjacent workspace Maven/cache installations when present, otherwise `PATH`; the JDK is detected through `JAVA_HOME` or `PATH`.

For a manual build, complete each step before the next:

```sh
mvn -B -ntp clean
python tools/package_resource_pack.py
mvn -B -ntp package
```

Generate the pack before compiling. Maven embeds `target/3DGames-resource-pack.zip` and its checksum; Maven alone does not generate this ZIP from the asset directory.

| Output | Purpose |
| --- | --- |
| `target/3DGames-1.0.0.jar` | Installable plugin with its default pack |
| `target/3DGames-resource-pack.zip` | Client pack for inspection or external hosting |
| `target/resource-pack-manifest.json` | Resource inventory, dimensions and digests |
| `target/surefire-reports/` | Java test results |

Asset checks and source export:

```sh
python -m unittest discover -s tools/tests -v
python tools/package_source.py
```

Source export needs Git and a Git checkout, producing `target/3DGames-source.zip`. It includes current source and uncommitted new files, excluding ignored build outputs, logs and runtime data. Identical files produce identical resource ZIPs in the same Python environment.

Build scripts do not deploy servers. There are no GitHub Actions workflows; pushing source does not automatically build or publish a Release.

## Source layout

```text
3DGames/
├── src/             Plugin code, defaults, translations and Java tests
├── resource-pack/   Editable client pack, item registrations and source artwork
├── tools/           Build scripts, native card tools and verification
├── target/          Generated JARs, ZIPs and test results
├── pom.xml          Maven build configuration
├── README.md        English documentation
├── README.zh-CN.md  Chinese documentation
└── LICENSE          Code license
```

| Directory | Contents |
| --- | --- |
| `src/main/java/GoldenEggOVO/TDGames/` | Application services in `Core/`, personal games in `Singleplayer/`, room games in `Multiplayer/`, and common sessions/rendering in `Shared/`; `src/test/java/` follows the same structure |
| `Shared/interaction/` and `Shared/render/` (Java) | Table input and legal move selection in `BoardSelection`; display geometry and per-player overlays in `TableView` |
| `src/main/resources/` | Plugin metadata, defaults, translations and native model data |
| `src/main/resources/vanilla-models/` | Native fixed geometry grouped under `tabletop/` and `shared/`; `index.json` maps model IDs to files |
| `resource-pack/assets/` | Client models, items, textures and sounds; `pack.mcmeta` describes the pack |
| `resource-pack/craftengine/` | Default item registrations bundled in the JAR and installed automatically |
| `resource-pack/originals/` | Original artwork, SVGs, recordings and native card source images, grouped under `tabletop/` and `shared/`; excluded from the client ZIP |
| `resource-pack/licenses/`, `sources.json` | Bundled attribution and source, checksum and license records |
| `tools/*.py` | Full build with `build.py`, client pack packaging with `package_resource_pack.py` and source export with `package_source.py` |
| `tools/native_heads/` | Native card skin slicing and optional MineSkin texture generation |
| `tools/tests/` | Python asset and build checks, the Minecraft model parser under `client/` and isolated Purpur checks under `server/` |

Historical deliveries and runtime records are stored outside the repository. The source tree contains the files needed to maintain the current version.

### Finding the code

Plugin packages are under `src/main/java/GoldenEggOVO/TDGames/`; all paths below are relative to that directory. Tests follow the same structure.

```text
TDGames/
├─ TDGamesPlugin.java   Plugin assembly, event registration and cleanup
├─ Core/               Game coordination, commands, shared menus, packs and common services
├─ Singleplayer/       2048/Klondike rules, personal progress and solo menus
├─ Multiplayer/        Multiplayer rules, rooms, preparation/reconnection, bots, payments and room menus
└─ Shared/             Mastermind/Memory rules, common sessions, models and input
```

| Change | Start here |
|---|---|
| Startup and configuration | `TDGamesPlugin.java` |
| Commands and Tab suggestions | `Core/command/` |
| Server compatibility and scheduling | `Core/platform/`; Paper and Bukkit Dialog backends share menu definitions |
| Game creation and mode routing | `Core/game/GameFactory.java` and `GameService.java`; IDs, order and player counts in `Shared/game/GameCatalog.java` |
| Personal sessions and progress | `Singleplayer/SingleplayerService.java` and `Singleplayer/progress/SoloProgressStore.java` |
| Multiplayer preparation, reconnection and persistence | `Multiplayer/MultiplayerService.java`, `Multiplayer/room/RoomStore.java` and `RoundActions.java` |
| Common session state and table execution | `Shared/session/GameSession.java`, `SessionHistory.java` and `SessionRuntime.java` |
| Game rules and legal actions | `Singleplayer/game/<game>/`, `Multiplayer/game/<game>/`, `Shared/game/mastermind/` and `Shared/game/memory/` |
| Clicks, hovering and table input | `Shared/interaction/TableWorldController.java` |
| Joining rooms and opening menus at tables | `Shared/interaction/TableAccessController.java` |
| Player focus camera, collision and state restoration | `Shared/interaction/TablePlayerController.java` |
| Menu pages and buttons | `Core/menu/GameMenus.java` routes shared pages; `Singleplayer/menu/SoloMenus.java` and `Multiplayer/menu/RoomMenus.java` own mode actions; `Core/menu/GameMenuWindow.java` adapts windows |
| Multiplayer amount input | `Multiplayer/menu/PokerRaiseWindow.java` and `StakeWindow.java` |
| Fee, balance and settlement text | `Multiplayer/menu/RoomEconomyText.java` and `Multiplayer/economy/RoomPaymentLedger.java`; Vault/PAPI integration in `Multiplayer/integration/` |
| Board display and viewer privacy | `Shared/render/TableView.java`, `Core/render/DisplayAudience.java` and `Core/render/PlayerViews.java` |
| Board coordinates, hit regions and piece geometry | `Shared/render/TableGeometry.java` and `BoardPieceModels.java` |
| Native board map artwork and map ID caching | `Shared/render/BoardMapArtwork.java` and `BoardMapStore.java` |
| Card, Mahjong and dice layouts | `Shared/render/cards/`, `Shared/render/mahjong/` and `Shared/render/dice/` |
| Resource-pack loading, delivery and CraftEngine integration | `Core/pack/ResourcePackService.java`; `Core/pack/PackedModels.java` reads the standalone model catalogue |
| Packed model selection and display | `Shared/render/packed/BoardModelKeys.java` and `PackedDisplay.java` |
| Bots, sounds and translations | `Multiplayer/bot/`, `Shared/audio/` and `Shared/text/`; player text in `src/main/resources/languages/` |

The normal action flow is input → `Core/game/GameService` mode routing → singleplayer or multiplayer validation → common runtime rule execution → mode-specific saving and view updates. Singleplayer owns personal progress; multiplayer owns readiness, reconnection waiting and settlement. Both modes share player occupancy and table allocation. Membership, readiness, absence and departure collections are read-only to consumers. Game rules do not depend on Bukkit; rendering does not determine whether a move is legal.

Shared card heads live in `Core/render/PlayingCardHeads.java`; display audiences query pack modes through `ResourcePackState`. Mahjong public-tile layouts, table geometry and native tile patterns live in `Shared/render/mahjong/MahjongLayout.java`, `MahjongTableFurniture.java` and `MahjongTilePatterns.java`.

`Core/platform/` wraps native Dialogs, messages, events and scheduling. `Core/render/PlayerViews.java` captures player snapshots and `DisplayMovement.java` handles display movement. `Shared/interaction/TableChunks.java` owns shared chunk tickets; `TableWorldController.java` executes table actions after their region is ready.

`Core/economy/` provides economy interfaces and the Vault adapter; `Core/text/Language.java` loads translations; `Core/menu/DialogMenus.java` builds native menus through the platform backend.

`tools/tests/server/run_standalone.py` checks isolated startup, gameplay and restart recovery. Its `--server-dir` fixture needs Purpur 26.2 named `purpur-2622.jar`, prepared libraries/cache and an already accepted EULA. Use `--maven-repo` for a custom dependency cache (default `~/.m2/repository`). Optional CraftEngine arguments enable resource-model checks. `run_soak.py` checks repeated gameplay and entity cleanup. Keep receipts and logs in local build outputs.

`tools/tests/client/run_probe.py <minecraft-home>` uses an installed official client parser to check model loading and card facing. It defaults to 26.2; select another installed client with `--version`.

Native head export: `python tools/native_heads/build-native-card-heads.py --output target/native-heads`; Color Eight: `python tools/native_heads/build-color-eight-heads.py --output target/color-eight-heads`. Only `generate-native-card-heads.py` contacts MineSkin, and only when explicitly run with a local `--api-key-file`; it is not part of normal builds or runtime.

Automated tests do not replace client acceptance. Check private hands from multiple seats and a spectator, pack switching and failed downloads, hover/click alignment, dice movement, sounds, restart recovery and frame-time performance in Minecraft.

### Editing native models

JSON files under `vanilla-models/` describe tables, pieces, dice, mahjong patterns and card backs with one format. `Core/render/model/` loads and validates definitions and calculates their transforms. Game Java classes select models and handle dynamic placement, animation, interaction and entity cleanup.

Each `blocks` entry contains `name`, `material`, `position` and `size`. `position` is the part's center and `size` gives its three dimensions. Optional `rotation` uses `[pitch, yaw, roll]` in degrees, composed in Y, Z, X order. Optional `brightness` ranges from 0 to 15; omission inherits the scene's brightness. `labels` describe fixed text that language files can override.

Tables use local block coordinates. Board pieces scale with cell spacing. Mahjong patterns scale X and Y to the tile face while retaining their depth layers; use `roll` for rotation within the pattern plane. Index aliases can share a geometry file, so check all references before editing it.

`shared/cards/head_textures.json` and `tabletop/color_eight/head_textures.json` hold signed native card textures and are separate from the geometry index. Use `tools/native_heads/` to regenerate those faces.

After changing bundled native data, follow the full [build](#build) and reinstall the JAR. Client resource-pack models remain separately editable under `resource-pack/assets/`. `reload-pack` does not reload native geometry bundled in the JAR.

### Editing the resource pack

Edit model JSONs under `resource-pack/assets/3dgames/models/item/`, item definitions under `items/`, images under `textures/item/` and OGG samples under `sounds/`. Sound events are in `assets/3dgames/sounds.json`. To add an item, match its definition path to CraftEngine's `item_model` and its `model.model` to the model path. Keep the logical item ID in the CraftEngine registration and `CraftEngineModels.IDS` consistent. Run the packager and tests after editing, then compile and ship the matching JAR and ZIP together. Packing does not redraw, resize or transcode any source file.

Asset filenames describe their game, color, suit, face or part: `poker_spades_ace`, `color_eight_card_blue_2` and `mahjong_characters_1`. Item definitions and model files share the same basename. Stable logical IDs are mapped to these filenames by CraftEngine's `item_model` registration; the packager uses that registration as its index. Renaming a client file updates its path references and registration while preserving logical IDs, native model IDs and gameplay code.

After editing the source defaults, follow the full [build](#build) sequence. Packaging creates `target/3DGames-resource-pack.zip` and updates its checksum; Maven then tests and embeds both in the plugin. Servers using unified or local resources edit their server resource folder and run `/3dgames reload-pack` for routine asset changes. External hosting still uses the ZIP matching the JAR. Editing an existing PNG does not require changing CraftEngine registrations.

| Directory or file | How to customize it |
| --- | --- |
| `assets/`, `pack.mcmeta` | Edit textures, models, item definitions, sounds and pack metadata; update references when renaming or moving files |
| `craftengine/` | Keep logical item IDs and client resource paths aligned; copy registration changes to the server's CraftEngine configuration |
| `originals/tabletop/` | Tabletop artwork, Mahjong recordings and Color Eight native card sources |
| `originals/shared/` | Shared poker artwork, native poker cards and common gameplay/menu recordings |
| `originals/tabletop/native-cards/`, `originals/shared/native-cards/` | Native head-card artwork; PNG edits also require regenerating signed head textures and compiling the plugin, as ordinary resource-pack packaging does not update heads |
| `licenses/`, `sources.json` | Attribution, origins and checksum records; keep them accurate when replacing materials |

CraftEngine item registrations are split into `craftengine/resources/3dgames/configuration/tabletop/items.yml` and `shared/items.yml`. Their logical IDs and client model paths remain stable. New installations use these categories; the installer checks existing configuration files, preserves custom definitions and adds only missing IDs to avoid duplicate registrations.

Original filenames describe their content, such as `mahjong_characters_1.png`, `color_eight_blue_draw_one.png`, `poker_spades_ace.png` and `wood_place_1.ogg`. The head exporter maps these names to the existing face IDs and skin cache hashes. `sources.json` retains the original source links, hashes and license notices; `originals/shared/sounds/sound_events.json` lists the audio samples. Editing originals does not automatically update the finished files under `assets/`.

JSON files use two-space indentation for direct editing.

Image names identify the game and part, for example `poker_spades_9_face.png`, `color_eight_yellow_6_face.png`, `xiangqi_red_pawn_face.png` and `mahjong_table_cloth.png`. `top`/`bottom` identify upper and lower surfaces, `front`/`rear` distinguish face orientations, and `shared_` marks textures reused by multiple models.

Under `textures/item/`, Tabletop images live in `tabletop/<game>/`, and shared images in `shared/`. Tabletop game directories include `mahjong/`, `xiangqi/`, `chess/`, `connect_four/`, `gomoku/`, `go/`, `reversi/`, `checkers/` (Chinese Checkers), `draughts/`, `ludo/`, `color_eight/`, `doudizhu/`, `liars_bar/`, `texas_holdem/` and `yacht/`. Shared images are stored once:

| Shared directory | Contents |
| --- | --- |
| `shared/materials/` | Basic wood, card body and concrete textures |
| `shared/playing_cards/` | Card faces and backs used by the playing-card games |
| `shared/table_parts/` | Table bottoms and pedestal textures used by multiple tables |
| `shared/pieces/` | Go/Gomoku stones and gold decorations used by multiple board games |
| `shared/dice/` | Six face textures for the die shared by Ludo and Yacht Dice |

Models and item definitions use the same `tabletop/` and `shared/` categories under `models/item/` and `items/`. Game-specific files sit under their category and game name. Shared playing cards live in `shared/playing_cards/`, Go/Gomoku stones in `shared/pieces/`, and the Ludo/Yacht die in `shared/dice/`. For example, `items/tabletop/mahjong/mahjong_characters_1.json` has the client ID `3dgames:tabletop/mahjong/mahjong_characters_1`, referenced by CraftEngine's `item_model`. Its `model.model` points to `3dgames:item/tabletop/mahjong/mahjong_characters_1`. Java code and CraftEngine's custom item keys retain the logical ID `3dgames:mahjong_m1`. Sound events are registered in `assets/3dgames/sounds.json`. Existing custom packs and CraftEngine registrations must use matching client paths when adopting this layout.

Resource paths, CraftEngine item keys, sounds, world entity tags, player preferences and native geometry references all use the lowercase `3dgames` namespace. The displayed plugin name remains `3DGames`.

Playing-card artwork is stored in `textures/item/shared/playing_cards/`. The 55 rounded Tabletop card models, including both jokers, live in `models/item/shared/playing_cards/`; item definitions follow the same paths under `items/`.

## License

The combined plugin is distributed under GPL-3.0-or-later; see [LICENSE](LICENSE). Corresponding source is available in this repository and `3DGames-source.zip` generated by the build tooling.

### Third-party and shared materials

Original notices and exact source provenance are retained under [`src/main/resources/META-INF/`](src/main/resources/META-INF/):

- NucleoidMC/Gomoku — MIT.
- jwang541/Xiangqi — MIT.
- hongsenliu/chinese-checkers — GPL-3.0-or-later.
- bhlangonijr/chesslib — Apache-2.0.
- Gson — Apache-2.0 runtime libraries included in the shaded JAR.
- Adventure API, MiniMessage and text serializers — MIT; bundled for Spigot and CraftBukkit. The original notice is retained in `META-INF/licenses/adventure-MIT.txt`.
- Kyori Option — MIT; a bundled Adventure dependency. The original notice is retained in `META-INF/licenses/option-MIT.txt`.

`Core/text/MessageText.java` and `Core/text/LabelLayout.java` are adapted from GoldenEggOVO/3DCasinoGames, revision `a4ddd20`, under its GPL-3.0 license. Tabletop's label fitting retains all status lines. These are bundled source utilities; installing Casino is not required.

Server APIs and JOML are supplied by the selected server; Paper-specific entry points are isolated. Private fonts, runtime configuration, worlds and third-party server/plugin JARs are not distributed here.

The bundled chesslib subset omits unused event observers, deprecated FEN aliases and Polyglot opening-book hashing. Standard move validation and repetition detection remain in use. The original Apache-2.0 notices and source provenance are retained. Embedded sources live under `Multiplayer/game/chess/engine/`, `Multiplayer/game/xiangqi/engine/` and `Multiplayer/game/chinesecheckers/engine/`.

The optional resource pack uses transparent tile artwork from [lietxia/mahjong_graphic](https://github.com/lietxia/mahjong_graphic), revision `3e275804ff58325306710bef3a7406860444bc6a`, under its M+ fonts license permitting modification and commercial use. Selected PNGs and their SVG sources are retained under `resource-pack/originals/tabletop/textures/mahjong/tiles/`; the full notice and file hashes are centralized in `resource-pack/sources.json`, and the client pack includes the notice.

The Mahjong tablecloth is original plain green felt by the 3DGames contributors, with its SVG source retained under `resource-pack/originals/tabletop/textures/mahjong/`. Mahjong sound effects use Kenney recordings under CC0-1.0. Exact source files, URLs, licenses and SHA-256 digests are recorded in `resource-pack/sources.json`; the client pack includes the notices. Color Eight artwork and model geometry are created by this project. CraftEngine is optional and its binary is not redistributed.

The resource-pack Xiangqi faces use the user-selected traditional-character SVGs from Wikimedia Commons (Inductiveload, with current vector revisions by Wj654cj86), released into the public domain. The first red and third black piece are selected from each SVG. Original SVGs and locally rendered PNGs are retained under `resource-pack/originals/tabletop/textures/xiangqi/`; exact hashes and file page links are in `resource-pack/sources.json`. Resource-pack poker faces and the blue reverse use [Pixel Playing Cards Pack by George Blackwell](https://georgeblackwell.itch.io/playing-cards-sprite-pack), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The original 60x84 PNGs are retained under `resource-pack/originals/shared/textures/playing-cards/`. Faces retain the original aspect ratio without added padding, with nearest-neighbor 2x enlargement; the blue back is simplified to a solid field with an ivory border, and the large joker corner lettering is recolored red. Author credit, license links and these changes are recorded in `resource-pack/sources.json` and in the client pack notice. Native head catalogues and their source images are unchanged. Each packed card uses one model display entity.

Current shared gameplay sounds, including card play, Reversi flips and dice, use physical foley compositions based on Kenney CC0 recordings. Selected masters live under `resource-pack/originals/shared/sounds/approved/`; events, processing and output digests are recorded in `sources.json` under `approved_audio`. Other Kenney recordings are grouped under `originals/shared/sounds/`, with Mahjong recordings under `originals/tabletop/sounds/mahjong/`; notices remain in the client pack. The source archive also retains the user-supplied `oxidvideos-taking-playing-card-522520.mp3`; its original download page and license receipt were not supplied, and this provenance status remains in `sources.json`. It is not the master used by the current card-play event.

Native poker card faces use cropped zombie, skeleton and creeper head textures from the user's Minecraft 26.2 client JAR (`assets/minecraft/textures/entity/`). The card layout, suits and Wither sprite are project artwork. Minecraft textures remain Mojang/Microsoft assets, separate from GPL plugin code. Head-front pixels were imported into offline PNG sources; signed skin properties are embedded for native rendering. The API key and generation cache are excluded.

Shogi and Mini Shogi share the user-selected [2-kanji red wood set by Kadagaden](https://github.com/Kadagaden/shogi-pieces/tree/3a7643b8f6aa9910b360b61e062961e70213c6cc/2-kanji_red_wood). Kadagaden artwork is CC BY 4.0; the two-kanji calligraphy is by Hari Seldon under CC BY-SA 3.0. The 15 original SVG files and their hashes are retained under `resource-pack/originals/tabletop/shogi/`. Packed pieces use 128x128 RGBA rasterizations of these originals with the original ivory lettering and red-wood colors; each piece has one closed 3D carrier. Source, license links and modifications are recorded in `resource-pack/sources.json` and `resource-pack/licenses/shogi-art.txt`.

Shared block-material PNGs were extracted from Minecraft 26.2 and remain Mojang/Microsoft assets, separate from the GPL code license; provenance and digests are recorded in `resource-pack/sources.json`.

#### Public rule references

These references informed independently implemented rules; regional profiles use the options and house rules described in the in-game rule descriptions.

- [RIF rules 9.2–9.3](https://gomoku.renju.net/rifrules/)
- [Switch Ludo guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/ludo)
- [Nintendo's update notes](https://en-americas-support.nintendo.com/app/answers/detail/a_id/49554/p/989)
- [Chinese Checkers guide](https://gamefaqs.gamespot.com/switch/286602-clubhouse-games-51-worldwide-classics/faqs/78437/chinese-checkers)
- [Blazing 8s FAQ](https://support-apps.discord.com/hc/en-us/articles/26501925147415-Blazing-8s-FAQ)
- [Nintendo game catalog](https://www.nintendo.com/jp/switch/as7ta/games/index.html)
- [EMA 2025 Riichi rules](https://mahjong-europe.org/portal/images/docs/Riichi-rules-2025-EN.pdf)
- [Sichuan Sports Venue Association rules](https://www.ssva.org.cn/upload/file/2025-08-22/6389146967009551555075859.pdf)
- [New Mahjong Online terminology](https://new.mjonline.com.tw/aboutgame_d.html)
