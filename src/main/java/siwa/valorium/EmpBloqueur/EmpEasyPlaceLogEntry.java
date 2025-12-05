package siwa.valorium.EmpBloqueur;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Modèle représentant une "session" d'utilisation suspecte d'Easy Place.
 */
public class EmpEasyPlaceLogEntry {

    private final UUID playerUuid;
    private final String playerName;

    private final long startTimeMillis;
    private final long endTimeMillis;

    private final String worldName;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private final int totalBlocksSuspects;
    private final double maxDistance;
    private final double avgDistance;
    private final double maxBlocksPerSecond;

    private final int ruleDistanceCount;
    private final int ruleSupportAirCount;
    private final int ruleAngleCount;
    private final int ruleRateCount;

    private final int totalSuspicionScore;
    private final boolean blocksCancelled;
    private final boolean staffNotified;
    private final String closeReason;

    public EmpEasyPlaceLogEntry(
            UUID playerUuid,
            String playerName,
            String worldName,
            long startTimeMillis,
            long endTimeMillis,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int totalBlocksSuspects,
            double maxDistance,
            double avgDistance,
            double maxBlocksPerSecond,
            int ruleDistanceCount,
            int ruleSupportAirCount,
            int ruleAngleCount,
            int ruleRateCount,
            int totalSuspicionScore,
            boolean blocksCancelled,
            boolean staffNotified,
            String closeReason
    ) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.worldName = worldName;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.totalBlocksSuspects = totalBlocksSuspects;
        this.maxDistance = maxDistance;
        this.avgDistance = avgDistance;
        this.maxBlocksPerSecond = maxBlocksPerSecond;
        this.ruleDistanceCount = ruleDistanceCount;
        this.ruleSupportAirCount = ruleSupportAirCount;
        this.ruleAngleCount = ruleAngleCount;
        this.ruleRateCount = ruleRateCount;
        this.totalSuspicionScore = totalSuspicionScore;
        this.blocksCancelled = blocksCancelled;
        this.staffNotified = staffNotified;
        this.closeReason = closeReason;
    }

    public String toSummaryString() {
        String start = formatInstant(startTimeMillis);
        String end = formatInstant(endTimeMillis);

        return String.format(
            "[%s → %s] EasyPlace suspect: joueur=%s (uuid=%s), monde=%s, zone=[x:%d..%d,y:%d..%d,z:%d..%d], blocsSuspects=%d, score=%d, maxDist=%.2f, maxBps=%.2f, cancel=%s, staff=%s, reason=%s",
            start,
            end,
            playerName,
            playerUuid,
            worldName,
            minX, maxX,
            minY, maxY,
            minZ, maxZ,
            totalBlocksSuspects,
            totalSuspicionScore,
            maxDistance,
            maxBlocksPerSecond,
            blocksCancelled,
            staffNotified,
            closeReason
        );
    }

    public String toFileString() {
        return String.format(
            "{ \"start\":\"%s\", \"end\":\"%s\", \"player\":\"%s\", \"uuid\":\"%s\", \"world\":\"%s\", \"min\":[%d,%d,%d], \"max\":[%d,%d,%d], \"blocksSuspects\":%d, \"score\":%d, \"maxDistance\":%.4f, \"avgDistance\":%.4f, \"maxBps\":%.4f, \"ruleDistance\":%d, \"ruleSupportAir\":%d, \"ruleAngle\":%d, \"ruleRate\":%d, \"cancel\":%b, \"staff\":%b, \"reason\":\"%s\" }",
            formatInstant(startTimeMillis),
            formatInstant(endTimeMillis),
            escape(playerName),
            playerUuid,
            escape(worldName),
            minX, minY, minZ,
            maxX, maxY, maxZ,
            totalBlocksSuspects,
            totalSuspicionScore,
            maxDistance,
            avgDistance,
            maxBlocksPerSecond,
            ruleDistanceCount,
            ruleSupportAirCount,
            ruleAngleCount,
            ruleRateCount,
            blocksCancelled,
            staffNotified,
            escape(closeReason)
        );
    }

    private String formatInstant(long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getTotalBlocksSuspects() {
        return totalBlocksSuspects;
    }

    public int getTotalSuspicionScore() {
        return totalSuspicionScore;
    }

    public boolean isBlocksCancelled() {
        return blocksCancelled;
    }

    public boolean isStaffNotified() {
        return staffNotified;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public static EmpEasyPlaceLogEntry parseFromLogLine(String line) {
    if (line == null || line.isEmpty()) {
        return null;
    }

    String[] parts = line.split("\\|");
    // On attend au moins 23 champs
    if (parts.length < 23) {
        return null;
    }

    try {
        UUID playerUuid = UUID.fromString(parts[0]);
        String playerName = parts[1];
        String worldName = parts[2];

        long startTimeMillis = Long.parseLong(parts[3]);
        long endTimeMillis = Long.parseLong(parts[4]);

        int minX = Integer.parseInt(parts[5]);
        int minY = Integer.parseInt(parts[6]);
        int minZ = Integer.parseInt(parts[7]);
        int maxX = Integer.parseInt(parts[8]);
        int maxY = Integer.parseInt(parts[9]);
        int maxZ = Integer.parseInt(parts[10]);

        int totalBlocksSuspects = Integer.parseInt(parts[11]);

        double maxDistance = Double.parseDouble(parts[12]);
        double avgDistance = Double.parseDouble(parts[13]);
        double maxBlocksPerSecond = Double.parseDouble(parts[14]);

        int ruleDistanceCount = Integer.parseInt(parts[15]);
        int ruleSupportAirCount = Integer.parseInt(parts[16]);
        int ruleAngleCount = Integer.parseInt(parts[17]);
        int ruleRateCount = Integer.parseInt(parts[18]);

        int totalSuspicionScore = Integer.parseInt(parts[19]);

        boolean cancelTriggered = Boolean.parseBoolean(parts[20]);
        boolean staffNotified = Boolean.parseBoolean(parts[21]);

        String closeReason = parts[22];

        return new EmpEasyPlaceLogEntry(
            playerUuid,
            playerName,
            worldName,
            startTimeMillis,
            endTimeMillis,
            minX, minY, minZ,
            maxX, maxY, maxZ,
            totalBlocksSuspects,
            maxDistance,
            avgDistance,
            maxBlocksPerSecond,
            ruleDistanceCount,
            ruleSupportAirCount,
            ruleAngleCount,
            ruleRateCount,
            totalSuspicionScore,
            cancelTriggered,
            staffNotified,
            closeReason
        );
    } catch (Exception ex) {
        // En cas de ligne corrompue, on la skip silencieusement
        return null;
    }
}
    
}
