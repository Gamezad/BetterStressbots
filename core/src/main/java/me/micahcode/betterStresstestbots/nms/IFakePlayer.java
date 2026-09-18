package me.micahcode.betterStresstestbots.nms;

import me.micahcode.betterStresstestbots.BotManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface IFakePlayer {
    void tick();
    void remove();
    void setSpeed(double speed);
    void setRadius(double radius);
    void setGroundMode(boolean groundMode);   // legacy boolean shim
    void setMode(BotManager.GroundMode mode); // preferred
    void navigateTo(Location target);
    void teleportTo(Location loc);
    void sendChat(String message);
    void executeCommand(String command);
    void setOp(boolean op);
    boolean isOp();
    boolean isAlive();
    String getName();

    /**
     * The bot's Bukkit entity, used to schedule work on the thread/region that
     * owns it (required for Folia). May be null if the bot failed to join.
     */
    Player getBukkitEntity();
}
