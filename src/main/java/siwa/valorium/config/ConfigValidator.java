package siwa.valorium.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Valide la structure et les valeurs du fichier config.yml
 * ⚠️ Toutes les clés sont alignées avec le plugin principal
 */
public class ConfigValidator {

    private final Logger logger;

    public ConfigValidator(Logger logger) {
        this.logger = logger;
    }

    /**
     * Valide la configuration complète
     * @param config Configuration à valider
     * @return Liste des erreurs (vide si OK)
     */
    public List<String> validate(YamlConfiguration config) {
        List<String> errors = new ArrayList<>();

        // ═══ MODE CONFORT ═══
        validateBoolean(config, "mode-confort.enabled", errors);
        validateBoolean(config, "mode-confort.spyglass", errors);
        validateBoolean(config, "mode-confort.chorus", errors);
        validateInteger(config, "mode-confort.chorus-amount", 1, 64, errors);
        validateBoolean(config, "mode-confort.keep-starter-items", errors);

        // ═══ WHITELIST ═══
        validateBoolean(config, "whitelist.auto-reload-on-join", errors);
        validateBoolean(config, "whitelist.auto-reload-on-command", errors);
        validateInteger(config, "whitelist.auto-reload-period-seconds", 0, 3600, errors);

        // ═══ PROTECTIONS : BLOCS ═══
        validateBoolean(config, "protections.block.break", errors);
        validateBoolean(config, "protections.block.place", errors);

        // ═══ PROTECTIONS : COMBAT ═══
        validateBoolean(config, "protections.damage.outgoing", errors);
        validateBoolean(config, "protections.damage.incoming", errors);

        // ═══ PROTECTIONS : FAIM ═══
        validateBoolean(config, "protections.hunger.freeze", errors);

        // ═══ PROTECTIONS : INVENTAIRES ═══
        validateBoolean(config, "protections.inventory.containers", errors);

        // ═══ PROTECTIONS : POSTES DE TRAVAIL ═══
        validateBoolean(config, "protections.workstations.block", errors);

        // ═══ PROTECTIONS : INTERACTIONS ═══
        validateBoolean(config, "protections.interact.blocks", errors);
        validateBoolean(config, "protections.interact.entities", errors);

        // ═══ PROTECTIONS : VÉHICULES ═══
        validateBoolean(config, "protections.vehicles.use", errors);

        // ═══ PROTECTIONS : ITEMS ═══
        validateBoolean(config, "protections.items.drop", errors);
        validateBoolean(config, "protections.items.pickup", errors);

        // ═══ PROTECTIONS : SEAUX ═══
        validateBoolean(config, "protections.buckets.fill", errors);
        validateBoolean(config, "protections.buckets.empty", errors);

        // ═══ PROTECTIONS : LIT ═══
        validateBoolean(config, "protections.bed.sleep", errors);

        // ═══ PROTECTIONS : PORTAILS ═══
        validateBoolean(config, "protections.portals.use", errors);

        // ═══ PROTECTIONS : TÉLÉPORTATION ═══
        validateBoolean(config, "protections.teleport.ender_pearl", errors);
        validateBoolean(config, "protections.teleport.chorus", errors);

        // ═══ PROTECTIONS : PROJECTILES ═══
        validateBoolean(config, "protections.projectiles.launch", errors);

        // ═══ PROTECTIONS : CRAFTING ═══
        validateBoolean(config, "protections.crafting.clicks", errors);

        // ═══ RESTRICTIONS ═══
        validateBoolean(config, "restrictions.chat.enabled", errors);
        validateBoolean(config, "restrictions.commands.enabled", errors);
        validateStringList(config, "restrictions.commands.allowed", errors);

        // ═══ MESSAGES (tous les messages utilisés) ═══
        validateString(config, "messages.prefix", errors);
        validateString(config, "messages.no-permission", errors);
        validateString(config, "messages.container-denied", errors);
        validateString(config, "messages.workstation-denied", errors);
        validateString(config, "messages.entity-denied", errors);
        validateString(config, "messages.bed-denied", errors);
        validateString(config, "messages.portal-denied", errors);
        validateString(config, "messages.chat-denied", errors);
        validateString(config, "messages.command-denied", errors);
        validateString(config, "messages.items-removed", errors);

        validateAllMessages(config, errors);

        return errors;
    }

    /**
     * Valide qu'une clé booléenne existe et est bien un booléen
     */
    private void validateBoolean(YamlConfiguration config, String key, List<String> errors) {
        if (!config.contains(key)) {
            errors.add("Clé manquante : " + key);
            return;
        }

        if (!config.isBoolean(key)) {
            errors.add("Type invalide pour '" + key + "' : doit être true/false");
        }
    }

    /**
     * Valide qu'une clé entière existe et est dans les bornes
     */
    private void validateInteger(YamlConfiguration config, String key, int min, int max, List<String> errors) {
        if (!config.contains(key)) {
            errors.add("Clé manquante : " + key);
            return;
        }

        if (!config.isInt(key)) {
            errors.add("Type invalide pour '" + key + "' : doit être un nombre entier");
            return;
        }

        int value = config.getInt(key);
        if (value < min || value > max) {
            errors.add("Valeur hors limites pour '" + key + "' : " + value + " (attendu: " + min + "-" + max + ")");
        }
    }

    /**
     * Valide qu'une clé string existe et n'est pas vide
     */
    private void validateString(YamlConfiguration config, String key, List<String> errors) {
        if (!config.contains(key)) {
            errors.add("Clé manquante : " + key);
            return;
        }

        if (!config.isString(key)) {
            errors.add("Type invalide pour '" + key + "' : doit être une chaîne de caractères");
            return;
        }

        String value = config.getString(key);
        if (value == null || value.trim().isEmpty()) {
            errors.add("Valeur vide pour '" + key + "'");
        }
    }

    /**
     * Valide qu'une clé liste de strings existe et est bien une liste
     * (peut être vide si la fonctionnalité est désactivée)
     */
    private void validateStringList(YamlConfiguration config, String key, List<String> errors) {
        if (!config.contains(key)) {
            errors.add("Clé manquante : " + key);
            return;
        }

        if (!config.isList(key)) {
            errors.add("Type invalide pour '" + key + "' : doit être une liste");
            return;
        }

        List<?> rawList = config.getList(key);
        if (rawList == null) {
            errors.add("Liste nulle pour '" + key + "'");
            return;
        }

        // Vérifie que tous les éléments sont des strings
        for (int i = 0; i < rawList.size(); i++) {
            Object element = rawList.get(i);
            if (!(element instanceof String)) {
                errors.add("Élément invalide dans '" + key + "' [index " + i + "] : doit être une chaîne de caractères");
            }
        }
    }

    /**
     * Affiche les erreurs de validation dans les logs
     */
    public void logErrors(List<String> errors) {
        if (errors.isEmpty()) return;

        logger.warning("╔══════════════════════════════════════════════════════════╗");
        logger.warning("║  ERREUR(S) DE VALIDATION DU FICHIER CONFIG.YML          ║");
        logger.warning("╠══════════════════════════════════════════════════════════╣");

        for (String error : errors) {
            logger.log(Level.WARNING, "\u2551  \u274c {0}", error);
        }

        logger.warning("╚══════════════════════════════════════════════════════════╝");
        logger.warning("La configuration précédente a été conservée.");
    }

    /**
     * Valide tous les messages utilisés dans le plugin
     */
    private void validateAllMessages(YamlConfiguration config, List<String> errors) {
        String[] messageKeys = {
            "messages.prefix",
            "messages.no-permission",
            "messages.container-denied",
            "messages.workstation-denied",
            "messages.entity-denied",
            "messages.bed-denied",
            "messages.portal-denied",
            "messages.chat-denied",
            "messages.command-denied",
            "messages.items-removed"
        };

        for (String key : messageKeys) {
            validateString(config, key, errors);
        }
    }
}
