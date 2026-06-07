package me.micahcode.betterStresstestbots;

import me.micahcode.betterStresstestbots.nms.FakePlayerFactory;
import me.micahcode.betterStresstestbots.nms.IFakePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

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
    private final List<IFakePlayer> bots = new ArrayList<>();

    private BukkitTask tickTask;
    private BukkitTask spawnTask;

    // these can be changed with commands
    private double speed = 0.1;
    private double radius = 500.0;
    private int targetCount = 0;
    private int spawnDelayTicks = 2;
    private GroundMode groundMode = GroundMode.NONE; // default: stand still

    public BotManager(StressTestPlugin plugin) {
        this.plugin = plugin;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

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
        bots.forEach(b -> b.setSpeed(speed));
    }

    public void setRadius(double radius) {
        this.radius = radius;
        bots.forEach(b -> b.setRadius(radius));
    }

    public void setGroundMode(GroundMode mode) {
        this.groundMode = mode;
        bots.forEach(b -> applyModeToBot(b, mode));
    }

    /** Passes the mode straight through to the bot's setMode(). */
    private void applyModeToBot(IFakePlayer bot, GroundMode mode) {
        bot.setMode(mode);
    }

    public void botsChat(String message) {
        bots.forEach(b -> b.sendChat(message));
    }

    /**
     * Sends all bots toward the given location.
     * Each bot navigates once; the tick loop keeps them walking until they arrive
     * (or you can call stop/start to reset normal wandering).
     */
    public void gotoLocation(Location target) {
        bots.forEach(b -> b.navigateTo(target));
    }

    public void start() {
        if (spawnTask != null && !spawnTask.isCancelled()) spawnTask.cancel();

        while (bots.size() > targetCount) {
            bots.remove(bots.size() - 1).remove();
        }

        if (bots.size() >= targetCount) {
            plugin.getLogger().info("Already at target count (" + targetCount + ")");
            return;
        }

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (bots.size() >= targetCount) {
                spawnTask.cancel();
                plugin.getLogger().info("All " + targetCount + " bots spawned.");
                return;
            }
            Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation().clone();
            spawn.add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            String name = "StressBot_" + bots.size();
            IFakePlayer bot = FakePlayerFactory.create(name, spawn, plugin.getLogger());
            bot.setSpeed(speed);
            bot.setRadius(radius);
            applyModeToBot(bot, groundMode);
            bots.add(bot);
        }, 0L, spawnDelayTicks);

        plugin.getLogger().info("Spawning " + targetCount + " bots | speed=" + speed
                + " | radius=" + radius + " | mode=" + groundMode);
    }

    public void stop() {
        if (spawnTask != null && !spawnTask.isCancelled()) spawnTask.cancel();
        bots.forEach(IFakePlayer::remove);
        bots.clear();
        plugin.getLogger().info("All bots removed.");
    }

    public void teleportAll(Player player) {
        Location loc = player.getLocation();
        bots.forEach(b -> b.teleportTo(loc));
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        stop();
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