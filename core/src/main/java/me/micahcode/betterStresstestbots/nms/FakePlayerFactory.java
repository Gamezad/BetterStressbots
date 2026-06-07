package me.micahcode.betterStresstestbots.nms;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.util.logging.Logger;

public class FakePlayerFactory {
    private static final String IMPL = "me.micahcode.betterStresstestbots.nms.FakePlayerImpl";

    public static IFakePlayer create(String name, Location spawn, Logger logger) {
        try {
            Class<?> clazz = Class.forName(IMPL);
            Constructor<?> ctor = clazz.getConstructor(String.class, Location.class, Logger.class);
            return (IFakePlayer) ctor.newInstance(name, spawn, logger);
        } catch (Exception e) {
            String mc = Bukkit.getMinecraftVersion();
            throw new RuntimeException(
                    "Failed to create FakePlayer for MC " + mc + ". " +
                            "Make sure you're using the jar for your version!", e
            );
        }
    }

    public static String detectVersionGroup() {
        String mc = Bukkit.getMinecraftVersion();
        if (mc.startsWith("26.") || mc.startsWith("27.")) return "26.x";
        if (mc.startsWith("1.21.1") || mc.startsWith("1.21.8")
                || mc.startsWith("1.21.10")) return "1.21.11-group";
        return "1.21.x";
    }
}
