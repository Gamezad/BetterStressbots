package me.micahcode.betterStresstestbots;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thin wrapper around the threaded-regions scheduler API
 * ({@code io.papermc.paper.threadedregions.scheduler.*}).
 *
 * <p>That API is shipped by recent Paper builds (1.21.11+) and by Folia. On
 * Folia, tasks are executed on the region thread that owns the target
 * location/entity; on regular (single-threaded) Paper the same calls fall
 * back to the main server thread, so a single code path is thread-safe on
 * both. This is what makes the plugin Folia-compatible: each bot is ticked
 * and manipulated on the region that owns it instead of the global main
 * loop.
 *
 * <p><b>Important for older Paper versions (1.21 – 1.21.4):</b> those builds
 * do not ship the API at all. Callers must first check {@link #hasApi()} —
 * which never loads any of the API classes — and only call the other
 * methods when it returns {@code true}. The {@code ThreadedRegions} class is
 * otherwise only ever touched on API-capable servers, so the missing classes
 * are never resolved on older runtimes.
 */
final class ThreadedRegions {

    private ThreadedRegions() {
    }

    /**
     * Detects the threaded-regions scheduler API without loading any of its
     * classes. Safe to call on any Paper/Folia version.
     */
    static boolean hasApi() {
        try {
            Class.forName("org.bukkit.entity.Entity")
                    .getMethod("getScheduler");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Starts the per-bot tick loop. The task runs every tick on the region
     * that owns the bot (or the main thread on regular Paper), following the
     * bot if it is teleported. When {@code isAlive} reports the bot is gone,
     * the task cancels itself and invokes {@code onDead}.
     *
     * @return the scheduled task, or {@code null} if the bot's scheduler was
     *         already retired (the bot vanished before the task could run)
     */
    static ScheduledTask startBotTick(Plugin plugin, Player bot, Runnable tick,
                                      Supplier<Boolean> isAlive, Runnable onDead) {
        return bot.getScheduler().runAtFixedRate(plugin, task -> {
            if (!isAlive.get()) {
                task.cancel();
                onDead.run();
                return;
            }
            tick.run();
        }, null, 1L, 1L);
    }

    /**
     * Starts a repeating loop anchored at {@code anchor}, running on the
     * region that owns that location (or the main thread on regular Paper).
     * Used for the spawn loop, which creates bots right around the anchor.
     */
    static ScheduledTask startRegionLoop(Plugin plugin, Location anchor, long periodTicks,
                                         Consumer<ScheduledTask> step) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, anchor, step, 0L, periodTicks);
    }

    /**
     * Schedules {@code op} to run on the thread that owns the given entity
     * (its region on Folia, the main thread on regular Paper). Scheduling is
     * thread-safe and may be called from any thread context.
     *
     * @return {@code true} if the operation was scheduled, {@code false} if
     *         the entity's scheduler is already retired (the entity was
     *         removed and the operation will not run)
     */
    static boolean dispatch(Plugin plugin, Player entity, Runnable op) {
        return entity.getScheduler().execute(plugin, op, null, 0L);
    }
}
