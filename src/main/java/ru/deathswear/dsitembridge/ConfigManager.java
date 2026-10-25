package ru.deathswear.dsitembridge;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

import java.util.*;

public class ConfigManager {

    private final DSItemBridge plugin;
    private final Map<String, List<String>> craftAdvancements = new HashMap<>();
    private final Map<String, List<String>> acquireAdvancements = new HashMap<>();
    private final Map<String, List<NamespacedKey>> recipeUnlocks = new HashMap<>();

    private boolean debug = false;
    private boolean soundEnabled = true;
    private Sound soundType = Sound.ENTITY_PLAYER_LEVELUP;
    private float soundVolume = 0.5f;
    private float soundPitch = 2.0f;

    private boolean chatEnabled = false;
    private String chatMessage = "&a[+] Открыт новый рецепт: &f%recipe%";

    private boolean actionbarEnabled = true;
    private String actionbarMessage = "&a✦ Открыт новый рецепт крафта!";

    public ConfigManager(DSItemBridge plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        craftAdvancements.clear();
        acquireAdvancements.clear();
        recipeUnlocks.clear();

        this.debug = config.getBoolean("settings.debug", false);

        // Sound settings
        this.soundEnabled = config.getBoolean("settings.notifications.sound.enabled", true);
        String soundName = config.getString("settings.notifications.sound.type", "ENTITY_PLAYER_LEVELUP");
        try {
            this.soundType = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound type: " + soundName + ", defaulting to ENTITY_PLAYER_LEVELUP");
            this.soundType = Sound.ENTITY_PLAYER_LEVELUP;
        }
        this.soundVolume = (float) config.getDouble("settings.notifications.sound.volume", 0.5);
        this.soundPitch = (float) config.getDouble("settings.notifications.sound.pitch", 2.0);

        // Message settings
        this.chatEnabled = config.getBoolean("settings.notifications.chat.enabled", false);
        this.chatMessage = config.getString("settings.notifications.chat.message", "&a[+] Открыт новый рецепт: &f%recipe%");

        this.actionbarEnabled = config.getBoolean("settings.notifications.actionbar.enabled", true);
        this.actionbarMessage = config.getString("settings.notifications.actionbar.message", "&a✦ Открыт новый рецепт крафта!");

        // Craft Advancements
        ConfigurationSection craftSec = config.getConfigurationSection("craft-advancements");
        if (craftSec != null) {
            for (String key : craftSec.getKeys(false)) {
                if (craftSec.isList(key)) {
                    List<String> list = craftSec.getStringList(key);
                    if (!list.isEmpty()) {
                        craftAdvancements.put(key, list);
                    }
                } else {
                    String adv = craftSec.getString(key);
                    if (adv != null && !adv.isEmpty()) {
                        craftAdvancements.put(key, Collections.singletonList(adv));
                    }
                }
            }
        }

        // Acquire Advancements
        ConfigurationSection acqSec = config.getConfigurationSection("acquire-advancements");
        if (acqSec != null) {
            for (String key : acqSec.getKeys(false)) {
                if (acqSec.isList(key)) {
                    List<String> list = acqSec.getStringList(key);
                    if (!list.isEmpty()) {
                        acquireAdvancements.put(key, list);
                    }
                } else {
                    String adv = acqSec.getString(key);
                    if (adv != null && !adv.isEmpty()) {
                        acquireAdvancements.put(key, Collections.singletonList(adv));
                    }
                }
            }
        }

        // Recipe Unlocks
        ConfigurationSection recipeSec = config.getConfigurationSection("recipe-unlocks");
        if (recipeSec != null) {
            for (String key : recipeSec.getKeys(false)) {
                List<String> list = recipeSec.getStringList(key);
                List<NamespacedKey> keys = new ArrayList<>();
                for (String rStr : list) {
                    NamespacedKey nKey = parseNamespacedKey(rStr);
                    if (nKey != null) {
                        keys.add(nKey);
                    } else {
                        plugin.getLogger().warning("Invalid recipe key: " + rStr + " under item " + key);
                    }
                }
                if (!keys.isEmpty()) {
                    recipeUnlocks.put(key, keys);
                }
            }
        }

        plugin.getLogger().info("Config loaded: " + craftAdvancements.size() + " craft achievement mappings, " +
                recipeUnlocks.size() + " recipe unlock mappings, " +
                acquireAdvancements.size() + " acquire achievement mappings.");
    }

    private NamespacedKey parseNamespacedKey(String s) {
        if (s == null || s.isEmpty()) return null;
        s = s.trim();
        if (s.contains(":")) {
            String[] parts = s.split(":", 2);
            return new NamespacedKey(parts[0].toLowerCase(), parts[1].toLowerCase());
        } else {
            return NamespacedKey.minecraft(s.toLowerCase());
        }
    }

    public List<String> getCraftAdvancements(String itemId) {
        return craftAdvancements.get(itemId);
    }

    public List<String> getAcquireAdvancements(String itemId) {
        return acquireAdvancements.get(itemId);
    }

    public List<NamespacedKey> getRecipesFor(String itemId) {
        return recipeUnlocks.get(itemId);
    }

    public boolean isDebug() {
        return debug;
    }

    public void playDiscoveryFeedback(Player player, NamespacedKey recipeKey) {
        if (soundEnabled && soundType != null) {
            player.playSound(player.getLocation(), soundType, soundVolume, soundPitch);
        }

        if (actionbarEnabled && actionbarMessage != null && !actionbarMessage.isEmpty()) {
            player.sendActionBar(Component.text(ChatColor.translateAlternateColorCodes('&', actionbarMessage)));
        }

        if (chatEnabled && chatMessage != null && !chatMessage.isEmpty()) {
            String msg = chatMessage.replace("%recipe%", recipeKey.getKey());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }
}
