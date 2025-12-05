package siwa.valorium.EmpBloqueur;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Commande pour interroger les logs d'EasyPlace.
 *
 * Usage :
 *   /empsuspects               -> joueurs suspects depuis toujours
 *   /empsuspects <jours>       -> joueurs suspects sur les X derniers jours
 *
 * Pour chaque joueur :
 *   - nombre de sessions loguées
 *   - date de dernière suspicion
 */
public class EmpEasyPlaceLogQueryCommand implements CommandExecutor, TabExecutor {

    private final JavaPlugin plugin;
    private final String logRelativePath;

    // Perm à vérifier, typiquement "empbloqueur.query"
    private final String permission;

    public EmpEasyPlaceLogQueryCommand(JavaPlugin plugin, String logRelativePath, String permission) {
        this.plugin = plugin;
        this.logRelativePath = logRelativePath;
        this.permission = permission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage("§cTu n'as pas la permission d'utiliser cette commande.");
            return true;
        }

        // Parsing de l'argument [jours]
        Long minTimestamp = null;
        int days = -1; // -1 = depuis toujours

        if (args.length >= 1) {
            try {
                days = Integer.parseInt(args[0]);
                if (days <= 0) {
                    sender.sendMessage("§eNombre de jours invalide. Utilisation : §f/" + label + " [jours]");
                    return true;
                }
                long now = System.currentTimeMillis();
                long delta = days * 24L * 60L * 60L * 1000L;
                minTimestamp = now - delta;
            } catch (NumberFormatException ex) {
                sender.sendMessage("§eArgument invalide. Utilisation : §f/" + label + " [jours]");
                return true;
            }
        }

        File dataFolder = plugin.getDataFolder();
        File logFile = new File(dataFolder, logRelativePath);

        if (!logFile.exists()) {
            sender.sendMessage("§eAucun fichier de logs EasyPlace trouvé pour le moment.");
            return true;
        }

        // Agrégation par joueur
        Map<UUID, PlayerStats> statsByPlayer = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                EmpEasyPlaceLogEntry entry = EmpEasyPlaceLogEntry.parseFromLogLine(line);
                if (entry == null) {
                    continue;
                }

                long endTime = entry.getEndTimeMillis();

                // Filtre temporel si minTimestamp défini
                if (minTimestamp != null && endTime < minTimestamp) {
                    continue;
                }

                UUID uuid = entry.getPlayerUuid();
                PlayerStats stats = statsByPlayer.get(uuid);
                if (stats == null) {
                    stats = new PlayerStats(entry.getPlayerUuid(), entry.getPlayerName());
                    statsByPlayer.put(uuid, stats);
                }

                stats.sessionCount++;
                if (endTime > stats.lastEndTimeMillis) {
                    stats.lastEndTimeMillis = endTime;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[EmpBloqueur] Erreur lors de la lecture du fichier de logs EasyPlace", e);
            sender.sendMessage("§cUne erreur est survenue lors de la lecture des logs. Voir la console.");
            return true;
        }

        if (statsByPlayer.isEmpty()) {
            if (days > 0) {
                sender.sendMessage("§aAucun joueur suspect sur les §e" + days + " §ajours passés.");
            } else {
                sender.sendMessage("§aAucun joueur suspect enregistré dans les logs.");
            }
            return true;
        }

        // Petit formateur de date lisible
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRENCH);
        // Tu peux mettre ici le fuseau que tu veux
        sdf.setTimeZone(TimeZone.getDefault());

        // Tri des joueurs par nombre de sessions décroissant
        var sortedStats = statsByPlayer.values().stream()
            .sorted(Comparator.comparingInt(PlayerStats::getSessionCount).reversed())
            .collect(Collectors.toList());

        if (days > 0) {
            sender.sendMessage("§6[EmpBloqueur] Joueurs suspects sur les §e" + days + " §6derniers jours :");
        } else {
            sender.sendMessage("§6[EmpBloqueur] Joueurs suspects (période : §etoujours§6) :");
        }

        for (PlayerStats stats : sortedStats) {
            String formattedDate = sdf.format(new Date(stats.lastEndTimeMillis));
            sender.sendMessage("§7- §e" + stats.playerName + "§7 : §c" + stats.sessionCount
                + " §7session(s), dernière suspicion le §f" + formattedDate);
        }

        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Auto-complétion simple : proposer quelques durées "classiques"
        if (args.length == 1) {
            return java.util.Arrays.asList("1", "3", "7", "14", "30");
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Stats agrégées pour un joueur sur la période demandée.
     */
    private static class PlayerStats {
        final UUID playerUuid;
        final String playerName;
        int sessionCount;
        long lastEndTimeMillis;

        PlayerStats(UUID playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.sessionCount = 0;
            this.lastEndTimeMillis = 0L;
        }

        int getSessionCount() {
            return sessionCount;
        }
    }
}
