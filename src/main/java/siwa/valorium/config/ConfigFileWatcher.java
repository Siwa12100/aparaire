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
 * Surveille les fichiers de config (config.yml et éventuellement empBloqueur.yml)
 * et déclenche un reload automatique en cas de modification externe.
 */
public class ConfigFileWatcher implements Runnable {

    private final JavaPlugin plugin;
    @SuppressWarnings("NonConstantLogger")
    private final Logger logger;
    private final ConfigManager configManager;
    private final Path watchedDirectory;

    private final String mainConfigName = "config.yml";
    private final String empConfigName = "empBloqueur.yml";

    /**
     * Callback optionnelle appelée quand empBloqueur.yml est modifié.
     * Typiquement : () -> empDetector.reloadConfig()
     */
    private final Runnable empConfigReloadCallback;

    private WatchService watchService;
    private volatile boolean running = true;

    // Debounce : temps d'attente après détection (évite reloads multiples)
    private static final long DEBOUNCE_MILLIS = 500;
    private long lastModificationTimeMain = 0;
    private long lastModificationTimeEmp = 0;

    public ConfigFileWatcher(JavaPlugin plugin,
                             ConfigManager configManager,
                             Runnable empConfigReloadCallback) {

        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = configManager;
        this.empConfigReloadCallback = empConfigReloadCallback;
        this.watchedDirectory = plugin.getDataFolder().toPath();

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watchedDirectory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            logger.info("Surveillance des fichiers de config activée (config.yml + empBloqueur.yml).");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Impossible de démarrer la surveillance des fichiers de config : {0}", e.getMessage());
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Attend un événement (timeout 1 seconde pour pouvoir arrêter proprement)
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);

                if (key == null) continue;

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    String name = filename.toString();

                    long now = System.currentTimeMillis();

                    // ───────────── config.yml ─────────────
                    if (name.equals(mainConfigName)) {
                        if (now - lastModificationTimeMain < DEBOUNCE_MILLIS) continue;
                        lastModificationTimeMain = now;

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            configManager.tryReload();
                        });
                        continue;
                    }

                    // ───────────── empBloqueur.yml ─────────────
                    if (name.equals(empConfigName) && empConfigReloadCallback != null) {
                        if (now - lastModificationTimeEmp < DEBOUNCE_MILLIS) continue;
                        lastModificationTimeEmp = now;

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            empConfigReloadCallback.run();
                        });
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    logger.warning("La surveillance des fichiers de config a été interrompue.");
                    break;
                }

            } catch (InterruptedException e) {
                logger.info("Surveillance des fichiers de config arrêtée (interruption).");
                break;
            } catch (IllegalArgumentException e) {
                logger.log(Level.SEVERE, "Erreur dans la surveillance des fichiers de config : {0}", e.getMessage());
            }
        }

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
