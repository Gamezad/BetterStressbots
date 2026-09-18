package me.micahcode.betterStresstestbots;

import me.micahcode.betterStresstestbots.nms.FakePlayerFactory;
import me.micahcode.betterStresstestbots.nms.IFakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BotManager {

    public enum GroundMode {
        NONE, WALK, FLY;

        public static GroundMode fromString(String s) {
            return switch (s.toLowerCase()) {
                case "walk" -> WALK;
                case "fly"  -> FLY;
                default     -> NONE;
            };
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    // just to be safe
    public static final int MAX_BOTS = 5000;

    private final StressTestPlugin plugin;

    /**
     * Copy-on-write because bots are added/removed from several threads on
     * Folia (each bot ticks on its own region thread) while commands run on
     * the calling player's region.
     */
    private final List<IFakePlayer> bots = new CopyOnWriteArrayList<>();

    /**
     * Per-bot tick tasks, only used on API-capable (Folia / recent Paper)
     * builds. Values are held as {@link Object} and cancelled through an
     * {@code instanceof} check so the scheduler task type is never resolved
     * on older Paper versions that do not ship it.
     */
    private final Map<IFakePlayer, Object> botTickTasks = new ConcurrentHashMap<>();

    /** Legacy global tick task — only used on older Paper without the threaded-regions API. */
    private volatile BukkitTask tickTask;
    /** Legacy spawn loop task — only used on older Paper without the threaded-regions API. */
    private volatile BukkitTask legacySpawnTask;
    /** Threaded (Folia-compatible) spawn loop task. See {@link #botTickTasks} for the type note. */
    private volatile Object regionSpawnTask;

    // these can be changed with commands — volatile because commands run on
    // the calling player's region thread while bots tick on their own regions
    private volatile double speed = 0.1;
    private volatile double radius = 500.0;
    private volatile int targetCount = 0;
    /**
     * Ticks between bot joins. Staggered enough that plugins reacting to
     * joins (anti-cheat scans, lifesteal, voice chat, ...) don't all fire on
     * the same tick and cause a TPS dip.
     */
    private int spawnDelayTicks = 5;
    private volatile GroundMode groundMode = GroundMode.NONE; // default: stand still
    private volatile boolean botsOp = true; // default OP so /rtp and other commands work

    /**
     * True on Folia and on Paper builds that ship the threaded-regions
     * scheduler API (1.21.11+). On those, every bot is ticked and manipulated
     * on the region thread that owns it. On older Paper the classic
     * main-thread task is used instead.
     */
    private final boolean threadedRegions = ThreadedRegions.hasApi();

    public BotManager(StressTestPlugin plugin) {
        this.plugin = plugin;
        if (!threadedRegions) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
        }
    }

    /** Legacy path: one main-thread task ticking every bot (pre-1.21.11 Paper). */
    private void tickAll() {
        bots.removeIf(bot -> !bot.isAlive());
        for (IFakePlayer bot : bots) {
            bot.tick();
        }
    }

    public void setTargetCount(int count) {
        targetCount = Math.min(Math.max(0, count), MAX_BOTS);
    }

    public void setSpeed(double speed) {
        this.speed = speed;
        bots.forEach(b -> onBotRegion(b, () -> b.setSpeed(speed)));
    }

    public void setRadius(double radius) {
        this.radius = radius;
        bots.forEach(b -> onBotRegion(b, () -> b.setRadius(radius)));
    }

    public void setGroundMode(GroundMode mode) {
        this.groundMode = mode;
        bots.forEach(b -> onBotRegion(b, () -> b.setMode(mode)));
    }

    /** Make every running and future bot operator (so /rtp, /home, etc. work). */
    public void setBotsOp(boolean op) {
        this.botsOp = op;
        bots.forEach(b -> onBotRegion(b, () -> b.setOp(op)));
    }

    public boolean isBotsOp() {
        return botsOp;
    }

    /**
     * Executes a command as every bot. Commands should start with '/' or be given
     * without it; this method normalises the leading slash and dispatches through
     * the player's normal command pipeline (so PlayerCommandPreprocessEvent fires).
     */
    public void botsCommand(String command) {
        if (command == null || command.isBlank()) return;
        String normalized = command.startsWith("/") ? command : "/" + command;
        bots.forEach(b -> onBotRegion(b, () -> b.executeCommand(normalized)));
    }

    public void botsChat(String message) {
        if (message == null) return;
        if (message.startsWith("/")) {
            botsCommand(message);
        } else {
            bots.forEach(b -> onBotRegion(b, () -> b.sendChat(message)));
        }
    }

    /**
     * Sends all bots toward the given location.
     * Each bot navigates once; the tick loop keeps them walking until they arrive
     * (or you can call stop/start to reset normal wandering).
     */
    public void gotoLocation(Location target) {
        bots.forEach(b -> onBotRegion(b, () -> b.navigateTo(target)));
    }

    public void start() {
        stopSpawnLoop();

        // Snapshot: bots can concurrently die (their region tick tasks remove
        // themselves) while we shrink the list.
        List<IFakePlayer> toRemove = new ArrayList<>(bots);
        while (toRemove.size() > targetCount) {
            removeBot(toRemove.remove(toRemove.size() - 1));
        }

        if (bots.size() >= targetCount) {
            plugin.getLogger().info("Already at target count (" + targetCount + ")");
            return;
        }

        if (threadedRegions) {
            // Spawn loop anchored at the world spawn: on Folia it runs on the
            // region that owns the spawn point, which is where the bots join.
            Location anchor = Bukkit.getWorlds().get(0).getSpawnLocation().clone();
            regionSpawnTask = ThreadedRegions.startRegionLoop(plugin, anchor, spawnDelayTicks, task -> {
                if (bots.size() >= targetCount) {
                    task.cancel();
                    regionSpawnTask = null;
                    plugin.getLogger().info("All " + targetCount + " bots spawned.");
                    return;
                }
                trySpawnOne();
            });
        } else {
            legacySpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (bots.size() >= targetCount) {
                    legacySpawnTask.cancel();
                    legacySpawnTask = null;
                    plugin.getLogger().info("All " + targetCount + " bots spawned.");
                    return;
                }
                trySpawnOne();
            }, 0L, spawnDelayTicks);
        }

        plugin.getLogger().info("Spawning " + targetCount + " bots | speed=" + speed
                + " | radius=" + radius + " | mode=" + groundMode);
    }

    private void trySpawnOne() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation().clone();
        spawn.add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
        String name = "StressBot_" + bots.size();
        IFakePlayer bot = FakePlayerFactory.create(name, spawn, plugin.getLogger());
        Player player = bot.getBukkitEntity();
        if (player == null) {
            return; // join failed; the impl already logged it
        }

        bots.add(bot);

        // Apply the current settings on the bot's own thread (region on Folia).
        onBotRegion(bot, () -> {
            bot.setSpeed(speed);
            bot.setRadius(radius);
            bot.setOp(botsOp);
            bot.setMode(groundMode);
        });

        if (threadedRegions) {
            Object tickTask = ThreadedRegions.startBotTick(plugin, player, bot::tick, bot::isAlive,
                    () -> onBotDead(bot));
            if (tickTask != null) {
                botTickTasks.put(bot, tickTask);
            } else {
                // The bot vanished before the task could be scheduled.
                bots.remove(bot);
                bot.remove(); // best-effort disconnect of the dangling NMS player
            }
        }
    }

    public void stop() {
        stopSpawnLoop();
        for (IFakePlayer bot : bots) {
            removeBot(bot);
        }
        plugin.getLogger().info("All bots removed.");
    }

    private void removeBot(IFakePlayer bot) {
        if (!bots.remove(bot)) {
            return; // already gone (died) — the tick task cleaned it up
        }
        cancelBotTickTask(bot);
        // Disconnect happens on the bot's own thread; onBotRegion falls back
        // to a direct (best-effort) call if the scheduler is already retired.
        onBotRegion(bot, bot::remove);
    }

    /** Removes a bot that died on its own (already left the server). */
    private void onBotDead(IFakePlayer bot) {
        bots.remove(bot);
        cancelBotTickTask(bot);
    }

    private void cancelBotTickTask(IFakePlayer bot) {
        Object tickTask = botTickTasks.remove(bot);
        // instanceof never throws even if the class is missing on old runtimes.
        if (tickTask instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask t) {
            t.cancel();
        }
    }

    private void stopSpawnLoop() {
        Object regionTask = regionSpawnTask;
        if (regionTask != null) {
            if (regionTask instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask t) {
                t.cancel();
            }
            regionSpawnTask = null;
        }
        if (legacySpawnTask != null && !legacySpawnTask.isCancelled()) {
            legacySpawnTask.cancel();
        }
        legacySpawnTask = null;
    }

    public void teleportAll(Player player) {
        Location loc = player.getLocation();
        bots.forEach(b -> onBotRegion(b, () -> b.teleportTo(loc)));
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        stop();
    }

    /**
     * Runs an operation on the thread that owns the bot: its region on Folia,
     * the main thread on API-capable Paper. On older Paper (no threaded-regions
     * API) and as a last resort when the bot's scheduler is already retired
     * (e.g. during plugin disable), it runs inline.
     */
    private void onBotRegion(IFakePlayer bot, Runnable op) {
        if (!threadedRegions) {
            op.run();
            return;
        }
        try {
            Player player = bot.getBukkitEntity();
            if (player != null && ThreadedRegions.dispatch(plugin, player, op)) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Scheduler already retired (e.g. during plugin disable) — fall
            // through to a direct best-effort call.
        }
        op.run();
    }

    public int getBotCount() {
        return bots.size();
    }

    public int getTargetCount() {
        return targetCount;
    }

    public double getSpeed() {
        return speed;
    }

    public double getRadius() {
        return radius;
    }

    public GroundMode getGroundMode() {
        return groundMode;
    }

    /** Legacy boolean accessor kept for any NMS layer that still uses it. */
    public boolean isGroundMode() {
        return groundMode == GroundMode.WALK;
    }
}
