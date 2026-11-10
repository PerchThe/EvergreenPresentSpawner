package evergreen;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerManager {

    private final EvergreenPresentSpawner plugin;
    private BukkitTask currentTask;
    private final Random random = new Random();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public SpawnerManager(EvergreenPresentSpawner plugin) {
        this.plugin = plugin;
    }

    public void startSpawnTimer() {
        scheduleNextSpawn();
    }

    public void restartSpawnTimer() {
        cancelTimer();
        scheduleNextSpawn();
    }

    private void scheduleNextSpawn() {
        long minSec = plugin.getConfig().getLong("spawn-timer.min-seconds", 3600);
        long maxSec = plugin.getConfig().getLong("spawn-timer.max-seconds", 7200);

        if (minSec <= 0 || maxSec <= 0 || minSec > maxSec) {
            plugin.getLogger().severe("[SpawnerManager] Invalid spawn-timer configuration! min-seconds: " + minSec + ", max-seconds: " + maxSec + ". Falling back to 3600s - 7200s.");
            minSec = 3600;
            maxSec = 7200;
        }

        long delaySeconds = ThreadLocalRandom.current().nextLong(minSec, maxSec + 1);
        long delayTicks = delaySeconds * 20L;

        currentTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnPresent();
            scheduleNextSpawn();
        }, delayTicks);
    }

    public void spawnPresent() {
        try {
            ConfigurationSection regionsSec = plugin.getConfig().getConfigurationSection("regions");
            if (regionsSec == null || regionsSec.getKeys(false).isEmpty()) {
                plugin.getLogger().severe("[SpawnerManager] Failed to spawn present: No spawn regions found in config.yml!");
                return;
            }

            List<String> regionKeys = new ArrayList<>(regionsSec.getKeys(false));
            String selectedKey = regionKeys.get(random.nextInt(regionKeys.size()));
            ConfigurationSection region = regionsSec.getConfigurationSection(selectedKey);

            if (region == null) {
                plugin.getLogger().warning("[SpawnerManager] Region section '" + selectedKey + "' is null.");
                return;
            }

            String worldName = region.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().severe("[SpawnerManager] World '" + worldName + "' specified in region '" + selectedKey + "' does not exist or is not loaded!");
                return;
            }

            int x1 = region.getInt("x1");
            int z1 = region.getInt("z1");
            int x2 = region.getInt("x2");
            int z2 = region.getInt("z2");

            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            int targetX = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int targetZ = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);

            int rawY = world.getHighestBlockYAt(targetX, targetZ);
            Block topBlock = world.getBlockAt(targetX, rawY, targetZ);

            while (rawY > world.getMinHeight() && (!topBlock.getType().isSolid() || topBlock.isPassable())) {
                rawY--;
                topBlock = world.getBlockAt(targetX, rawY, targetZ);
            }

            int targetY = rawY + 1;

            boolean aboveSeaLevelOnly = plugin.getConfig().getBoolean("restrictions.above-sea-level-only", true);
            int minYLevel = plugin.getConfig().getInt("restrictions.min-y-level", 63);

            if (aboveSeaLevelOnly && targetY < minYLevel) {
                targetY = minYLevel;
            }

            // Lowering Y by 0.5 so the head sits flush against the block top surface
            double exactY = targetY - 0.5;
            Location spawnLocation = new Location(world, targetX + 0.5, exactY, targetZ + 0.5);

            world.spawn(spawnLocation, ArmorStand.class, armorStand -> {
                armorStand.setVisible(false);
                armorStand.setSmall(true);
                armorStand.setMarker(false);
                armorStand.setGravity(false);
                armorStand.setInvulnerable(true);

                ItemStack presentHead = createPresentHead();
                if (armorStand.getEquipment() != null) {
                    armorStand.getEquipment().setHelmet(presentHead);
                }

                armorStand.getPersistentDataContainer().set(plugin.getPresentKey(), PersistentDataType.BYTE, (byte) 1);

                // Native Paper TextDisplay Hologram
                TextDisplay hologram = spawnHologram(world, spawnLocation);
                if (hologram != null) {
                    armorStand.addPassenger(hologram);
                }

                // Decay Timer Implementation
                boolean decayEnabled = plugin.getConfig().getBoolean("decay-timer.enabled", true);
                long decaySeconds = plugin.getConfig().getLong("decay-timer.seconds", 1800);

                if (decayEnabled && decaySeconds > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (armorStand.isValid() && !armorStand.isDead()) {
                            if (hologram != null && hologram.isValid()) {
                                hologram.remove();
                            }
                            armorStand.remove();

                            Map<String, String> decayPlaceholders = new HashMap<>();
                            decayPlaceholders.put("region", selectedKey);
                            Component decayMessage = plugin.getMessageManager().getMessage("decay-announcement", decayPlaceholders);

                            for (Player player : world.getPlayers()) {
                                player.sendMessage(decayMessage);
                            }

                            plugin.getLogger().info("[SpawnerManager] Present in region " + selectedKey + " has decayed due to inactivity.");

                            spawnPresent();
                        }
                    }, decaySeconds * 20L);
                }
            });

            plugin.getLogger().info("[SpawnerManager] Present successfully spawned! Region: " + selectedKey +
                    " | World: " + world.getName() +
                    " | X: " + spawnLocation.getBlockX() +
                    " | Y: " + spawnLocation.getBlockY() +
                    " | Z: " + spawnLocation.getBlockZ());

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("region", selectedKey);

            Component announcement = plugin.getMessageManager().getMessage("spawn-announcement", placeholders);

            for (Player player : world.getPlayers()) {
                player.sendMessage(announcement);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[SpawnerManager] An unexpected error occurred while spawning a present!", e);
        }
    }

    private TextDisplay spawnHologram(World world, Location baseLocation) {
        boolean enabled = plugin.getConfig().getBoolean("hologram.enabled", true);
        if (!enabled) return null;

        double offset = plugin.getConfig().getDouble("hologram.height-offset", 0.65);
        Location holoLoc = baseLocation.clone().add(0, offset, 0);

        String rawText = plugin.getConfig().getString("hologram.text", "&e&l[?] Present");

        // Parse hex and legacy color codes
        Matcher matcher = HEX_PATTERN.matcher(rawText);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);

        String translated = ChatColor.translateAlternateColorCodes('&', builder.toString());
        Component textComponent = LegacyComponentSerializer.legacySection().deserialize(translated);

        return world.spawn(holoLoc, TextDisplay.class, textDisplay -> {
            textDisplay.text(textComponent);
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent background
            textDisplay.setSeeThrough(false);
            textDisplay.setShadowed(true);
        });
    }

    private ItemStack createPresentHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Holiday Present"));

            String base64Texture = plugin.getConfig().getString("present-head-texture", "");
            if (!base64Texture.isEmpty()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    profile.setProperty(new ProfileProperty("textures", base64Texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[SpawnerManager] Failed to apply Base64 texture to present head.", e);
                }
            }

            head.setItemMeta(meta);
        }
        return head;
    }

    public void cancelTimer() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }
}