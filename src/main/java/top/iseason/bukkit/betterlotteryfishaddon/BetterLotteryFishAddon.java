package top.iseason.bukkit.betterlotteryfishaddon;

import betterlottery2.Config;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;

public final class BetterLotteryFishAddon extends JavaPlugin implements CommandExecutor {

    private FileConfiguration blConfig = Config.load("BetterLottery");
    public static String messagePattern = null;
    public static String failurePattern = null;
    private static BetterLotteryFishAddon plugin;

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        if (!new File(getInstance().getDataFolder(), "config.yml").exists()) saveDefaultConfig();
        else reloadConfig();
        messagePattern = getConfig().getString("Message");
        failurePattern = getConfig().getString("Failure");
        setBlConfig(Config.load("BetterLottery"));
    }

    @Override
    public void onDisable() {
        PlayerListener.fishingMap.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command var2, String var3, String[] args) {
        if (!sender.isOp()) return true;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!(sender instanceof Player)) return;
                Player player = (Player) sender;
                ItemStack itemInHand = player.getInventory().getItemInHand();
                if (itemInHand == null) return;
                if (!itemInHand.hasItemMeta()) return;
                if (!itemInHand.getItemMeta().hasDisplayName()) return;
                String key = itemInHand.getItemMeta().getDisplayName().replaceAll("§", "&");
                FileConfiguration blConfig = Config.load("BetterLottery");
                if (!blConfig.contains(key)) return;
                FileConfiguration config = getConfig();
                if (config.contains(key)) return;
                config.createSection(key);
                saveConfig();
                reloadConfig();
                player.sendMessage(ChatColor.GREEN + "设置鱼饵成功!");
            }
        }.runTaskAsynchronously(this);
        messagePattern = getConfig().getString("Message");
        failurePattern = getConfig().getString("Failure");
        setBlConfig(Config.load("BetterLottery"));
        sender.sendMessage(ChatColor.GREEN + "配置已重载!");
        return true;
    }

    public FileConfiguration getBlConfig() {
        return this.blConfig;
    }

    public void setBlConfig(FileConfiguration blConfig) {
        this.blConfig = blConfig;
    }

    public static BetterLotteryFishAddon getInstance() {
        return plugin;
    }
}
