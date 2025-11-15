# 🔒 Aparaire - Système de spectateur restreint

**Aparaire** est un plugin Bukkit/Spigot qui transforme les joueurs non autorisés en **spectateurs restreints** capables d'explorer votre serveur sans pouvoir modifier le monde.

---

## 🎯 Cas d'usage

- **Serveurs privés** : laissez vos amis visiter sans risque de griefing
- **Serveurs créatifs** : montrez vos constructions sans donner de permissions
- **Tests de maps** : offrez des previews sans risque de destruction
- **Serveurs RP** : créez des zones "hors-jeu" où les joueurs peuvent observer

---

## ✨ Fonctionnalités

### 🛡️ Système de protections ultra-complet

Par défaut, les visiteurs peuvent **UNIQUEMENT** :
- ✅ Se déplacer (marcher, courir, sauter, nager, voler en elytra)
- ✅ Monter dans les **bateaux** et **minecarts**
- ✅ Activer les **plaques de pression** (doors automation)
- ✅ S'accroupir (sneak)

**Tout le reste est bloqué** :
- ❌ Casser/placer des blocs
- ❌ Infliger des dégâts (PVP/PVE)
- ❌ Recevoir des dégâts (invulnérable)
- ❌ Ouvrir des conteneurs (coffres, barils, fours, etc.)
- ❌ Utiliser des postes de travail (tables de craft, enclumes, etc.)
- ❌ Interagir avec les blocs (boutons, leviers, portes, etc.)
- ❌ Interagir avec les entités (villageois, item frames, etc.)
- ❌ Jeter/ramasser des items
- ❌ Utiliser des seaux
- ❌ Dormir dans les lits
- ❌ Utiliser les portails (Nether, End)
- ❌ Lancer des projectiles
- ❌ Crafting/déplacement d'items dans l'inventaire
- ❌ Écrire dans le chat *(optionnel, désactivable)*
- ❌ Utiliser des commandes *(whitelist configurable)*

### 🔄 Rechargement automatique de la whitelist

Le plugin détecte automatiquement les modifications de la whitelist :
- ✅ À chaque connexion de joueur
- ✅ Quand quelqu'un tape `/whitelist add/remove`
- ✅ Périodiquement (configurable, ex: toutes les 5 minutes)

**Plus besoin de `/reload` ou `/aparairereload` !**

### 🎁 Mode confort (optionnel)

Donne automatiquement aux visiteurs :
- 🔭 **Longue-vue** (spyglass) pour observer de loin
- 🍇 **Chorus fruits** pour se téléporter (pratique pour l'exploration)

Options :
- Retrait automatique de ces items quand le joueur est whitelisté
- Quantité configurable
- Désactivable complètement

### 📝 Surveillance du fichier config.yml

Le plugin surveille automatiquement les modifications du fichier `config.yml` :
- ✅ **Rechargement instantané** (debounce 500ms)
- ✅ **Validation complète** du schéma (types, bornes, clés manquantes)
- ✅ **Rollback automatique** si le YAML est invalide
- ✅ **Logs clairs** en cas d'erreur de configuration
- ✅ **Thread séparé** (pas de lag serveur)

**Édite ta config à la main, elle se recharge toute seule !**

### 🎨 Messages personnalisables

Tous les messages sont configurables dans le fichier `config.yml` :
- Préfixe du plugin
- Messages de refus (conteneurs, chat, commandes, etc.)
- Support des codes couleur Minecraft (`§a`, `§c`, etc.)

---

## 📦 Installation

1. **Télécharge** le fichier `Aparaire.jar`
2. **Place-le** dans le dossier `plugins/` de ton serveur
3. **Redémarre** le serveur
4. **Configure** le fichier `plugins/Aparaire/config.yml` (optionnel)

---

## ⚙️ Configuration

Le fichier `config.yml` est **entièrement commenté** et organisé en sections :

### 🔧 Sections principales

| Section | Description |
|---------|-------------|
| `protections.*` | Active/désactive chaque type de protection |
| `restrictions.*` | Bloque le chat et filtre les commandes |
| `mode-confort.*` | Donne des items aux visiteurs |
| `whitelist.*` | Configure le rechargement automatique |
| `messages.*` | Personnalise tous les messages |

### 📋 Exemple de configuration

```yaml
# Mode ultra-restrictif (défaut)
protections:
  block:
    break: true
    place: true
  damage:
    outgoing: true
    incoming: true

# Autoriser l'exploration libre
protections:
  teleport:
    chorus: false
mode-confort:
  enabled: true
  chorus: true
  chorus-amount: 16
```

**👉 Consulte le fichier `config.yml` généré pour voir toutes les options !**

---

## 🎮 Commandes

| Commande | Permission | Description |
|----------|-----------|-------------|
| `/aparairereload` | `aparaire.reload` (OP) | Recharge manuellement la whitelist et la config |

**💡 Astuce** : avec le rechargement automatique activé, tu n'auras presque jamais besoin de cette commande !

---

## 🔐 Permissions

Le plugin utilise la **whitelist native de Minecraft** + les **OPs** pour déterminer qui est autorisé.

**Joueurs autorisés** = OPs + Joueurs en whitelist  
**Joueurs restreints** = Tous les autres

Pas de système de permissions compliqué, c'est simple et efficace !

---

## 🏗️ Architecture technique

### Fichiers du plugin

```
src/siwa/valorium/
├── Aparaire.java                  # Classe principale + listeners
├── config/
│   ├── ConfigManager.java         # Gestion config + snapshot/rollback
│   ├── ConfigFileWatcher.java     # Surveillance du fichier (thread dédié)
│   └── ConfigValidator.java       # Validation du schéma YAML
```

## 👨‍💻 Auteur

Développé par **Siwa** pour le Valorium  
