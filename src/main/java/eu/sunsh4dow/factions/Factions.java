package eu.sunsh4dow.factions;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Factions extends JavaPlugin implements Listener {

    private static final String FACTION_RED = "red";
    private static final String FACTION_BLUE = "blue";
    private Map<UUID, String> playerFactions;
    private Map<UUID, ArmorStand> factionArmorStands;
    private Map<String, EulerAngle> factionAngles;

    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        // Load player factions from storage
        loadPlayerFactions();
        getCommand("joinfaction").setExecutor(this); // Register the "/joinfaction" command
        ////getCommand("somecommand").setExecutor(new SomeCommandExecutor()); // Register another command with its own executor
        // Initialize faction armor stands
        factionArmorStands = new HashMap<>();
        // Initialize faction angles for rotation
        factionAngles = new HashMap<>();
        factionAngles.put(FACTION_RED, new EulerAngle(0, 0, 0)); // Adjust the EulerAngle values as needed
        factionAngles.put(FACTION_BLUE, new EulerAngle(0, 0, 0)); // Adjust the EulerAngle values as needed
        // Schedule the rotation task for faction armor stands
        BukkitTask rotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                rotateFactionArmorStands();
            }
        }.runTaskTimer(this, 0L, 20L); // Adjust the interval (in ticks) for rotation as needed
        getLogger().info("FactionsPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        // Save player factions to storage
        savePlayerFactions();
        getLogger().info("FactionsPlugin has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("joinfaction")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /joinfaction <faction>");
                return true;
            }
            String faction = args[0].toLowerCase();
            if (faction.equals(FACTION_RED) || faction.equals(FACTION_BLUE)) {
                setPlayerFaction(player, faction);
                player.sendMessage(ChatColor.GREEN + "You have joined the " + ChatColor.BOLD + faction + ChatColor.GREEN + " faction!");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid faction name. Available factions: red, blue");
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!hasPlayerFaction(player)) {
            // Set default faction for new players
            setPlayerFaction(player, FACTION_RED);
            player.sendMessage(ChatColor.YELLOW + "Welcome to the server! To join a faction, use the command: /joinfaction <faction>");
            player.sendMessage(ChatColor.YELLOW + "Available factions: " + FACTION_RED + ", " + FACTION_BLUE);
        }
    }
    private String getPlayerFaction(Player player) {
        UUID playerId = player.getUniqueId();
        return playerFactions.get(playerId);
    }

    private void rotateFactionArmorStands() {
        for (ArmorStand armorStand : factionArmorStands.values()) {
            String faction = armorStand.getCustomName();
            EulerAngle angle = factionAngles.get(faction);
            armorStand.setHeadPose(angle);
        }
    }
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            // Only apply the faction check to player-versus-player damage
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player target = (Player) event.getEntity();

        if (hasPlayerFaction(attacker) && hasPlayerFaction(target)) {
            String attackerFaction = getPlayerFaction(attacker);
            String targetFaction = getPlayerFaction(target);

            if (attackerFaction.equals(targetFaction)) {
                // Players of the same faction, cancel the attack
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "You cannot attack players of your own faction!");
            }
        }
    }
    private void setPlayerFaction(Player player, String faction) {
        UUID playerId = player.getUniqueId();
        playerFactions.put(playerId, faction);
    }
    private void spawnFactionArmorStand(Player player) {
        String faction = getPlayerFaction(player);
        if (faction != null) {
            Location location = player.getLocation();
            ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
            armorStand.setCustomName(faction);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            factionArmorStands.put(player.getUniqueId(), armorStand);
        }
    }

    private void despawnFactionArmorStand(Player player) {
        UUID playerId = player.getUniqueId();
        ArmorStand armorStand = factionArmorStands.remove(playerId);
        if (armorStand != null) {
            armorStand.remove();
        }
    }
    private boolean hasPlayerFaction(Player player) {
        UUID playerId = player.getUniqueId();
        return playerFactions.containsKey(playerId);
    }

    private void loadPlayerFactions() {
        File factionsFile = new File(getDataFolder(), "playerfactions.yml");
        if (!factionsFile.exists()) {
            playerFactions = new HashMap<>();
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(factionsFile);
        playerFactions = new HashMap<>();

        for (String playerIdString : config.getKeys(false)) {
            UUID playerId = UUID.fromString(playerIdString);
            String faction = config.getString(playerIdString);
            playerFactions.put(playerId, faction);
        }
    }

    private void savePlayerFactions() {
        File factionsFile = new File(getDataFolder(), "playerfactions.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(factionsFile);

        for (Map.Entry<UUID, String> entry : playerFactions.entrySet()) {
            String playerIdString = entry.getKey().toString();
            String faction = entry.getValue();
            config.set(playerIdString, faction);
        }

        try {
            config.save(factionsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
