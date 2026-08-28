package me.micahcode.betterStresstestbots;

import org.bukkit.plugin.java.JavaPlugin;

public class StressTestPlugin extends JavaPlugin {

    private BotManager botManager;

    @Override
    public void onEnable() {
        botManager = new BotManager(this);
        StressCommand cmd = new StressCommand(botManager);

        getCommand("stress").setExecutor(cmd);
        getCommand("stress").setTabCompleter(cmd);
        getCommand("start").setExecutor(cmd);
        getCommand("start").setTabCompleter(cmd);
        getCommand("goto").setExecutor(cmd);
        getCommand("goto").setTabCompleter(cmd);
        getCommand("botcmd").setExecutor(cmd);
        getCommand("botcmd").setTabCompleter(cmd);

        getLogger().info("BetterStresstestbots enabled!");
    }

    @Override
    public void onDisable() {
        if (botManager != null) botManager.shutdown();
    }

    public BotManager getBotManager() {
        return botManager;
    }
}