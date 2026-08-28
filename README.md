# BetterStresstestbots

Stress test your Minecraft server with server-side fake bots. Unlike external bot tools, this runs entirely as a Paper plugin, so doesnt require `online-mode=false`. Bots can fly or walk around the world, load chunks, and stress the game loop just like real players.

Supports **1.21.x** and **26.x**.

## Building

Clone the repo:

```
git clone https://github.com/yourusername/BetterStresstestbots.git
```

Build the 1.21.11 jar:

```
./gradlew :v1_21_11:assemble
```

Build the 1.21–1.21.4 jar:

```
./gradlew :v1_21:assemble
```

Build all version jars at once (needs a JDK that the v26 modules require too):

```
./gradlew buildAll
```

Jars will be in each module's `build/libs/`. Use the `*-reobf.jar` for 1.21.x versions and the regular jar for 26.x.

A GitHub Actions workflow in `.github/workflows/build.yml` builds and uploads the 1.21 and 1.21.11 jars on every push/PR.

## Installation

Drop the correct jar for your server version into your `plugins/` folder and restart.

| Jar | Version |
|-----|---------|
| `v1_21-x.x.x-reobf.jar` | 1.21 – 1.21.4 |
| `v1_21_11-x.x.x-reobf.jar` | 1.21.5 – 1.21.11 |
| `v26-x.x.x.jar` | 26.x |

Add this to your `server.properties`:

```
allow-flight=true
```

## Commands

Configure the bots first, then start them with `/start`:

```
/stress count 100       Set how many bots to spawn (does not spawn yet)
/stress speed 0.15      Set movement speed in blocks/tick (default: 0.1)
/stress radius 500      Set the wander radius in blocks (default: 500)
/stress mode fly        Set movement mode: fly or walk
/start                  Spawn bots with the current settings
/start 50               Spawn 50 bots immediately
/stress stop            Remove all bots
/stress chat <msg>      Make all bots send a chat message
/stress chat /rtp       Make all bots run /rtp like a normal player
/stress cmd <command>   Make all bots run any command (e.g. /stress cmd /rtp)
/botcmd <command>       Shortcut: make all bots run a command
/stress op <on/off>     Toggle whether bots are OP (default ON so commands work)
/stress tp              Teleport all bots to your location
/stress status          Show current bot count and settings
```

### Bot command support / normal player events

Bots are real `ServerPlayer` objects, so they fire `PlayerJoinEvent` / `PlayerQuitEvent` /
`PlayerCommandPreprocessEvent` / chat events on join and quit. This plugin additionally:

- Executes `/stress chat /rtp` (or `/botcmd /rtp`) through the normal command pipeline.
- Makes bots OP by default so permission-based commands like `/rtp` are allowed.
- Fires `PlayerMoveEvent` while bots wander, and uses Bukkit teleport for `/stress tp`, so
  `PlayerTeleportEvent` / `PlayerChangedWorldEvent` work too.
- Resets bot navigation after an external teleport so `/rtp` doesn't immediately walk back.

## Notes

- Bots are real server-side `ServerPlayer` objects so they load chunks, trigger entity tracking, and count toward the player list just like real players
- Start small (10–20 bots) and check TPS etc.
- Hard cap of 1000 bots to prevent OOM crashes
- In **walk mode** bots snap to the terrain surface
