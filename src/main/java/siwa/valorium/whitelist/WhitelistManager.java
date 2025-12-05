package siwa.valorium.whitelist;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

public final class WhitelistManager implements Listener{
    
    private final Set<UUID> whitelistUUIDs = new HashSet<>();

    private static final Pattern WHITELIST_COMMAND_PATTERN = Pattern.compile("^(?:\\w+:)?whitelist(\\s|$)", Pattern.CASE_INSENSITIVE);

    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public WhitelistManager(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
        reloadWhitelist();
    }

      /**
     * Recharge la liste des joueurs autorisés depuis la whitelist du serveur
     */
    public void reloadWhitelist() {
        whitelistUUIDs.clear();
        for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
            whitelistUUIDs.add(player.getUniqueId());
        }
        plugin.getLogger().log(Level.INFO, "Whitelist rechargée. Joueurs autorisés : {0}", whitelistUUIDs.size());
    }

    /**
     * Vérifie si un joueur est autorisé (OP ou whitelist)
     * @param player Le joueur à vérifier
     * @return true si autorisé, false sinon
     */
    private boolean estAutorise(Player player) {
        if (!isSystemEnabled()) {
            return true;
        }
        return player.isOp() || whitelistUUIDs.contains(player.getUniqueId());
    }

    /**
     * Récupère un message depuis la config avec fallback
     * @param key Clé du message
     * @param fallback Message par défaut
     * @return Le message formaté
     */
    private String getMessage(String key, String fallback) {
        String prefix = plugin.getConfig().getString("messages.prefix", "§7[Aparaire] ");
        String message = plugin.getConfig().getString(key, fallback);
        return prefix + message;
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : BLOCS
    // ═════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protections.block.break", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protections.block.place", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.blocks", true)) return;

        Player player = event.getPlayer();
        if (estAutorise(player)) return;

        // Vérifie uniquement les interactions sur bloc
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK &&
            event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        if (event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();

            // Vérifie si le bloc est dans la liste des exceptions
            if (isAllowedBlock(type)) {
                return;
            }

            // Bloque tous les autres blocs interactifs
            if (isInteractiveBlock(type)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Vérifie si un bloc est autorisé (plaques de pression, etc.)
     */
    private boolean isAllowedBlock(Material type) {
        // Plaques de pression (si configuré)
        if (!plugin.getConfig().getBoolean("protections.interact.pressure_plates", false) &&
            (type.name().endsWith("_PRESSURE_PLATE") || type.name().equals("STONE_PRESSURE_PLATE"))) {
            return true;
        }
        return false;
    }

    /**
     * Vérifie si un bloc est interactif
     */
    private boolean isInteractiveBlock(Material type) {
        return type.name().contains("BUTTON") ||
               type.name().contains("LEVER") ||
               type.name().contains("DOOR") ||
               type.name().contains("TRAPDOOR") ||
               type.name().contains("GATE") ||
               type == Material.REPEATER ||
               type == Material.COMPARATOR ||
               type == Material.REDSTONE_WIRE ||
               type == Material.TRIPWIRE ||
               type == Material.TRIPWIRE_HOOK ||
               type == Material.NOTE_BLOCK ||
               type == Material.DRAGON_EGG ||
               type == Material.CAKE ||
               type == Material.FLOWER_POT ||
               type == Material.RESPAWN_ANCHOR ||
               type == Material.TNT ||
               type.name().endsWith("_SIGN") ||
               type.name().contains("SIGN");
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : SEAUX
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getConfig().getBoolean("protections.buckets.empty", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.getConfig().getBoolean("protections.buckets.fill", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : COMBAT & DÉGÂTS
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protections.damage.outgoing", true)) return;

        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                damager = shooter;
            }
        }

        if (damager instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("protections.damage.incoming", true)) return;
        if (event.getEntity() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!plugin.getConfig().getBoolean("protections.projectiles.launch", true)) return;
        if (event.getEntity().getShooter() instanceof Player shooter && !estAutorise(shooter)) {
            event.setCancelled(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : FAIM
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!plugin.getConfig().getBoolean("protections.hunger.freeze", true)) return;
        if (event.getEntity() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : INVENTAIRES & CONTENEURS
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (estAutorise(player)) return;

        InventoryType type = event.getInventory().getType();

        // Conteneurs de stockage
        if (plugin.getConfig().getBoolean("protections.inventory.containers", true) &&
            (type == InventoryType.CHEST || type == InventoryType.BARREL ||
             type == InventoryType.DISPENSER || type == InventoryType.DROPPER ||
             type == InventoryType.HOPPER || type == InventoryType.SHULKER_BOX ||
             type == InventoryType.ENDER_CHEST)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("messages.container-denied",
                "§cTu n'es pas autorisé à accéder à ce conteneur."));
        }

        // Postes de travail
        if (plugin.getConfig().getBoolean("protections.workstations.block", true) &&
            (type == InventoryType.WORKBENCH || type == InventoryType.ANVIL ||
             type == InventoryType.ENCHANTING || type == InventoryType.BREWING ||
             type == InventoryType.BEACON || type == InventoryType.GRINDSTONE ||
             type == InventoryType.STONECUTTER || type == InventoryType.CARTOGRAPHY ||
             type == InventoryType.LOOM || type == InventoryType.SMITHING ||
             type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE ||
             type == InventoryType.SMOKER)) {
            event.setCancelled(true);
            player.sendMessage(getMessage("messages.workstation-denied",
                "§cTu n'es pas autorisé à utiliser ce poste de travail."));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("protections.crafting.clicks", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!estAutorise(player) && event.getInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!plugin.getConfig().getBoolean("protections.crafting.clicks", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!estAutorise(player) && event.getInventory().getType() != InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : ITEMS
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("protections.items.drop", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("protections.items.pickup", true)) return;
        if (event.getEntity() instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : ENTITÉS & HANGING
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.entities", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getMessage("messages.entity-denied",
                "§cTu n'es pas autorisé à interagir avec cette entité."));
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.entities", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ═══ NOUVEAU : Protection des peintures et item frames ═══
    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.entities", true)) return;

        Entity remover = event.getRemover();
        if (remover instanceof Player player && !estAutorise(player)) {
            event.setCancelled(true);
        }
        else if (remover instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter && !estAutorise(shooter)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.entities", true)) return;
        if (event.getCause() != HangingBreakEvent.RemoveCause.ENTITY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protections.block.place", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    // ═══ NOUVEAU : Protection des panneaux ═══
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("protections.interact.blocks", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);

            // Réinitialise le texte original
            if (event.getBlock().getState() instanceof org.bukkit.block.Sign sign) {
                for (int i = 0; i < 4; i++) {
                    event.setLine(i, sign.getLine(i));
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : VÉHICULES
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!plugin.getConfig().getBoolean("protections.vehicles.use", true)) return;
        if (!(event.getEntered() instanceof Player player)) return;

        // Vérifie si le véhicule est autorisé
        if (isAllowedVehicle(event.getVehicle().getType())) {
            return;
        }

        if (!estAutorise(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Vérifie si un véhicule est autorisé (bateaux, minecarts)
     */
    private boolean isAllowedVehicle(EntityType vehicleType) {
        // Bateaux et minecarts toujours autorisés si configuré
        if (!plugin.getConfig().getBoolean("protections.vehicles.restrict_boats", false) &&
            (vehicleType == EntityType.BOAT || vehicleType == EntityType.BOAT)) {
            return true;
        }

        if (!plugin.getConfig().getBoolean("protections.vehicles.restrict_minecarts", false) &&
            (vehicleType == EntityType.MINECART ||
             vehicleType == EntityType.MINECART_CHEST ||
             vehicleType == EntityType.MINECART_FURNACE ||
             vehicleType == EntityType.MINECART_HOPPER ||
             vehicleType == EntityType.MINECART_TNT)) {
            return true;
        }

        return false;
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : LIT & SOMMEIL
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!plugin.getConfig().getBoolean("protections.bed.sleep", true)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getMessage("messages.bed-denied",
                "§cTu n'es pas autorisé à dormir."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : TÉLÉPORTATION
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (estAutorise(player)) return;

        switch (event.getCause()) {
            case ENDER_PEARL:
                if (plugin.getConfig().getBoolean("protections.teleport.ender_pearl", true)) {
                    event.setCancelled(true);
                }
                break;
            case CHORUS_FRUIT:
                if (plugin.getConfig().getBoolean("protections.teleport.chorus", false)) {
                    event.setCancelled(true);
                }
                break;
            case NETHER_PORTAL:
            case END_PORTAL:
            case END_GATEWAY:
                if (plugin.getConfig().getBoolean("protections.portals.use", true)) {
                    event.setCancelled(true);
                    player.sendMessage(getMessage("messages.portal-denied",
                        "§cTu n'es pas autorisé à utiliser les portails."));
                }
                break;
            default:
                break;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PROTECTIONS : CHAT & COMMANDES
    // ════════════════════════════════════════════════════════════════════

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.chat.enabled", false)) return;
        if (!estAutorise(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getMessage("messages.chat-denied",
                "§cTu n'es pas autorisé à écrire dans le chat."));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // RELOAD WHITELIST AUTO
    // ═════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String commandLine = event.getMessage().substring(1);
        String command = commandLine.split(" ")[0].toLowerCase();

        // Détection améliorée des commandes whitelist
        if (WHITELIST_COMMAND_PATTERN.matcher(commandLine).find() &&
            plugin.getConfig().getBoolean("whitelist.auto-reload-on-command", true)) {

            plugin.getLogger().info("whitelist cmd détectée (joueur): " + commandLine);

            // Délai augmenté à 5 ticks
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                reloadWhitelist();
                if (event.getPlayer().isOp()) {
                    event.getPlayer().sendMessage("§7[Aparaire] Whitelist rechargée automatiquement.");
                }
            }, 5L);
        }

        // Filtrage des commandes pour les visiteurs
        if (!plugin.getConfig().getBoolean("restrictions.commands.enabled", false)) return;
        if (estAutorise(event.getPlayer())) return;

        List<String> allowedCommands = plugin.getConfig().getStringList("restrictions.commands.allowed");
        boolean isAllowed = allowedCommands.stream()
            .anyMatch(cmd -> cmd.equalsIgnoreCase(command));

        if (!isAllowed) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getMessage("messages.command-denied",
                "§cTu n'es pas autorisé à utiliser cette commande."));
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("whitelist.auto-reload-on-command", true)) return;

        String commandLine = event.getCommand();
        if (WHITELIST_COMMAND_PATTERN.matcher(commandLine).find()) {
            plugin.getLogger().info("whitelist cmd détectée (console): " + commandLine);

            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                reloadWhitelist();
                plugin.getLogger().info("Whitelist rechargée automatiquement (commande console détectée).");
            }, 5L);
        }
    }

    @EventHandler
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("whitelist.auto-reload-on-command", true)) return;

        String commandLine = event.getCommand();
        if (WHITELIST_COMMAND_PATTERN.matcher(commandLine).find()) {
            plugin.getLogger().info("whitelist cmd détectée (RCON): " + commandLine);

            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                reloadWhitelist();
                plugin.getLogger().info("Whitelist rechargée automatiquement (commande RCON détectée).");
            }, 5L);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONNEXION : MODE CONFORT
    // ════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Reload auto à chaque join
        if (plugin.getConfig().getBoolean("whitelist.auto-reload-on-join", true)) {
            reloadWhitelist();
        }

        // Gestion des items
        if (estAutorise(player)) {
            retirerItemsDepart(player);
        } else {
            donnerItemsRestreints(player);
        }
    }

    private void retirerItemsDepart(Player player) {
        if (!plugin.getConfig().getBoolean("mode-confort.enabled", true)) return;
        if (plugin.getConfig().getBoolean("mode-confort.keep-starter-items", true)) return;

        if (plugin.getConfig().getBoolean("mode-confort.spyglass", true)) {
            player.getInventory().remove(new ItemStack(Material.SPYGLASS, 1));
        }

        if (plugin.getConfig().getBoolean("mode-confort.chorus", true)) {
            int chorusAmount = plugin.getConfig().getInt("mode-confort.chorus-amount", 10);
            ItemStack[] contents = player.getInventory().getContents();
            int remaining = chorusAmount;

            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == Material.CHORUS_FRUIT) {
                    int toRemove = Math.min(item.getAmount(), remaining);
                    item.setAmount(item.getAmount() - toRemove);
                    remaining -= toRemove;
                    if (item.getAmount() <= 0) contents[i] = null;
                }
            }
            player.getInventory().setContents(contents);
        }

        player.sendMessage(getMessage("messages.items-removed",
            "§aItems de départ retirés (tu es maintenant autorisé)."));
    }

    private void donnerItemsRestreints(Player player) {
        if (!plugin.getConfig().getBoolean("mode-confort.enabled", true)) return;

        if (plugin.getConfig().getBoolean("mode-confort.spyglass", true) &&
            !player.getInventory().contains(Material.SPYGLASS)) {
            player.getInventory().addItem(new ItemStack(Material.SPYGLASS, 1));
        }

        if (plugin.getConfig().getBoolean("mode-confort.chorus", true) &&
            !player.getInventory().contains(Material.CHORUS_FRUIT)) {
            int chorusAmount = plugin.getConfig().getInt("mode-confort.chorus-amount", 10);
            player.getInventory().addItem(new ItemStack(Material.CHORUS_FRUIT, chorusAmount));
        }
    }

    private boolean isSystemEnabled() {
        return plugin.getConfig().getBoolean("whitelist.enabled", true);
    }

}
