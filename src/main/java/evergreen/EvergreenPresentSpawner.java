package evergreen;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class EvergreenPresentSpawner extends JavaPlugin {

    private NamespacedKey presentKey;
    private MessageManager messageManager;
    private LootManager lootManager;
    private SpawnerManager spawnerManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        presentKey = new NamespacedKey(this, "is_evergreen_present");
        messageManager = new MessageManager(this);
        lootManager = new LootManager(this);
        spawnerManager = new SpawnerManager(this);

        getServer().getPluginManager().registerEvents(new PresentListener(this), this);

        EvergreenCommand commandHandler = new EvergreenCommand(this);
        if (getCommand("evergreenpresentspawner") != null) {
            getCommand("evergreenpresentspawner").setExecutor(commandHandler);
            getCommand("evergreenpresentspawner").setTabCompleter(commandHandler);
            getLogger().info("Successfully registered /evergreenpresentspawner command and tab completer.");
        } else {
            getLogger().severe("Failed to register /evergreenpresentspawner command! Check your plugin.yml declarations.");
        }

        spawnerManager.startSpawnTimer();
        getLogger().info("EvergreenPresentSpawner has been enabled!");
    }

    @Override
    public void onDisable() {
        if (spawnerManager != null) {
            spawnerManager.cancelTimer();
        }
        getLogger().info("EvergreenPresentSpawner has been disabled.");
    }

    public NamespacedKey getPresentKey() {
        return presentKey;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }
}