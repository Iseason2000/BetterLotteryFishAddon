package top.iseason.bukkit.betterlotteryfishaddon;

import betterlottery2.PrizeInfo;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static betterlottery2.LotteryHelper.getChestWorld;


public class PlayerListener implements Listener {
    public static HashMap<UUID, ConfigurationSection> fishingMap = new HashMap<>();
    static SecureRandom RANDOM;

    static {
        try {
            RANDOM = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onThrowRod(PlayerFishEvent event) {
        //抛出鱼竿
        if (event.isCancelled()) return;
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (!checkBaitThenPut(item, player)) continue;
            if (item.getAmount() <= 1) {
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(i, item);
            }
            player.updateInventory();
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTakeRod(PlayerFishEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!fishingMap.containsKey(player.getUniqueId())) return;
        PlayerFishEvent.State state = event.getState();
        ConfigurationSection config = fishingMap.get(player.getUniqueId());
        if (config != null && state == PlayerFishEvent.State.CAUGHT_FISH) {
            PrizeInfo randomBait = getRandomBait(config);
            if (randomBait != null) {
                Item fish = (Item) event.getCaught();
                ItemStack itemStack = randomBait.getItemStack();
                fish.setItemStack(itemStack);
                String messagePattern = BetterLotteryFishAddon.messagePattern;
                if (randomBait.isNotice() && messagePattern != null) {
                    String message = messagePattern.replace("${player}", player.getName())
                            .replace("${fish}", itemStack.getItemMeta().hasDisplayName() ?
                                    itemStack.getItemMeta().getDisplayName() :
                                    itemStack.getType().toString())
                            .replace("${count}", String.valueOf(itemStack.getAmount()));
                    BetterLotteryFishAddon.getInstance()
                            .getServer()
                            .getOnlinePlayers()
                            .forEach(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', message)));

                }
            }
        }
        if (config != null && state == PlayerFishEvent.State.FAILED_ATTEMPT) {
            String failurePattern = BetterLotteryFishAddon.failurePattern;
            if (failurePattern != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', failurePattern));
            }
        }
        fishingMap.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fishingMap.remove(event.getPlayer().getUniqueId());
    }

    public boolean checkBaitThenPut(ItemStack item, Player player) {
        if (!item.hasItemMeta()) return false;
        ItemMeta itemMeta = item.getItemMeta();
        if (!itemMeta.hasDisplayName()) return false;
        String key = itemMeta.getDisplayName().replaceAll("§", "&");
        FileConfiguration config = BetterLotteryFishAddon.getInstance().getConfig();
        if (!config.contains(key)) return false;
        FileConfiguration blConfig = BetterLotteryFishAddon.getInstance().getBlConfig();
        ConfigurationSection configS = blConfig.getConfigurationSection(key);
        if (configS == null) return false;
        if (!configS.getBoolean("enable")) return false;
        fishingMap.put(player.getUniqueId(), configS);
        return true;
    }

    private PrizeInfo getRandomBait(ConfigurationSection config) {
        String string = config.getString("chest");
        String[] chestInfo = string.split(" ");
        int x = Integer.parseInt(chestInfo[0]);
        int y = Integer.parseInt(chestInfo[1]);
        int z = Integer.parseInt(chestInfo[2]);
        List<PrizeInfo> prizeInfos = new ArrayList<>();
        List<Integer> odds = config.getIntegerList("odds");
        List<Integer> notice = config.getIntegerList("notice");
        int maxOdds = 0;
        for (Integer odd : odds) {
            if (odd > 0)
                maxOdds += odd;
        }
        readPrize(getChestWorld().getBlockAt(x, y, z), prizeInfos, odds, notice, 0);
        readPrize(getChestWorld().getBlockAt(x, y + 1, z), prizeInfos, odds, notice, 27);
        float random = RANDOM.nextFloat() * maxOdds;
        int odd = 0;
        for (PrizeInfo prizeInfo : prizeInfos) {
            odd += prizeInfo.getOdds();
            if (random <= odd) return prizeInfo;
        }
        return null;
    }

    public void readPrize(Block block, List<PrizeInfo> prizeInfos, List<Integer> odds, List<Integer> notice, int modifier) {
        if (block.getType() != Material.CHEST) return;
        Inventory inventory = ((Chest) block.getState()).getInventory();
        for (int i = 0; i < 27; ++i) {
            ItemStack tempItemStack = inventory.getItem(i);
            if (tempItemStack == null) continue;
            prizeInfos.add(new PrizeInfo(tempItemStack.clone(), odds.get(i + modifier), notice.get(i) == 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerRightUseItem(PlayerInteractEvent event) {
        ItemStack itemStack = event.getPlayer().getItemInHand();
        if ((event.getAction().equals(Action.RIGHT_CLICK_AIR) || event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) && itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            FileConfiguration config = BetterLotteryFishAddon.getInstance().getConfig();
            if (!itemStack.hasItemMeta()) return;
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (!itemMeta.hasDisplayName()) return;
            String key = itemMeta.getDisplayName().replaceAll("§", "&");
            if (config.contains(key)) event.getPlayer().closeInventory();
        }
    }
}
