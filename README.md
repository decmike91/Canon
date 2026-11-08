# Canon Remote — télécommande Wi-Fi Android pour appareils Canon

Application Android (Kotlin / Jetpack Compose) qui pilote un boîtier Canon en Wi-Fi grâce à la
**Canon Camera Control API (CCAPI)**, l'API HTTP officielle de Canon.

## Fonctions

| Domaine | Fonctions |
|---|---|
| **Connexion** | Saisie de l'IP, recherche automatique sur le réseau, forçage du Wi-Fi (le Wi-Fi de l'appareil n'a pas d'Internet), reconnexion à la dernière IP |
| **Télécommande** | Visée directe, grille (tiers / 4×4 / diagonales), histogramme, AF tactile, mise au point manuelle (6 pas), déclenchement avec ou sans AF, retardateur 2 s / 10 s, bouton AF à maintenir (mi-course), **pose B** (ouvrir/fermer + chrono), enregistrement vidéo, vignette de la dernière photo |
| **Réglages** | Lecture de *tous* les réglages exposés par le boîtier, modification (listes, plages numériques, réglages composés comme la qualité RAW+JPEG), recherche, mise à jour en direct quand on tourne les molettes |
| **Galerie** | Cartes SD multiples, filtres JPEG / RAW / vidéo, vignettes, visionneuse plein écran avec balayage, infos EXIF, téléchargement simple ou multiple, suppression |
| **Séquences** | **Intervallomètre** : nombre d'images (ou illimité), temps de pose (vitesse ou pose B au-delà de 30 s), pause entre les poses ou intervalle début-à-début, délai de départ, préréglages (timelapse, astro, filé d'étoiles, pose longue, filtre ND) |
| **HDR** | Bracketing 3 / 5 / 7 / 9 images, écart ⅓ à 3 IL, par la vitesse (mode M) ou par la correction d'exposition (P/Av/Tv), **HDR timelapse** (séries répétées), réglage d'origine restauré à la fin |
| **Focus stacking** | N images avec déplacement motorisé de la mise au point entre chaque vue |
| **Appareil** | Modèle, firmware, n° de série, batterie, objectif, température, espace libre des cartes, **synchronisation de l'heure** |
| **Transfert auto** | Les nouvelles photos sont copiées sur le téléphone dès la prise de vue (option RAW) |

Les séquences tournent dans un service au premier plan : elles continuent écran éteint,
avec une notification de progression et un bouton « Arrêter ».

Fonctions reprises des applications existantes : Canon Camera Connect (visée, déclenchement,
transfert, transfert auto, réglage de l'heure), qDslrDashboard / DSLR Controller / Helicon Remote
(intervallomètre, pose B, HDR, focus stacking, histogramme, grilles).

## Installation

1. Ouvrez l'onglet **Releases** du dépôt et téléchargez `app-release.apk`
   (version « latest » reconstruite à chaque modification), ou récupérez l'APK dans
   **Actions › dernier build › Artifacts**.
2. Sur le téléphone, autorisez l'installation depuis des sources inconnues et installez l'APK.
   Android 10 minimum.

## Préparer l'appareil

1. **Activer la CCAPI** (une seule fois) : Canon fournit l'outil d'activation après inscription
   gratuite sur le programme développeur Canon (developercommunity.usa.canon.com). Une fois activée,
   l'option « Camera Control API » apparaît dans le menu Wi-Fi du boîtier.
2. Sur l'appareil : *Réseau › Wi-Fi › Connexion CCAPI*. Deux possibilités :
   - rejoindre votre box/partage de connexion (le téléphone doit être sur le même réseau) ;
   - ou utiliser l'appareil comme point d'accès et y connecter le téléphone.
3. L'adresse IP s'affiche sur l'écran de l'appareil (souvent `192.168.1.2` en mode point d'accès).
   Dans l'application, touchez **Rechercher** ou saisissez l'adresse, puis **Se connecter**.

Boîtiers compatibles CCAPI : la plupart des EOS R (R, RP, R3, R5, R5 II, R6, R6 II, R7, R8, R10, R50, R100…),
EOS 90D, 850D, 250D, M50 Mk II, M6 Mk II, M200, PowerShot G5 X II, G7 X III… Certaines fonctions
(visée tactile, vidéo, focus stacking) dépendent du modèle : l'application affiche un message
si le boîtier refuse une commande.

## Développement

```bash
./gradlew testDebugUnitTest   # tests unitaires (calculs d'exposition, séquences)
./gradlew assembleDebug       # APK de debug
```

Structure :

```
app/src/main/java/com/decmike/canonremote/
├── ccapi/      client CCAPI (HTTP/JSON), calculs d'exposition, recherche réseau
├── data/       état global, préférences, enregistrement dans la galerie
├── jobs/       moteur de séquences (intervallomètre, HDR, focus stack) + service
└── ui/         écrans Compose (connexion, télécommande, réglages, galerie, séquences, appareil)
```

La GitHub Action `.github/workflows/android.yml` lance les tests, construit l'APK et le publie
dans la release `latest`.
