package siwa.valorium.EmpBloqueur;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class EmpEasyPlaceDetector implements Listener {

    private final JavaPlugin plugin;

    // Configuration dédiée (empBloqueur.yml)
    private File empConfigFile;
    private FileConfiguration empConfig;

    // Champs de config mis en cache
    private boolean enabled;
    private boolean onlySurvival;
    private boolean ignoreCreative;
    private boolean ignoreSpectator;

    private boolean checkAirSupport;
    private double maxDistanceBlocks;
    private double maxBlocksPerSecond;
    private double angleMaxDegrees;

    private int suspicionPointsDistance;
    private int suspicionPointsSupportAir;
    private int suspicionPointsAngle;
    private int suspicionPointsRate;

    private int suspicionThresholdSession;
    private int suspicionThresholdCancel;

    private boolean cancelPlacement;
    private boolean notifyStaff;
    private String notifyStaffPermission;
    private String bypassPermission;

    private boolean loggingEnabled;
    private boolean loggingConsole;
    private boolean loggingFileEnabled;
    private int minBlocksPerSession;
    private long inactivityMillisToCloseSession;

    private int historyMaxEntries;

    private String loggingFilePath;

    // Sessions en cours par joueur
    private final Map<UUID, EasyPlaceSession> sessions = new HashMap<>();

    // Historique des sessions clôturées (pour les commandes)
    private final Deque<EmpEasyPlaceLogEntry> history = new ArrayDeque<>();

    private final EmpEasyPlaceFileLogger fileLogger;

    public EmpEasyPlaceDetector(JavaPlugin plugin, EmpEasyPlaceFileLogger fileLogger, String loggingFilePath) {
        this.plugin = plugin;
        this.fileLogger = fileLogger;
        this.loggingFilePath = loggingFilePath;
        reloadConfig(); // charge empBloqueur.yml + met en cache les valeurs
    }

    /**
     * À appeler depuis ta commande /aparairereload ou équivalent.
     */
    public void reloadConfig() {
        // Chargement / création du fichier empBloqueur.yml
        if (empConfigFile == null) {
            empConfigFile = new File(plugin.getDataFolder(), "empBloqueur.yml");
        }

        if (!empConfigFile.exists()) {
            // Nécessite que empBloqueur.yml soit présent dans les resources du plugin.
            plugin.saveResource("empBloqueur.yml", false);
        }

        empConfig = YamlConfiguration.loadConfiguration(empConfigFile);

        // Lecture des valeurs de config
        enabled = empConfig.getBoolean("empBloqueur.enabled", true);
        onlySurvival = empConfig.getBoolean("empBloqueur.only-survival", true);
        ignoreCreative = empConfig.getBoolean("empBloqueur.ignore-creative", true);
        ignoreSpectator = empConfig.getBoolean("empBloqueur.ignore-spectator", true);

        checkAirSupport = empConfig.getBoolean("empBloqueur.detection.check-air-support", true);
        maxDistanceBlocks = empConfig.getDouble("empBloqueur.detection.max-distance-blocks", 5.2D);
        maxBlocksPerSecond = empConfig.getDouble("empBloqueur.detection.max-blocks-per-second", 15.0D);
        angleMaxDegrees = empConfig.getDouble("empBloqueur.detection.angle-max-degrees", 35.0D);

        suspicionPointsDistance = empConfig.getInt("empBloqueur.detection.suspicion-points.distance", 3);
        suspicionPointsSupportAir = empConfig.getInt("empBloqueur.detection.suspicion-points.support-air", 5);
        suspicionPointsAngle = empConfig.getInt("empBloqueur.detection.suspicion-points.angle", 4);
        suspicionPointsRate = empConfig.getInt("empBloqueur.detection.suspicion-points.rate", 3);

        suspicionThresholdSession = empConfig.getInt("empBloqueur.detection.suspicion-threshold-session", 15);
        suspicionThresholdCancel = empConfig.getInt("empBloqueur.detection.suspicion-threshold-cancel", 8);

        cancelPlacement = empConfig.getBoolean("empBloqueur.actions.cancel-placement", true);
        notifyStaff = empConfig.getBoolean("empBloqueur.actions.notify-staff", true);
        notifyStaffPermission = empConfig.getString("empBloqueur.actions.notify-staff-permission", "empbloqueur.notify");
        bypassPermission = empConfig.getString("empBloqueur.actions.bypass-permission", "empbloqueur.bypass");

        loggingEnabled = empConfig.getBoolean("empBloqueur.logging.enabled", true);
        loggingConsole = empConfig.getBoolean("empBloqueur.logging.console", true);
        loggingFileEnabled = empConfig.getBoolean("empBloqueur.logging.file.enabled", false);
        loggingFilePath = empConfig.getString("empBloqueur.logging.file.path", "logs/emp-easyplace.log");
        minBlocksPerSession = empConfig.getInt("empBloqueur.logging.min-blocks-per-session", 5);

        int inactivitySeconds = empConfig.getInt("empBloqueur.logging.inactivity-seconds-to-close-session", 5);
        inactivityMillisToCloseSession = inactivitySeconds * 1000L;

        historyMaxEntries = empConfig.getInt("empBloqueur.logging.history-max-entries", 100);

        plugin.getLogger().info("[EmpBloqueur] Configuration rechargée.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) {
            return;
        }

        Player player = event.getPlayer();

        // Bypass global
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        GameMode gm = player.getGameMode();
        if (onlySurvival && gm != GameMode.SURVIVAL) {
            return;
        }
        if (ignoreCreative && gm == GameMode.CREATIVE) {
            return;
        }
        if (ignoreSpectator && gm == GameMode.SPECTATOR) {
            return;
        }

        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();
        World world = placed.getWorld();

        Location blockCenter = placed.getLocation().add(0.5, 0.5, 0.5);
        Location eyeLoc = player.getEyeLocation();
        double distance = eyeLoc.distance(blockCenter);

        long now = System.currentTimeMillis();

        int suspicionScoreForThisBlock = 0;
        boolean ruleDistance = false;
        boolean ruleSupportAir = false;
        boolean ruleAngle = false;
        boolean ruleRate = false;

        // 1) Distance
        if (distance > maxDistanceBlocks) {
            ruleDistance = true;
            suspicionScoreForThisBlock += suspicionPointsDistance;
        }

        // 2) Support (blocAgainst)
        if (checkAirSupport) {
            if (against == null || against.isEmpty()) {
                ruleSupportAir = true;
                suspicionScoreForThisBlock += suspicionPointsSupportAir;
            } else {
                if (!isAdjacent(placed, against)) {
                    ruleSupportAir = true;
                    suspicionScoreForThisBlock += suspicionPointsSupportAir;
                }
            }
        }

        // 3) Angle & raytrace
        double angleDegrees = calculateAngleDegrees(eyeLoc, blockCenter);
        if (angleDegrees > angleMaxDegrees) {
            ruleAngle = true;
            suspicionScoreForThisBlock += suspicionPointsAngle;
        } else {
            RayTraceResult result = player.rayTraceBlocks(maxDistanceBlocks);
            if (result == null || result.getHitBlock() == null) {
                ruleAngle = true;
                suspicionScoreForThisBlock += suspicionPointsAngle;
            }
        }

        // Récupération / création de la session
        EasyPlaceSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new EasyPlaceSession(player.getUniqueId(), player.getName(), world.getName(), now, blockCenter);
            sessions.put(player.getUniqueId(), session);
        } else {
            if (now - session.lastActivityMillis > inactivityMillisToCloseSession) {
                closeSession(player.getUniqueId(), "INACTIVITY");
                session = new EasyPlaceSession(player.getUniqueId(), player.getName(), world.getName(), now, blockCenter);
                sessions.put(player.getUniqueId(), session);
            }
        }

        // 4) Taux de placement
        session.recentPlacementsMillis.addLast(now);
        while (!session.recentPlacementsMillis.isEmpty() &&
                now - session.recentPlacementsMillis.getFirst() > 1000L) {
            session.recentPlacementsMillis.removeFirst();
        }
        double blocksPerSecond = session.recentPlacementsMillis.size();
        if (blocksPerSecond > maxBlocksPerSecond) {
            ruleRate = true;
            suspicionScoreForThisBlock += suspicionPointsRate;
        }

        session.updateBoundingBox(blockCenter);
        session.lastActivityMillis = now;

        if (suspicionScoreForThisBlock <= 0) {
            return;
        }

        // Bloc suspect
        session.totalBlocksSuspects++;
        session.totalSuspicionScore += suspicionScoreForThisBlock;
        session.maxDistance = Math.max(session.maxDistance, distance);
        session.maxBlocksPerSecond = Math.max(session.maxBlocksPerSecond, blocksPerSecond);
        session.updateAverageDistance(distance);

        if (ruleDistance) session.ruleDistanceCount++;
        if (ruleSupportAir) session.ruleSupportAirCount++;
        if (ruleAngle) session.ruleAngleCount++;
        if (ruleRate) session.ruleRateCount++;

        boolean shouldCancelThisBlock = false;

        if (cancelPlacement && suspicionScoreForThisBlock >= suspicionThresholdCancel) {
            shouldCancelThisBlock = true;
        }

        if (session.totalSuspicionScore >= suspicionThresholdSession) {
            session.cancelTriggered = true;
        }

        if (cancelPlacement && session.cancelTriggered) {
            shouldCancelThisBlock = true;
        }

        if (shouldCancelThisBlock) {
            event.setCancelled(true);
        }

        if (notifyStaff && session.totalSuspicionScore >= suspicionThresholdSession && !session.staffNotified) {
            notifyStaff(session);
            session.staffNotified = true;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (sessions.containsKey(uuid)) {
            closeSession(uuid, "PLAYER_QUIT");
        }
    }

    /**
     * Ferme une session et loggue si nécessaire.
     */
    private void closeSession(UUID playerUuid, String reason) {
        EasyPlaceSession session = sessions.remove(playerUuid);
        if (session == null) {
            return;
        }

        if (!loggingEnabled || session.totalBlocksSuspects < minBlocksPerSession) {
            return;
        }

        long now = System.currentTimeMillis();
        EmpEasyPlaceLogEntry entry = new EmpEasyPlaceLogEntry(
                session.playerUuid,
                session.playerName,
                session.worldName,
                session.startTimeMillis,
                now,
                session.minX,
                session.minY,
                session.minZ,
                session.maxX,
                session.maxY,
                session.maxZ,
                session.totalBlocksSuspects,
                session.maxDistance,
                session.avgDistance,
                session.maxBlocksPerSecond,
                session.ruleDistanceCount,
                session.ruleSupportAirCount,
                session.ruleAngleCount,
                session.ruleRateCount,
                session.totalSuspicionScore,
                session.cancelTriggered,
                session.staffNotified,
                reason
        );

        // Ajoute à l'historique (en mémoire)
        history.addLast(entry);
        while (history.size() > historyMaxEntries) {
            history.removeFirst();
        }

        if (loggingConsole) {
            plugin.getLogger().log(Level.INFO, "[EmpBloqueur] " + entry.toSummaryString());
        }

        if (loggingFileEnabled && fileLogger != null) {
            fileLogger.logToFile(entry, loggingFilePath);
        }
    }

    /**
     * Notifie les staffs de la session suspecte.
     */
    private void notifyStaff(EasyPlaceSession session) {
        String msg = String.format(
            "§c[EmpBloqueur] Suspicion d'Easy Place: §e%s§7 dans le monde §e%s§7 zone [x:%d..%d, y:%d..%d, z:%d..%d] (blocs suspects: %d, score: %d)",
            session.playerName,
            session.worldName,
            session.minX, session.maxX,
            session.minY, session.maxY,
            session.minZ, session.maxZ,
            session.totalBlocksSuspects,
            session.totalSuspicionScore
        );

        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission(notifyStaffPermission))
            .forEach(p -> p.sendMessage(msg));

        plugin.getLogger().info(msg);
    }

    private boolean isAdjacent(Block placed, Block against) {
        if (placed.getWorld() != against.getWorld()) {
            return false;
        }
        Location p = placed.getLocation();
        Location a = against.getLocation();
        int dx = Math.abs(p.getBlockX() - a.getBlockX());
        int dy = Math.abs(p.getBlockY() - a.getBlockY());
        int dz = Math.abs(p.getBlockZ() - a.getBlockZ());
        return (dx + dy + dz) == 1;
    }

    private double calculateAngleDegrees(Location eye, Location target) {
        Vector dir = eye.getDirection().normalize();
        Vector toBlock = target.toVector().subtract(eye.toVector()).normalize();
        double dot = dir.dot(toBlock);
        if (dot > 1.0) dot = 1.0;
        if (dot < -1.0) dot = -1.0;
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * Récupère la dernière entrée de log pour un joueur donné.
     */
    public EmpEasyPlaceLogEntry getLastEntryForPlayer(UUID uuid) {
        for (EmpEasyPlaceLogEntry entry : (Iterable<EmpEasyPlaceLogEntry>) () -> history.descendingIterator()) {
            if (entry.getPlayerUuid().equals(uuid)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Récupère les N dernières sessions (tous joueurs confondus).
     */
    public List<EmpEasyPlaceLogEntry> getRecentEntries(int limit) {
        List<EmpEasyPlaceLogEntry> result = new ArrayList<>();
        if (limit <= 0) {
            return result;
        }

        int count = 0;
        for (EmpEasyPlaceLogEntry entry : (Iterable<EmpEasyPlaceLogEntry>) () -> history.descendingIterator()) {
            result.add(entry);
            count++;
            if (count >= limit) break;
        }
        return result;
    }

    /**
     * Donne un snapshot simplifié de la session en cours d'un joueur (si elle existe).
     */
    public EmpEasyPlaceLogEntry getCurrentSessionSnapshot(UUID uuid) {
        EasyPlaceSession session = sessions.get(uuid);
        if (session == null || session.totalBlocksSuspects < minBlocksPerSession) {
            return null;
        }
        long now = System.currentTimeMillis();

        return new EmpEasyPlaceLogEntry(
                session.playerUuid,
                session.playerName,
                session.worldName,
                session.startTimeMillis,
                now,
                session.minX,
                session.minY,
                session.minZ,
                session.maxX,
                session.maxY,
                session.maxZ,
                session.totalBlocksSuspects,
                session.maxDistance,
                session.avgDistance,
                session.maxBlocksPerSecond,
                session.ruleDistanceCount,
                session.ruleSupportAirCount,
                session.ruleAngleCount,
                session.ruleRateCount,
                session.totalSuspicionScore,
                session.cancelTriggered,
                session.staffNotified,
                "CURRENT_SESSION_SNAPSHOT"
        );
    }

    /**
     * Session interne pour agréger plusieurs placements suspects.
     */
    private static class EasyPlaceSession {
        final UUID playerUuid;
        final String playerName;
        final String worldName;

        final long startTimeMillis;
        long lastActivityMillis;

        int totalBlocksSuspects;
        int totalSuspicionScore;

        int minX;
        int minY;
        int minZ;
        int maxX;
        int maxY;
        int maxZ;

        double maxDistance;
        double avgDistance;
        int distanceSamples;

        double maxBlocksPerSecond;

        int ruleDistanceCount;
        int ruleSupportAirCount;
        int ruleAngleCount;
        int ruleRateCount;

        boolean cancelTriggered;
        boolean staffNotified;

        final Deque<Long> recentPlacementsMillis = new ArrayDeque<>();

        EasyPlaceSession(UUID playerUuid, String playerName, String worldName, long startTimeMillis, Location firstBlock) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.worldName = worldName;
            this.startTimeMillis = startTimeMillis;
            this.lastActivityMillis = startTimeMillis;

            int x = firstBlock.getBlockX();
            int y = firstBlock.getBlockY();
            int z = firstBlock.getBlockZ();

            this.minX = x;
            this.minY = y;
            this.minZ = z;
            this.maxX = x;
            this.maxY = y;
            this.maxZ = z;

            this.maxDistance = 0.0;
            this.avgDistance = 0.0;
            this.distanceSamples = 0;

            this.maxBlocksPerSecond = 0.0;
        }

        void updateBoundingBox(Location loc) {
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;

            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        void updateAverageDistance(double distance) {
            distanceSamples++;
            avgDistance += (distance - avgDistance) / distanceSamples;
        }
    }
}
