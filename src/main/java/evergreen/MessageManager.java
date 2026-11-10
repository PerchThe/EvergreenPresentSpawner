package evergreen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {

    private final EvergreenPresentSpawner plugin;
    private File messageFile;
    private FileConfiguration messageConfig;

    // Pattern to match &#rrggbb hex formats
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessageManager(EvergreenPresentSpawner plugin) {
        this.plugin = plugin;
        createMessagesFile();
    }

    private void createMessagesFile() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            messageFile = new File(plugin.getDataFolder(), "messages.yml");
            if (!messageFile.exists()) {
                if (plugin.getResource("messages.yml") != null) {
                    plugin.saveResource("messages.yml", false);
                    plugin.getLogger().info("[MessageManager] Created default messages.yml.");
                } else {
                    messageFile.createNewFile();
                }
            }

            messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MessageManager] Failed to load messages.yml!", e);
        }
    }

    public void reloadMessages() {
        try {
            if (messageFile == null) {
                messageFile = new File(plugin.getDataFolder(), "messages.yml");
            }
            if (!messageFile.exists()) {
                createMessagesFile();
            } else {
                messageConfig = YamlConfiguration.loadConfiguration(messageFile);
                plugin.getLogger().info("[MessageManager] Reloaded messages.yml successfully.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MessageManager] Failed to reload messages.yml!", e);
        }
    }

    public Component getMessage(String key) {
        return getMessage(key, null);
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        if (messageConfig == null) {
            return Component.text("Missing message: " + key);
        }

        String raw = messageConfig.getString(key, "&cMissing message key: " + key + "&r");
        String prefix = messageConfig.getString("prefix", "");

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        String combined = translateHexColorCodes(prefix + raw);
        String translated = ChatColor.translateAlternateColorCodes('&', combined);
        return LegacyComponentSerializer.legacySection().deserialize(translated);
    }

    public Component getRawMessage(String key) {
        if (messageConfig == null) {
            return Component.text("Missing message: " + key);
        }
        String raw = messageConfig.getString(key, "&cMissing message key: " + key + "&r");
        String combined = translateHexColorCodes(raw);
        String translated = ChatColor.translateAlternateColorCodes('&', combined);
        return LegacyComponentSerializer.legacySection().deserialize(translated);
    }

    // Translates &#rrggbb into Bungee/Bukkit section-symbol hex format (§x§r§r§g§g§b§b)
    private String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
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
        return builder.toString();
    }
}