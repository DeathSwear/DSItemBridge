package ru.deathswear.dsitembridge;

import io.lumine.mythiccrucible.events.MythicCraftItemEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class CrucibleCraftListener implements Listener {

    private final DSItemBridge plugin;
    private final ItemListener itemListener;

    public CrucibleCraftListener(DSItemBridge plugin, ItemListener itemListener) {
        this.plugin = plugin;
        this.itemListener = itemListener;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicCraft(MythicCraftItemEvent event) {
        if (event.getTrigger() == null) return;
        if (!(event.getTrigger().getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getTrigger().getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            result = event.getTrigger().getCursor();
        }
        if (result == null || result.getType() == Material.AIR) return;

        String id = itemListener.getMythicOrVanillaId(result);
        if (id != null) {
            itemListener.handleCraft(player, id, result);
        }
    }
}
