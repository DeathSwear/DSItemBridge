package ru.deathswear.dsitembridge;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ItemListener implements Listener {

    private final DSItemBridge plugin;
    private final ConfigManager configManager;
    private final boolean hasMythicMobs;

    public ItemListener(DSItemBridge plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.hasMythicMobs = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /**
     * Resolves the MythicMobs item type, or falls back to Bukkit Material name.
     */
    public String getMythicOrVanillaId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        if (hasMythicMobs) {
            try {
                if (MythicBukkit.inst().getItemManager().isMythicItem(item)) {
                    String mType = MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
                    if (mType != null && !mType.isEmpty()) {
                        return mType;
                    }
                }
            } catch (Throwable t) {
                // If MythicBukkit is reloading or unavailable
            }
        }

        return item.getType().name();
    }

    /**
     * Grants a PremiumAdvancements advancement via console command.
     */
    public void grantAdvancement(Player player, String advancement) {
        if (player == null || advancement == null || advancement.isEmpty()) return;

        String cmd = "padv give " + player.getName() + " " + advancement + " --silent";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Executed: " + cmd);
        }
    }

    /**
     * Processes any item entering or being touched by the player's inventory.
     */
    public void processItemAcquisition(Player player, ItemStack item) {
        String id = getMythicOrVanillaId(item);
        if (id == null) return;

        // 1. Check Recipe Unlocks
        List<NamespacedKey> recipes = configManager.getRecipesFor(id);
        if (recipes != null && !recipes.isEmpty()) {
            boolean anyNew = false;
            NamespacedKey lastKey = null;
            for (NamespacedKey rKey : recipes) {
                if (!player.hasDiscoveredRecipe(rKey)) {
                    player.discoverRecipe(rKey);
                    anyNew = true;
                    lastKey = rKey;
                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[DEBUG] Unlocked recipe " + rKey + " for " + player.getName() + " via " + id);
                    }
                }
            }
            if (anyNew && lastKey != null) {
                configManager.playDiscoveryFeedback(player, lastKey);
            }
        }

        // 2. Check Acquire Advancements
        List<String> acqAdvs = configManager.getAcquireAdvancements(id);
        if (acqAdvs != null && !acqAdvs.isEmpty()) {
            for (String adv : acqAdvs) {
                grantAdvancement(player, adv);
            }
        }
    }

    /**
     * CRAFT ITEM EVENT: Player crafts an item.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            if (event.getRecipe() != null) {
                result = event.getRecipe().getResult();
            }
        }
        if (result == null || result.getType() == Material.AIR) return;

        String id = getMythicOrVanillaId(result);
        if (id == null) return;

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] " + player.getName() + " crafted " + id);
        }

        // 1. Grant Craft Advancements
        List<String> craftAdvs = configManager.getCraftAdvancements(id);
        if (craftAdvs != null && !craftAdvs.isEmpty()) {
            for (String adv : craftAdvs) {
                grantAdvancement(player, adv);
            }
        }

        // 2. Check if crafting this item also unlocks any recipes or acquire achievements
        processItemAcquisition(player, result);
    }

    /**
     * INVENTORY CLICK EVENT: Handles container looting (LootContainers, chests, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        Inventory topInv = event.getView().getTopInventory();
        Inventory bottomInv = event.getView().getBottomInventory();

        // Check if interaction involves an external container (top inventory is not player's inventory)
        boolean isExternalContainer = topInv != null && !topInv.equals(bottomInv) && !topInv.equals(player.getInventory());

        if (!isExternalContainer) {
            return;
        }

        // Case A: Shift-click from external container into player inventory
        if (event.isShiftClick() && clickedInv.equals(topInv)) {
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
            return;
        }

        // Case B: Number key swap from external container into hotbar
        if (event.getClick() == ClickType.NUMBER_KEY && clickedInv.equals(topInv)) {
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
            return;
        }

        // Case C: Picking up item with cursor from external container
        if (clickedInv.equals(topInv)) {
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
        }
    }

    /**
     * ENTITY PICKUP ITEM EVENT: Player picks up an item from the floor.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();
            processItemAcquisition(player, item);
        }
    }

    /**
     * INVENTORY DRAG EVENT: Player drags items from external container into inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (topInv == null || topInv.equals(player.getInventory())) return;

        int topSize = topInv.getSize();
        boolean touchedPlayerInventory = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                touchedPlayerInventory = true;
                break;
            }
        }

        if (touchedPlayerInventory) {
            ItemStack item = event.getOldCursor();
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
        }
    }
}
