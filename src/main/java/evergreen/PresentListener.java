package evergreen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class PresentListener implements Listener {

    private final EvergreenPresentSpawner plugin;

    public PresentListener(EvergreenPresentSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        // Check if entity holds our custom tracking key
        if (entity.getPersistentDataContainer().has(plugin.getPresentKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            // 1. Remove the present entity
            entity.remove();

            // 2. Global found announcement
            Component announcement = Component.text(player.getName(), NamedTextColor.GOLD)
                    .append(Component.text(" has found the hidden present!", NamedTextColor.YELLOW));
            Bukkit.broadcast(announcement);

            // 3. Distribute rewards
            plugin.getLootManager().distributeLoot(player);
        }
    }
}