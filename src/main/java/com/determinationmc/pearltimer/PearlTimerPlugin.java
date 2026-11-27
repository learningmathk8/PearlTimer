package com.determinationmc.pearltimer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PearlTimerPlugin extends JavaPlugin implements Listener {

    private static final String GUI_TITLE = "PearlTimer Configuration";
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PearlPlaceholder(this).register();
        }
    }

    public double getCooldownSeconds() {
        return Math.max(0.0, getConfig().getDouble("cooldown-seconds", 10.0));
    }

    public void setCooldownSeconds(double seconds) {
        double clamped = Math.max(0.0, Math.min(300.0, Math.round(seconds * 10.0) / 10.0));
        getConfig().set("cooldown-seconds", clamped);
        saveConfig();
    }

    public boolean isSendCooldownMessage() {
        return getConfig().getBoolean("send-cooldown-message", true);
    }

    public void setSendCooldownMessage(boolean enabled) {
        getConfig().set("send-cooldown-message", enabled);
        saveConfig();
    }

    public String getReadyText() {
        String raw = getConfig().getString("ready-text", "&aReady!");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public double getRemainingSeconds(UUID uuid) {
        Long expiry = cooldowns.get(uuid);
        if (expiry == null) return 0.0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(uuid);
            return 0.0;
        }
        return remaining / 1000.0;
    }

    @EventHandler
    public void onPearlUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;

        Player player = event.getPlayer();
        double remaining = getRemainingSeconds(player.getUniqueId());

        if (remaining > 0.05) {
            event.setCancelled(true);
            if (isSendCooldownMessage()) {
                String rawMsg = getConfig().getString("cooldown-message",
                        "&cYou must wait &e{time}s &cbefore throwing another Ender Pearl!");
                String formatted = rawMsg.replace("{time}", String.format("%.1f", remaining));
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(formatted));
            }
            return;
        }

        double durationSec = getCooldownSeconds();
        if (durationSec <= 0) return;

        long durationMs = (long) (durationSec * 1000L);
        int ticks = (int) Math.round(durationSec * 20.0);

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        Bukkit.getScheduler().runTask(this, () -> player.setCooldown(Material.ENDER_PEARL, ticks));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    public void openConfigGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(GUI_TITLE, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        double current = getCooldownSeconds();

        gui.setItem(10, createItem(Material.RED_CONCRETE,
                Component.text("-5 Seconds", NamedTextColor.RED, TextDecoration.BOLD),
                List.of(Component.text("Click to subtract 5s", NamedTextColor.GRAY))));

        gui.setItem(11, createItem(Material.ORANGE_CONCRETE,
                Component.text("-1 Second", NamedTextColor.GOLD, TextDecoration.BOLD),
                List.of(Component.text("Click to subtract 1s", NamedTextColor.GRAY))));

        gui.setItem(13, createItem(Material.ENDER_PEARL,
                Component.text("Current Cooldown: " + String.format("%.1fs", current), NamedTextColor.AQUA, TextDecoration.BOLD),
                List.of(
                        Component.text("Click to reset to 10.0s", NamedTextColor.YELLOW),
                        Component.text("Saved automatically to config.yml", NamedTextColor.DARK_GRAY)
                )));

        gui.setItem(15, createItem(Material.LIME_CONCRETE,
                Component.text("+1 Second", NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(Component.text("Click to add 1s", NamedTextColor.GRAY))));

        gui.setItem(16, createItem(Material.GREEN_CONCRETE,
                Component.text("+5 Seconds", NamedTextColor.DARK_GREEN, TextDecoration.BOLD),
                List.of(Component.text("Click to add 5s", NamedTextColor.GRAY))));

        boolean msgOn = isSendCooldownMessage();
        gui.setItem(21, createItem(msgOn ? Material.BELL : Material.GRAY_DYE,
                Component.text("Cooldown Chat Warning: " + (msgOn ? "ON" : "OFF"),
                        msgOn ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD),
                List.of(Component.text("Click to toggle chat warning message", NamedTextColor.GRAY))));

        gui.setItem(23, createItem(Material.COMPASS,
                Component.text("Reload config.yml", NamedTextColor.YELLOW, TextDecoration.BOLD),
                List.of(Component.text("Click to reload changes from config.yml", NamedTextColor.GRAY))));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(GUI_TITLE)) return;

        event.setCancelled(true);
        if (!player.isOp() && !player.hasPermission("pearltimer.admin")) {
            player.closeInventory();
            return;
        }

        double current = getCooldownSeconds();
        switch (event.getRawSlot()) {
            case 10 -> {
                setCooldownSeconds(current - 5.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
                openConfigGui(player);
            }
            case 11 -> {
                setCooldownSeconds(current - 1.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                openConfigGui(player);
            }
            case 13 -> {
                setCooldownSeconds(10.0);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                openConfigGui(player);
            }
            case 15 -> {
                setCooldownSeconds(current + 1.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                openConfigGui(player);
            }
            case 16 -> {
                setCooldownSeconds(current + 5.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openConfigGui(player);
            }
            case 21 -> {
                setSendCooldownMessage(!isSendCooldownMessage());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.0f);
                openConfigGui(player);
            }
            case 23 -> {
                reloadConfig();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.0f);
                player.sendMessage(Component.text("PearlTimer config.yml reloaded!", NamedTextColor.GREEN));
                openConfigGui(player);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("pearltimerconfig")
                || (args.length > 0 && args[0].equalsIgnoreCase("config"))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the configuration GUI.");
                return true;
            }
            if (!player.isOp() && !player.hasPermission("pearltimer.admin")) {
                player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            openConfigGui(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.isOp() && !sender.hasPermission("pearltimer.admin")) {
                sender.sendMessage(Component.text("You do not have permission to reload PearlTimer.", NamedTextColor.RED));
                return true;
            }
            reloadConfig();
            sender.sendMessage(Component.text("PearlTimer config reloaded! Cooldown: " + getCooldownSeconds() + "s", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can check their pearl timer.");
            return true;
        }
        double remaining = getRemainingSeconds(player.getUniqueId());
        if (remaining <= 0.05) {
            player.sendMessage(Component.text("Your Ender Pearl is ready!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    String.format("Pearl Cooldown: %.1fs remaining", remaining),
                    NamedTextColor.YELLOW
            ));
        }
        return true;
    }

    private static class PearlPlaceholder extends PlaceholderExpansion {
        private final PearlTimerPlugin plugin;

        public PearlPlaceholder(PearlTimerPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public @NotNull String getIdentifier() {
            return "pearltimer";
        }

        @Override
        public @NotNull String getAuthor() {
            return "DeterminationMC";
        }

        @Override
        public @NotNull String getVersion() {
            return "1.2.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (player == null) return "";
            double remaining = plugin.getRemainingSeconds(player.getUniqueId());

            if (params.equalsIgnoreCase("seconds")) {
                return remaining <= 0.05 ? "0" : String.valueOf((int) Math.ceil(remaining));
            }
            if (params.equalsIgnoreCase("live")) {
                return remaining <= 0.05 ? plugin.getReadyText() : ((int) Math.ceil(remaining)) + "s";
            }
            if (params.equalsIgnoreCase("formatted")) {
                return remaining <= 0.05 ? plugin.getReadyText() : String.format("%.1fs", remaining);
            }
            return null;
        }
    }
}
