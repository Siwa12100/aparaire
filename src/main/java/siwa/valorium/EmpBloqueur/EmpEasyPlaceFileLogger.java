package siwa.valorium.EmpBloqueur;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utilitaire responsable d'écrire les sessions EasyPlace dans un fichier de log.
 *
 * - Résout le chemin à partir du dataFolder du plugin.
 * - Crée les dossiers parents si besoin.
 * - Écrit chaque session sur une ligne (entry.toFileString()).
 * - Gère les erreurs d'IO proprement (log en console).
 */
public class EmpEasyPlaceFileLogger {

    private final JavaPlugin plugin;

    public EmpEasyPlaceFileLogger(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Écrit une entrée de log dans le fichier spécifié (chemin relatif
     * par rapport au dataFolder du plugin).
     *
     * @param entry          Session EasyPlace à logger
     * @param relativePath   Chemin relatif du fichier (ex: "logs/emp-easyplace.log")
     */
    public void logToFile(EmpEasyPlaceLogEntry entry, String relativePath) {
        if (entry == null || relativePath == null || relativePath.isEmpty()) {
            return;
        }

        try {
            // Dossier racine du plugin (plugins/Aparaire par exemple)
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // Fichier de log (chemin relatif)
            File logFile = new File(dataFolder, relativePath);

            // Création des dossiers parents si nécessaire
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Écriture en append (une ligne par session)
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(entry.toFileString());
                writer.newLine();
            }

        } catch (IOException e) {
            plugin.getLogger().log(
                Level.SEVERE,
                "[EmpBloqueur] Impossible d'écrire dans le fichier de logs EasyPlace (" + relativePath + ")",
                e
            );
        }
    }
}
