package evergreen;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvergreenCommand implements CommandExecutor, TabCompleter {

    private final EvergreenPresentSpawner plugin;

    public EvergreenCommand(EvergreenPresentSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessageManager msg = plugin.getMessageManager();

        if (!sender.hasPermission("evergreenpresentspawner.admin")) {
            sender.sendMessage(msg.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(msg.getRawMessage("command-header"));
            sender.sendMessage(msg.getRawMessage("command-settime-usage"));
            sender.sendMessage(msg.getRawMessage("command-spawn-usage"));
            sender.sendMessage(msg.getRawMessage("command-reload-usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "settime":
                if (args.length < 3) {
                    sender.sendMessage(msg.getMessage("command-settime-invalid"));
                    return true;
                }
                try {
                    long min = Long.parseLong(args[1]);
                    long max = Long.parseLong(args[2]);

                    if (min > max) {
                        sender.sendMessage(msg.getMessage("command-settime-range-error"));
                        return true;
                    }

                    plugin.getConfig().set("spawn-timer.min-seconds", min);
                    plugin.getConfig().set("spawn-timer.max-seconds", max);
                    plugin.saveConfig();

                    plugin.getSpawnerManager().cancelTimer();
                    plugin.getSpawnerManager().startSpawnTimer();

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("min", String.valueOf(min));
                    placeholders.put("max", String.valueOf(max));

                    sender.sendMessage(msg.getMessage("command-settime-success", placeholders));
                } catch (NumberFormatException e) {
                    sender.sendMessage(msg.getMessage("command-settime-number-error"));
                }
                break;

            case "spawn":
                plugin.getSpawnerManager().spawnPresent();
                sender.sendMessage(msg.getMessage("command-spawn-success"));
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.getLootManager().reloadLootTable();
                plugin.getMessageManager().reloadMessages();
                plugin.getSpawnerManager().restartSpawnTimer();

                sender.sendMessage(msg.getMessage("command-reload-success"));
                break;

            default:
                sender.sendMessage(msg.getMessage("command-unknown"));
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("evergreenpresentspawner.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("settime", "spawn", "reload");
            String input = args[0].toLowerCase();
            for (String sub : subcommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("settime")) {
            completions.add("<minSeconds>");
            return completions;
        } else if (args.length == 3 && args[0].equalsIgnoreCase("settime")) {
            completions.add("<maxSeconds>");
            return completions;
        }

        return Collections.emptyList();
    }
}