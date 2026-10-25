package ru.deathswear.dsitembridge;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DSItemBridge extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private ItemListener itemListener;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        this.itemListener = new ItemListener(this, configManager);
        getServer().getPluginManager().registerEvents(itemListener, this);

        if (getCommand("dsbridge") != null) {
            getCommand("dsbridge").setExecutor(this);
            getCommand("dsbridge").setTabCompleter(this);
        }

        getLogger().info("DSItemBridge v" + getDescription().getVersion() + " has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DSItemBridge disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dsitembridge.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            configManager.loadConfig();
            sender.sendMessage(ChatColor.GREEN + "[DSItemBridge] Конфигурация успешно перезагружена!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("check")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок.");
                return true;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();
            String id = itemListener.getMythicOrVanillaId(hand);

            player.sendMessage(ChatColor.GOLD + "=== [DSItemBridge Info] ===");
            player.sendMessage(ChatColor.YELLOW + "Идентификатор предмета: " + ChatColor.WHITE + (id != null ? id : "AIR / Пусто"));

            if (id != null) {
                List<String> craftAdvs = configManager.getCraftAdvancements(id);
                player.sendMessage(ChatColor.YELLOW + "Ачивка за крафт: " +
                        (craftAdvs != null && !craftAdvs.isEmpty() ? ChatColor.AQUA + String.join(", ", craftAdvs) : ChatColor.GRAY + "нет"));

                List<String> acqAdvs = configManager.getAcquireAdvancements(id);
                player.sendMessage(ChatColor.YELLOW + "Ачивка за получение: " +
                        (acqAdvs != null && !acqAdvs.isEmpty() ? ChatColor.AQUA + String.join(", ", acqAdvs) : ChatColor.GRAY + "нет"));

                List<NamespacedKey> recipes = configManager.getRecipesFor(id);
                if (recipes != null && !recipes.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Открываемые рецепты (" + recipes.size() + "):");
                    for (NamespacedKey rKey : recipes) {
                        boolean discovered = player.hasDiscoveredRecipe(rKey);
                        player.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + rKey.toString() +
                                (discovered ? ChatColor.GREEN + " [Изучен]" : ChatColor.RED + " [Не изучен]"));
                    }
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Открываемые рецепты: " + ChatColor.GRAY + "нет");
                }
            }
            player.sendMessage(ChatColor.GOLD + "==========================");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== DSItemBridge v" + getDescription().getVersion() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "/dsbridge reload " + ChatColor.GRAY + "- Перезагрузить конфигурацию");
        sender.sendMessage(ChatColor.YELLOW + "/dsbridge check  " + ChatColor.GRAY + "- Проверить предмет в руке (Mythic ID, рецепты, ачивки)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dsitembridge.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) sub.add("reload");
            if ("check".startsWith(args[0].toLowerCase())) sub.add("check");
            return sub;
        }
        return Collections.emptyList();
    }
}
