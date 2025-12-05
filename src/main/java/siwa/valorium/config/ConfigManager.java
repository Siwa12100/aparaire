package siwa.valorium.config;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import siwa.valorium.whitelist.WhitelistManager;

/**
 * Gestionnaire centralisé de la configuration avec système de snapshot
 */
public class ConfigManager {

    private final WhitelistManager whitelistManager;
    private final JavaPlugin plugin;
    @SuppressWarnings("NonConstantLogger")
    private final Logger logger;
    private final ConfigValidator validator;
    private final File configFile;

    // Snapshot de la dernière configuration valide
    private YamlConfiguration currentConfig;
    private YamlConfiguration snapshotConfig;

    // Tâche périodique de reload whitelist
    private BukkitTask reloadTask;

    public ConfigManager(WhitelistManager whitelistManager, JavaPlugin plugin) {
        this.whitelistManager = whitelistManager;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.validator = new ConfigValidator(logger);
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadInitialConfig();
    }

    /**
     * Charge la configuration initiale au démarrage du plugin
     */
    private void loadInitialConfig() {
        plugin.saveDefaultConfig();
        currentConfig = YamlConfiguration.loadConfiguration(configFile);
        snapshotConfig = YamlConfiguration.loadConfiguration(configFile);
        
        logger.info("Configuration initiale chargée.");
    }

    /**
     * Tente de recharger la configuration depuis le fichier
     * @return true si le reload a réussi, false sinon (rollback)
     */
    public synchronized boolean tryReload() {
        logger.info("Détection de modification du fichier config.yml...");

        // Charge le nouveau fichier
        YamlConfiguration newConfig;
        try {
            newConfig = YamlConfiguration.loadConfiguration(configFile);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors du parsing YAML : {0}", e.getMessage());
            logger.warning("Configuration précédente conservée (rollback).");
            return false;
        }

        // Valide la nouvelle config
        List<String> errors = validator.validate(newConfig);
        
        if (!errors.isEmpty()) {
            validator.logErrors(errors);
            return false; // Rollback implicite (on garde currentConfig)
        }

        // Sauvegarde l'ancienne config comme snapshot
        snapshotConfig = currentConfig;

        // Applique la nouvelle config
        currentConfig = newConfig;
        plugin.reloadConfig(); // Sync avec Bukkit (optionnel selon architecture)

        logger.info("✅ Configuration rechargée avec succès !");

        // Gestion des changements stateful (tâche périodique)
        handleStatefulChanges();

        return true;
    }

    /**
     * Gère les modifications qui nécessitent une action (ex: replannifier une tâche)
     */
    private void handleStatefulChanges() {
        // Annule l'ancienne tâche périodique si elle existe
        if (reloadTask != null && !reloadTask.isCancelled()) {
            reloadTask.cancel();
            reloadTask = null;
        }

        // Replanifie si la période a changé et est > 0
        int newPeriod = currentConfig.getInt("whitelist.auto-reload-period-seconds", 0);
        
        if (newPeriod > 0) {
            reloadTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                // Appelle la méthode reloadWhitelist du plugin principal
                if (plugin instanceof siwa.valorium.Aparaire aparaire) {
                    whitelistManager.reloadWhitelist();
                    logger.info("Whitelist rechargée automatiquement (tâche périodique).");
                }
            }, 20L * newPeriod, 20L * newPeriod);

            logger.log(Level.INFO, "T\u00e2che p\u00e9riodique de reload whitelist programm\u00e9e ({0}s).", newPeriod);
        }
    }

    /**
     * Récupère la configuration actuelle (thread-safe)
     */
    public synchronized YamlConfiguration getConfig() {
        return currentConfig;
    }

    /**
     * Arrête les tâches en cours (à appeler dans onDisable)
     */
    public void shutdown() {
        if (reloadTask != null && !reloadTask.isCancelled()) {
            reloadTask.cancel();
        }
    }

    /**
     * Rollback manuel vers le snapshot précédent
     */
    public synchronized void rollback() {
        if (snapshotConfig != null) {
            currentConfig = snapshotConfig;
            logger.info("Rollback effectué vers la configuration précédente.");
        } else {
            logger.warning("Aucun snapshot disponible pour le rollback.");
        }
    }
}
