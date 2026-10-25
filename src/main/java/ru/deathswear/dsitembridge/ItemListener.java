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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ItemListener implements Listener {

    private final DSItemBridge plugin;
    private final ConfigManager configManager;
    private final boolean hasMythicMobs;
    private final NamespacedKey mythicTypeKey;

    public ItemListener(DSItemBridge plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.hasMythicMobs = Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        this.mythicTypeKey = new NamespacedKey("mythicmobs", "type");
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
                // 1. MythicBukkit API lookup
                String mType = MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
                if (mType != null && !mType.isEmpty()) {
                    return mType;
                }
            } catch (Throwable ignored) {
            }

            try {
                // 2. Direct PersistentDataContainer check for "mythicmobs:type"
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.getPersistentDataContainer().has(mythicTypeKey, PersistentDataType.STRING)) {
                        String type = meta.getPersistentDataContainer().get(mythicTypeKey, PersistentDataType.STRING);
                        if (type != null && !type.isEmpty()) {
                            return type;
                        }
                    }
                }
            } catch (Throwable ignored) {
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
     * Handles crafting of an item: grants craft advancements and processes acquisition.
     */
    public void handleCraft(Player player, String id, ItemStack craftedItem) {
        if (player == null || id == null) return;

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Craft detected for " + player.getName() + ": " + id);
        }

        // 1. Grant Craft Advancements
        List<String> craftAdvs = configManager.getCraftAdvancements(id);
        if (craftAdvs != null && !craftAdvs.isEmpty()) {
            for (String adv : craftAdvs) {
                grantAdvancement(player, adv);
            }
        }

        // 2. Process Acquisition (recipe unlocks & acquire achievements)
        processItemAcquisition(player, craftedItem);
    }

    /**
     * Processes any item entering or being touched by the player's inventory.
     */
    public void processItemAcquisition(Player player, ItemStack item) {
        if (player == null || item == null || item.getType() == Material.AIR) return;

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
     * Scans the player's entire inventory (hotbar, main, armor, offhand, cursor)
     * and processes all items for recipe discoveries and acquire advancements.
     */
    public void scanPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            processItemAcquisition(player, cursor);
        }
    }

    // ==========================================
    // CRAFT DETECTION (Multi-layer)
    // ==========================================

    /**
     * Layer 1: InventoryClickEvent on RESULT slot.
     * Priority LOW executes before Crucible cancels the event.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onCraftSlotClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean isResult = false;
        if (event.getSlotType() == InventoryType.SlotType.RESULT) {
            isResult = true;
        } else if (event.getRawSlot() == 0) {
            Inventory top = event.getView().getTopInventory();
            if (top instanceof CraftingInventory || top.getType() == InventoryType.WORKBENCH || top.getType() == InventoryType.CRAFTING) {
                isResult = true;
            }
        }

        if (!isResult) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            if (event.getInventory() instanceof CraftingInventory ci) {
                result = ci.getResult();
            }
        }
        if (result == null || result.getType() == Material.AIR) return;

        String id = getMythicOrVanillaId(result);
        if (id != null) {
            handleCraft(player, id, result);
        }

        // Guaranteed next-tick scan for Shift-click multi-crafts
        Bukkit.getScheduler().runTask(plugin, () -> scanPlayerInventory(player));
    }

    /**
     * Layer 2: Vanilla CraftItemEvent fallback.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanillaCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) {
            if (event.getRecipe() != null) {
                result = event.getRecipe().getResult();
            }
        }
        if (result == null || result.getType() == Material.AIR) return;

        String id = getMythicOrVanillaId(result);
        if (id != null) {
            handleCraft(player, id, result);
        }

        Bukkit.getScheduler().runTask(plugin, () -> scanPlayerInventory(player));
    }

    // ==========================================
    // ITEM ACQUISITION (Chests, LootContainers, Drag, Pickup)
    // ==========================================

    /**
     * Catches all inventory clicks (taking from chest, shift-click, number keys, moving items).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGeneralInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Skip slot 0 crafting result here to avoid duplicate handling
        if (event.getSlotType() == InventoryType.SlotType.RESULT) return;

        // 1. Immediate check on clicked item
        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR) {
            processItemAcquisition(player, current);
        }

        // 2. Immediate check on cursor item
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            processItemAcquisition(player, cursor);
        }

        // 3. Hotbar button swap check
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
            if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                processItemAcquisition(player, hotbarItem);
            }
        }

        // 4. Guaranteed next-tick scan of player's inventory once click changes are committed
        Bukkit.getScheduler().runTask(plugin, () -> scanPlayerInventory(player));
    }

    /**
     * Catches closing any container (chests, LootContainers, barrels, workbenches).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scanPlayerInventory(player);
        }
    }

    /**
     * Catches picking up items from the floor.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();
            processItemAcquisition(player, item);
            Bukkit.getScheduler().runTask(plugin, () -> scanPlayerInventory(player));
        }
    }

    /**
     * Catches dragging items across slots.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            ItemStack item = event.getOldCursor();
            if (item != null && item.getType() != Material.AIR) {
                processItemAcquisition(player, item);
            }
            Bukkit.getScheduler().runTask(plugin, () -> scanPlayerInventory(player));
        }
    }

    /**
     * Scan inventory on join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> scanPlayerInventory(event.getPlayer()), 20L);
    }
}
