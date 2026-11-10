package evergreen;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

public class LootManager {

    private final EvergreenPresentSpawner plugin;
    private File lootFile;
    private FileConfiguration lootConfig;
    private final Random random = new Random();

    public LootManager(EvergreenPresentSpawner plugin) {
        this.plugin = plugin;
        createLootTableFile();
    }

    private void createLootTableFile() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            lootFile = new File(plugin.getDataFolder(), "loot-tables.yml");
            if (!lootFile.exists()) {
                if (plugin.getResource("loot-tables.yml") != null) {
                    plugin.saveResource("loot-tables.yml", false);
                    plugin.getLogger().info("[LootManager] Successfully created default loot-tables.yml.");
                } else {
                    plugin.getLogger().severe("[LootManager] Could not find loot-tables.yml in plugin jar resources!");
                    lootFile.createNewFile();
                }
            }

            lootConfig = YamlConfiguration.loadConfiguration(lootFile);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[LootManager] Failed to load or create loot-tables.yml!", e);
        }
    }

    public void reloadLootTable() {
        try {
            if (lootFile == null) {
                lootFile = new File(plugin.getDataFolder(), "loot-tables.yml");
            }
            if (!lootFile.exists()) {
                createLootTableFile();
            } else {
                lootConfig = YamlConfiguration.loadConfiguration(lootFile);
                plugin.getLogger().info("[LootManager] Reloaded loot-tables.yml successfully.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[LootManager] Error encountered while reloading loot-tables.yml!", e);
        }
    }

    public void distributeLoot(Player player) {
        if (lootConfig == null) {
            plugin.getLogger().severe("[LootManager] Cannot distribute loot: lootConfig is null.");
            return;
        }

        ConfigurationSection rewardsSec = lootConfig.getConfigurationSection("rewards");
        if (rewardsSec == null || rewardsSec.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("[LootManager] No rewards found inside 'rewards' section of loot-tables.yml!");
            player.sendMessage(plugin.getMessageManager().getMessage("no-rewards-configured"));
            return;
        }

        Map<String, String> commandsMap = new HashMap<>();
        Map<String, Integer> weightsMap = new HashMap<>();
        int totalWeight = 0;

        for (String key : rewardsSec.getKeys(false)) {
            String command = rewardsSec.getString(key + ".command");
            int weight = rewardsSec.getInt(key + ".weight", 10);

            if (command == null || command.trim().isEmpty()) {
                plugin.getLogger().warning("[LootManager] Reward '" + key + "' has no command configured. Skipping.");
                continue;
            }

            if (weight <= 0) {
                plugin.getLogger().warning("[LootManager] Reward '" + key + "' has weight <= 0. Skipping.");
                continue;
            }

            commandsMap.put(key, command);
            weightsMap.put(key, weight);
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            plugin.getLogger().severe("[LootManager] Total weight across all valid rewards is 0 or less. Cannot pick loot.");
            return;
        }

        int selectedWeight = random.nextInt(totalWeight);
        int currentWeightSum = 0;
        String chosenCommand = null;

        for (String key : weightsMap.keySet()) {
            currentWeightSum += weightsMap.get(key);
            if (selectedWeight < currentWeightSum) {
                chosenCommand = commandsMap.get(key);
                break;
            }
        }

        if (chosenCommand != null) {
            String finalCommand = chosenCommand.replace("%player%", player.getName());

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    if (!success) {
                        plugin.getLogger().warning("[LootManager] Command execution returned false: '" + finalCommand + "'");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "[LootManager] Exception occurred while executing reward command: '" + finalCommand + "'", e);
                }
            });
        }
    }
}