package me.micahcode.betterStresstestbots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class StressCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[StressTest] §f";
    private static final String ERR = "§c";
    private static final String ACCENT = "§a";
    private static final String MUTED = "§7";

    private final BotManager manager;

    public StressCommand(BotManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equalsIgnoreCase("goto")) {
            return handleGoto(sender, args);
        }

        if (label.equalsIgnoreCase("start")) {
            return handleStart(sender, args);
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                return handleStart(sender, Arrays.copyOfRange(args, 1, args.length));
            }

            case "stop" -> {
                manager.stop();
                sender.sendMessage(PREFIX + "§cAll bots removed.");
            }

            case "count" -> {
                if (args.length < 2) {
                    sender.sendMessage(ERR + "Usage: /stress count <number>");
                    return true;
                }
                try {
                    int count = Integer.parseInt(args[1]);
                    manager.setTargetCount(count);
                    sender.sendMessage(PREFIX + "Count set to " + ACCENT + manager.getTargetCount()
                            + MUTED + ". Use §f/start§7 to spawn the bots in!");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ERR + "Invalid number.");
                }
            }

            case "speed" -> {
                if (args.length < 2) {
                    sender.sendMessage(ERR + "Usage: /stress speed <number>");
                    return true;
                }
                try {
                    double speed = Double.parseDouble(args[1]);
                    manager.setSpeed(speed);
                    sender.sendMessage(PREFIX + "Speed set to: " + ACCENT + speed + MUTED + " blocks/tick.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ERR + "Invalid number.");
                }
            }

            case "radius" -> {
                if (args.length < 2) {
                    sender.sendMessage(ERR + "Usage: /stress radius <number>");
                    return true;
                }
                try {
                    double radius = Double.parseDouble(args[1]);
                    manager.setRadius(radius);
                    sender.sendMessage(PREFIX + "Radius set to: " + ACCENT + radius + MUTED + " blocks.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ERR + "Invalid number.");
                }
            }

            case "mode" -> {
                if (args.length < 2) {
                    sender.sendMessage(ERR + "Usage: /stress mode <none|fly|walk>");
                    return true;
                }
                BotManager.GroundMode mode = BotManager.GroundMode.fromString(args[1]);
                if (args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("walk") || args[1].equalsIgnoreCase("fly")) {
                    manager.setGroundMode(mode);
                    sender.sendMessage(PREFIX + "Mode → " + ACCENT + mode);
                } else {
                    sender.sendMessage(ERR + "Mode must be 'none', 'fly', or 'walk'.");
                }
            }

            case "chat" -> {
                if (args.length < 2) {
                    sender.sendMessage(ERR + "Usage: /stress chat <message>");
                    return true;
                }
                String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                manager.botsChat(msg);
                sender.sendMessage(PREFIX + "All bots sent: " + MUTED + msg);
            }

            case "tp", "teleport" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ERR + "Must be a player.");
                    return true;
                }
                manager.teleportAll(player);
                sender.sendMessage(PREFIX + "Teleported " + ACCENT + manager.getBotCount() + MUTED + " bots to you.");
            }

            case "goto" -> {
                return handleGoto(sender, Arrays.copyOfRange(args, 1, args.length));
            }

            case "status" -> sendStatus(sender);

            default ->
                    sender.sendMessage(ERR + "Unknown subcommand. Try: start, stop, count, speed, radius, mode, chat, tp, goto, status");
        }
        return true;
    }

    private boolean handleGoto(CommandSender sender, String[] args) {
        Location target;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ERR + "Usage: /goto <x> <y> <z> [world]");
                return true;
            }
            target = player.getLocation();
        } else if (args.length >= 3) {
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);

                World world;
                if (args.length >= 4) {
                    world = Bukkit.getWorld(args[3]);
                    if (world == null) {
                        sender.sendMessage(ERR + "Unknown world: " + args[3]);
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    world = player.getWorld();
                } else {
                    world = Bukkit.getWorlds().get(0);
                }

                target = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage(ERR + "Usage: /goto <x> <y> <z> [world]");
                return true;
            }
        } else {
            sender.sendMessage(ERR + "Usage: /goto [x y z [world]]");
            return true;
        }

        manager.gotoLocation(target);
        sender.sendMessage(PREFIX + "Sending " + ACCENT + manager.getBotCount()
                + MUTED + " bots to §f"
                + (int) target.getX() + "§7, §f"
                + (int) target.getY() + "§7, §f"
                + (int) target.getZ());
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        for (String arg : args) {
            BotManager.GroundMode parsed = tryParseMode(arg);
            if (parsed != null) {
                manager.setGroundMode(parsed);
                continue;
            }
            try {
                manager.setTargetCount(Integer.parseInt(arg));
            } catch (NumberFormatException e) {
                sender.sendMessage(ERR + "Usage: /start [count] [none|walk|fly]");
                return true;
            }
        }

        if (manager.getTargetCount() <= 0) {
            sender.sendMessage(ERR + "Set a count first: /stress count <n>  or  /start <n>");
            return true;
        }

        manager.start();
        sender.sendMessage(PREFIX + "Spawning " + ACCENT + manager.getTargetCount() + MUTED + " bots"
                + " | speed=" + manager.getSpeed()
                + " | radius=" + manager.getRadius()
                + " | mode=" + manager.getGroundMode());
        return true;
    }

    /**
     * Returns a GroundMode if the string matches one, or null if it doesn't.
     */
    private BotManager.GroundMode tryParseMode(String s) {
        return switch (s.toLowerCase()) {
            case "none" -> BotManager.GroundMode.NONE;
            case "walk" -> BotManager.GroundMode.WALK;
            case "fly" -> BotManager.GroundMode.FLY;
            default -> null;
        };
    }

    // todo: make this look better
    private void sendStatus(CommandSender sender) {
        sender.sendMessage(PREFIX + MUTED + "───────────────────────");
        sender.sendMessage(PREFIX + "Bots: " + ACCENT + manager.getBotCount()
                + MUTED + " / target " + ACCENT + manager.getTargetCount()
                + MUTED + " (cap " + BotManager.MAX_BOTS + ")");
        sender.sendMessage(MUTED + "  Speed: §f" + manager.getSpeed()
                + MUTED + "  Radius: §f" + manager.getRadius()
                + MUTED + "  Mode: §f" + manager.getGroundMode());
        sender.sendMessage(MUTED + "  /stress count|speed|radius|mode|chat|tp|goto → configure");
        sender.sendMessage(MUTED + "  /start [n] [mode] → spawn  |  /stress stop → remove all");
        sender.sendMessage(MUTED + "  /goto [x y z] → send bots to location");
        sender.sendMessage(PREFIX + MUTED + "───────────────────────");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("goto")) {
            if (args.length <= 3) return List.of("<x>", "<y>", "<z>").subList(args.length - 1, args.length);
            if (args.length == 4) return Bukkit.getWorlds().stream().map(w -> w.getName()).toList();
            return List.of();
        }

        if (alias.equalsIgnoreCase("start")) {
            if (args.length == 1) return Arrays.asList("10", "25", "50", "100");
            if (args.length == 2) return Arrays.asList("none", "walk", "fly");
            return List.of();
        }

        if (args.length == 1)
            return Arrays.asList("start", "stop", "count", "speed", "radius", "mode", "chat", "tp", "goto", "status");

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "count" -> Arrays.asList("10", "25", "50", "100");
                case "speed" -> Arrays.asList("0.05", "0.1", "0.2", "0.5");
                case "radius" -> Arrays.asList("25", "50", "100", "250", "500", "1000");
                case "mode" -> Arrays.asList("none", "fly", "walk");
                case "chat" -> Arrays.asList("Hello!", "Test message", "Stress test");
                case "start" -> Arrays.asList("10", "25", "50", "100");
                case "goto" -> List.of("<x>");
                default -> List.of();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start"))
            return Arrays.asList("none", "walk", "fly");

        if (args[0].equalsIgnoreCase("goto")) {
            if (args.length == 3) return List.of("<y>");
            if (args.length == 4) return List.of("<z>");
            if (args.length == 5) return Bukkit.getWorlds().stream().map(w -> w.getName()).toList();
        }

        return List.of();
    }
}