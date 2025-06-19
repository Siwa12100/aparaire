package siwa.valorium;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Aparaire extends JavaPlugin implements Listener {

    private final Set<UUID> whitelistUUIDs = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Aparaire plugin lancé !");
        Bukkit.getPluginManager().registerEvents(this, this);
        reloadWhitelist();
    }

    @Override
    public void onDisable() {
        getLogger().info("Aparaire plugin arrêté !");
    }

    private void reloadWhitelist() {
        whitelistUUIDs.clear();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            whitelistUUIDs.add(player.getUniqueId());
        }
        getLogger().info("Whitelist rechargée. Joueurs autorisés : " + whitelistUUIDs.size());
    }

    private boolean estAutorise(Player player) {
        return player.isOp() || whitelistUUIDs.contains(player.getUniqueId());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !estAutorise(player)) {
            InventoryType type = event.getInventory().getType();
            switch (type) {
                case CHEST:
                case BARREL:
                case DISPENSER:
                case DROPPER:
                case FURNACE:
                case BLAST_FURNACE:
                case SMOKER:
                case HOPPER:
                case SHULKER_BOX:
                case ENDER_CHEST:
                    event.setCancelled(true);
                    player.sendMessage("§cTu n'es pas autorisé à accéder à ce conteneur.");
                    break;
                default:
                    break;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!estAutorise(player)) {
            donnerItemsRestreints(player);
        }
    }

    private void donnerItemsRestreints(Player player) {
        if (!getConfig().getBoolean("mode-confort", true)) {
            return;
        }

        boolean hasSpyglass = player.getInventory().contains(Material.SPYGLASS);
        boolean hasChorus = player.getInventory().contains(Material.CHORUS_FRUIT);

        if (!hasSpyglass) {
            player.getInventory().addItem(new ItemStack(Material.SPYGLASS, 1));
        }

        if (!hasChorus) {
            player.getInventory().addItem(new ItemStack(Material.CHORUS_FRUIT, 10));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("aparairereload")) {
            if (sender instanceof Player p && !p.isOp()) {
                sender.sendMessage("§cTu n'as pas la permission.");
                return true;
            }

            reloadWhitelist();
            reloadConfig();
            sender.sendMessage("§aWhitelist et configuration rechargées !");
            return true;
        }
        return false;
    }
}
