package siwa.valorium;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import siwa.valorium.EmpBloqueur.EmpEasyPlaceDetector;
import siwa.valorium.EmpBloqueur.EmpEasyPlaceFileLogger;
import siwa.valorium.EmpBloqueur.EmpEasyPlaceLogEntry;
import siwa.valorium.EmpBloqueur.EmpEasyPlaceLogQueryCommand;
import siwa.valorium.config.ConfigFileWatcher;
import siwa.valorium.config.ConfigManager;
import siwa.valorium.whitelist.WhitelistManager;

public final class Aparaire extends JavaPlugin {

    private WhitelistManager whitelistManager;
    private EmpEasyPlaceDetector empDetector;
    private EmpEasyPlaceFileLogger fileLogger;
    private EmpEasyPlaceLogQueryCommand logQueryCommand;
    private ConfigManager configManager;
    private ConfigFileWatcher configWatcher;
    private Thread configWatcherThread;

    @Override
    public void onEnable() {
        getLogger().info("Aparaire plugin lancé !");

        saveDefaultConfig();

        // Whitelist
        this.whitelistManager = new WhitelistManager(this);

        // Config manager (pour config.yml)
        this.configManager = new ConfigManager(whitelistManager, this);

        // EmpBloqueur
        String logRelativePath = "logs/easyplace.log";
        this.fileLogger = new EmpEasyPlaceFileLogger(this);
        this.empDetector = new EmpEasyPlaceDetector(this, this.fileLogger, logRelativePath);

        Bukkit.getPluginManager().registerEvents(this.whitelistManager, this);
        Bukkit.getPluginManager().registerEvents(this.empDetector, this);

        // Commande /empsuspects
        String queryPermission = "empbloqueur.query";
        this.logQueryCommand =
            new EmpEasyPlaceLogQueryCommand(this, logRelativePath, queryPermission);
        if (getCommand("empsuspects") != null) {
            getCommand("empsuspects").setExecutor(this.logQueryCommand);
            getCommand("empsuspects").setTabCompleter(this.logQueryCommand);
        }

        // ─────────────────────────────────────────────
        // Watcher des fichiers de config
        // ─────────────────────────────────────────────
        this.configWatcher = new ConfigFileWatcher(
            this,
            this.configManager,
            () -> {
                // callback pour empBloqueur.yml
                if (empDetector != null) {
                    empDetector.reloadConfig();
                    getLogger().info("empBloqueur.yml rechargé automatiquement (modification détectée).");
                }
            }
        );

        this.configWatcherThread = new Thread(this.configWatcher, "ConfigFileWatcher");
        this.configWatcherThread.start();
    }

    @Override
    public void onDisable() {
        getLogger().info("Aparaire plugin arrêté !");

        // Stop watcher de config
        if (configWatcher != null) {
            configWatcher.stop();
        }
        if (configWatcherThread != null && configWatcherThread.isAlive()) {
            configWatcherThread.interrupt();
        }

        // Arrêt propre du ConfigManager (tâches périodiques)
        if (configManager != null) {
            configManager.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // ---------------------------------------------
        // /aparairereload
        // ---------------------------------------------
        if (command.getName().equalsIgnoreCase("aparairereload")) {

            if (sender instanceof Player p && (!p.isOp() && !p.hasPermission("aparairereload.use"))) {
                sender.sendMessage("§7[Aparaire] §cTu n'as pas la permission.");
                return true;
            }

            // Reload config principale (whitelist, mode confort, etc.)
            reloadConfig();
            whitelistManager.reloadWhitelist();

            // Reload config dédiée EmpBloqueur
            if (empDetector != null) {
                empDetector.reloadConfig();
            }

            sender.sendMessage("§7[Aparaire] §aConfiguration rechargée (config principale + EmpBloqueur).");
            return true;
        }

        // ---------------------------------------------
        // /empbloqueur ...
        // ---------------------------------------------
        if (command.getName().equalsIgnoreCase("empbloqueur")) {

            if (!sender.hasPermission("empbloqueur.admin") && !(sender instanceof Player p && p.isOp())) {
                sender.sendMessage("§cTu n'as pas la permission d'utiliser cette commande.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("§e/empbloqueur reload §7- Recharge la config EmpBloqueur");
                sender.sendMessage("§e/empbloqueur last <joueur> §7- Affiche la dernière session suspecte pour ce joueur");
                sender.sendMessage("§e/empbloqueur recent [n] §7- Affiche les n dernières sessions (par défaut 5)");
                return true;
            }

            String sub = args[0].toLowerCase();

            // /empbloqueur reload
            if (sub.equals("reload")) {
                if (empDetector != null) {
                    empDetector.reloadConfig();
                    sender.sendMessage("§7[EmpBloqueur] §aConfiguration rechargée.");
                } else {
                    sender.sendMessage("§7[EmpBloqueur] §cLe détecteur n'est pas initialisé.");
                }
                return true;
            }

            // /empbloqueur last <joueur>
            if (sub.equals("last")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /empbloqueur last <joueur>");
                    return true;
                }

                String playerName = args[1];
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(playerName);
                if (offline == null) {
                    // On tente quand même
                    offline = Bukkit.getOfflinePlayer(playerName);
                }

                if (offline == null || offline.getUniqueId() == null) {
                    sender.sendMessage("§cJoueur introuvable: " + playerName);
                    return true;
                }

                EmpEasyPlaceLogEntry last = empDetector != null
                        ? empDetector.getLastEntryForPlayer(offline.getUniqueId())
                        : null;

                if (last == null) {
                    sender.sendMessage("§7[EmpBloqueur] §eAucune session suspecte enregistrée pour §6" + playerName + "§e.");
                    // Optionnel : snapshot de session en cours
                    EmpEasyPlaceLogEntry current = empDetector != null
                            ? empDetector.getCurrentSessionSnapshot(offline.getUniqueId())
                            : null;
                    if (current != null) {
                        sender.sendMessage("§7[EmpBloqueur] §eUne session en cours existe (non encore clôturée) :");
                        sender.sendMessage("§7" + current.toSummaryString());
                    }
                    return true;
                }

                sender.sendMessage("§7[EmpBloqueur] §eDernière session suspecte pour §6" + last.getPlayerName() + "§e :");
                sender.sendMessage("§7" + last.toSummaryString());
                return true;
            }

            // /empbloqueur recent [n]
            if (sub.equals("recent")) {
                int limit = 5;
                if (args.length >= 2) {
                    try {
                        limit = Integer.parseInt(args[1]);
                        if (limit <= 0) {
                            limit = 5;
                        }
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("§cNombre invalide, utilisation de la valeur par défaut: 5");
                        limit = 5;
                    }
                }

                if (empDetector == null) {
                    sender.sendMessage("§7[EmpBloqueur] §cLe détecteur n'est pas initialisé.");
                    return true;
                }

                var list = empDetector.getRecentEntries(limit);
                if (list.isEmpty()) {
                    sender.sendMessage("§7[EmpBloqueur] §eAucune session suspecte enregistrée pour le moment.");
                    return true;
                }

                sender.sendMessage("§7[EmpBloqueur] §eDernières sessions suspectes (max " + limit + ") :");
                for (EmpEasyPlaceLogEntry entry : list) {
                    sender.sendMessage("§7- " + entry.toSummaryString());
                }
                return true;
            }

            // Sous-commande inconnue
            sender.sendMessage("§cSous-commande inconnue. Utilise /empbloqueur pour l'aide.");
            return true;
        }

        return false;
    }
}
