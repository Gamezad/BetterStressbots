package me.micahcode.betterStresstestbots.nms;

import me.micahcode.betterStresstestbots.BotManager;
import org.bukkit.Location;

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
    boolean isAlive();
    String getName();
}