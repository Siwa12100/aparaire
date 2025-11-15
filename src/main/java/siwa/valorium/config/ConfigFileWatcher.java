package siwa.valorium.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Surveille le fichier config.yml et déclenche un reload automatique
 * en cas de modification externe
 */
public class ConfigFileWatcher implements Runnable {

    private final JavaPlugin plugin;
    @SuppressWarnings("NonConstantLogger")
    private final Logger logger;
    private final ConfigManager configManager;
    @SuppressWarnings("unused")
    private final File configFile;
    private final Path watchedDirectory;

    private WatchService watchService;
    private volatile boolean running = true;

    // Debounce : temps d'attente après détection (évite reloads multiples)
    private static final long DEBOUNCE_MILLIS = 500;
    private long lastModificationTime = 0;

    public ConfigFileWatcher(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.watchedDirectory = plugin.getDataFolder().toPath();

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watchedDirectory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            logger.info("Surveillance du fichier config.yml activée.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Impossible de d\u00e9marrer la surveillance du fichier config.yml : {0}", e.getMessage());
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Attend un événement (timeout 1 seconde pour pouvoir arrêter proprement)
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                
                if (key == null) continue;

                // Parcourt les événements
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    // Ignore les overflows
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    // Récupère le fichier modifié
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    // Vérifie si c'est bien config.yml
                    if (!filename.toString().equals("config.yml")) continue;

                    // ═══ DEBOUNCE ═══
                    long now = System.currentTimeMillis();
                    if (now - lastModificationTime < DEBOUNCE_MILLIS) {
                        continue; // Ignore les modifications trop rapprochées
                    }
                    lastModificationTime = now;

                    // ═══ RELOAD SUR LE THREAD PRINCIPAL ═══
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        configManager.tryReload();
                    });
                }

                // Reset la clé pour continuer à recevoir des événements
                boolean valid = key.reset();
                if (!valid) {
                    logger.warning("La surveillance du fichier config.yml a été interrompue.");
                    break;
                }

            } catch (InterruptedException e) {
                logger.info("Surveillance du fichier config.yml arrêtée.");
                break;
            } catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Erreur dans la surveillance du fichier : {0}", e.getMessage());
            }
        }

        // Fermeture propre
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Erreur lors de la fermeture du WatchService : {0}", e.getMessage());
        }
    }

    /**
     * Arrête la surveillance (à appeler dans onDisable)
     */
    public void stop() {
        running = false;
    }
}
