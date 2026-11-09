package com.decmike.canonremote.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterHdr
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Iso
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decmike.canonremote.ui.CompositionGuide
import com.decmike.canonremote.ui.GuidePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rubriques de l'aide : (onglet, identifiant de section). */
enum class HelpTopic(val tab: Int, val section: String) {
    HOME(0, "start"),
    WIFI(0, "wifi"), BLUETOOTH(0, "bluetooth"), REMOTE(0, "remote"), SETTINGS(0, "settings"),
    GALLERY(0, "gallery"), SEQUENCES(0, "sequences"), DEVICE(0, "device"), TROUBLE(0, "trouble"),
    EXPOSURE(1, "triangle"), COMPOSITION(2, "guides"),
}

/** Ouverture de l'aide depuis n'importe quel écran. */
object HelpNav {
    private val _topic = MutableStateFlow<HelpTopic?>(null)
    val topic = _topic.asStateFlow()
    fun open(t: HelpTopic = HelpTopic.HOME) { _topic.value = t }
    fun close() { _topic.value = null }
}

private class Section(val id: String, val title: String, val icon: ImageVector, val content: @Composable () -> Unit)

@Composable
fun HelpScreen(topic: HelpTopic, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable(topic) { mutableIntStateOf(topic.tab) }
    // Surface : bloque les appuis vers l'écran situé dessous
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text("Aide et mémos", style = MaterialTheme.typography.titleLarge)
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Application", "Mémo photo", "Composition").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
            }
        }
        val sections = when (tab) {
            0 -> appSections
            1 -> photoSections
            else -> compositionSections
        }
        val initial = if (tab == topic.tab) topic.section else sections.first().id
        SectionList(sections, initial)
    }
    }
}

@Composable
private fun SectionList(sections: List<Section>, initial: String) {
    var open by remember(sections) { mutableStateOf(setOf(initial)) }
    val listState = rememberLazyListState()
    LaunchedEffect(sections) {
        val i = sections.indexOfFirst { it.id == initial }
        if (i > 0) listState.scrollToItem(i)
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(sections, key = { it.id }) { s ->
            val expanded = s.id in open
            Card(
                onClick = { open = if (expanded) open - s.id else open + s.id },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { s.content() }
                    }
                }
            }
        }
    }
}

// ============================================================ éléments visuels

@Composable private fun P(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable private fun B(text: String) = Row {
    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(14.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable private fun H(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp))

@Composable private fun Tip(text: String) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x33E0B04A)).padding(10.dp),
) {
    Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFE0B04A))
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** Ligne de tableau : valeur à gauche, usage à droite. */
@Composable private fun Row2(left: String, right: String, color: Color = MaterialTheme.colorScheme.primary) = Row(
    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.width(118.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.85f)).padding(vertical = 4.dp, horizontal = 6.dp)) {
        Text(left, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
    Spacer(Modifier.width(10.dp))
    Text(right, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
}

private val warm = Color(0xFFD2691E)
private val cool = Color(0xFF2E7D32)

/** Échelle colorée avec libellés aux extrémités. */
@Composable private fun Scale(values: List<String>, left: String, right: String, from: Color = warm, to: Color = cool) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEachIndexed { i, v ->
            val t = if (values.size > 1) i / (values.size - 1f) else 0f
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(lerp(from, to, t)).padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(v, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center) }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(left, style = MaterialTheme.typography.labelSmall, color = from, modifier = Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelSmall, color = to, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

// ============================================================ onglet Application

private val appSections = listOf(
    Section("start", "Bien démarrer", Icons.Default.Info) {
        P("Canon Remote pilote votre boîtier de deux façons :")
        B("Wi-Fi (CCAPI) : visée directe, tous les réglages, galerie, séquences, HDR, focus stacking.")
        B("Bluetooth (télécommande BR-E1) : déclenchement, pose B, vidéo et intervallomètre, sans Wi-Fi ni activation.")
        P("Navigation en Wi-Fi : Télécommande · Réglages · Galerie · Séquences · Appareil (barre du bas). En haut : Bluetooth, aide (?), déconnexion.")
        Tip("Touchez « ? » sur n'importe quel écran pour revenir ici. L'onglet « Mémo photo » rappelle l'ouverture, la vitesse, l'ISO, etc.")
    },
    Section("wifi", "Connexion Wi-Fi", Icons.Default.Wifi) {
        H("Une seule fois")
        B("Activez la Camera Control API sur le boîtier avec l'outil gratuit du programme développeur Canon.")
        H("Connexion directe (recommandée en extérieur)")
        B("Appareil : Réseau › Wi-Fi › Connexion CCAPI › Mode point d'accès de l'appareil › Connexion facile. Il affiche un SSID et un mot de passe.")
        B("Application : onglet « Wi-Fi de l'appareil », saisissez le mot de passe (et le SSID si plusieurs appareils), touchez « Connexion directe » et validez dans la fenêtre Android.")
        B("L'application trouve l'adresse de l'appareil toute seule ; les données mobiles restent actives.")
        B("« Déconnecter » rend le Wi-Fi : le téléphone revient sur son réseau habituel.")
        H("Même réseau (box ou partage de connexion)")
        B("Appareil : Réseau › Wi-Fi › connexion CCAPI en rejoignant votre box / partage de connexion.")
        B("Téléphone : connectez-vous au même réseau.")
        B("Touchez « Rechercher » (balayage du réseau) ou saisissez l'IP affichée par l'appareil, port 8080.")
        Tip("L'application force l'utilisation du Wi-Fi même si celui-ci n'a pas d'accès Internet : inutile de couper les données mobiles.")
    },
    Section("bluetooth", "Télécommande Bluetooth", Icons.Default.Bluetooth) {
        B("Appareil : Réglages Bluetooth › Bluetooth activé › Télécommande › Appairage.")
        B("Application : Télécommande Bluetooth, puis touchez l'appareil. Acceptez l'association si Android la demande.")
        B("Ensuite, « Dernier appareil » suffit pour se reconnecter.")
        H("Commandes")
        B("Déclencheur (avec AF), AF à maintenir, retardateur 2 s, vidéo, zoom (PowerShot).")
        B("Pose B : boîtier en mode B, un appui ouvre, un second ferme ; le chrono s'affiche.")
        B("Intervallomètre : vitesse réglée sur l'appareil ou poses B au-delà de 30 s.")
        Tip("Le Bluetooth ne transmet ni réglages ni image : HDR, focus stacking et visée restent en Wi-Fi.")
    },
    Section("remote", "Télécommande et visée", Icons.Default.CameraAlt) {
        B("Visée directe : l'œil masque/affiche le flux. La taille (fluidité) se règle dans Appareil.")
        B("Toucher l'image : déplace le collimateur AF.")
        B("MF <<< … >>> : déplace la mise au point vers le proche (<) ou l'infini (>), 3 tailles de pas.")
        B("Grille : choisissez une mire de composition, un format de cadre et une couleur. Le bouton ⇋ retourne les mires orientées (spirale, triangles…).")
        B("Puces du haut : vitesse, ouverture, ISO, correction, balance des blancs… touchez pour modifier.")
        B("Déclencheur : retardateur 0/2/10 s, AF au déclenchement on/off. En pose B : un appui ouvre, un second ferme.")
        B("Bouton AF : maintenir = mi-course (mise au point sans déclencher).")
        B("Vidéo : bascule en mode vidéo et lance/arrête l'enregistrement.")
    },
    Section("settings", "Réglages", Icons.Default.Tune) {
        P("Liste de tous les réglages que le boîtier expose. Les plus utiles sont en tête ; la recherche filtre par nom.")
        B("Touchez un réglage pour le modifier : un rappel explique son effet.")
        B("Les valeurs se mettent à jour quand vous tournez les molettes de l'appareil.")
        B("Un réglage grisé n'est pas modifiable dans le mode actuel (ex. vitesse en mode Av).")
    },
    Section("gallery", "Galerie", Icons.Default.PhotoLibrary) {
        B("Filtres : JPEG/HEIF, RAW, vidéos ; plusieurs cartes si le boîtier en a deux.")
        B("Appui long : sélection multiple, puis Télécharger ou Supprimer.")
        B("Visionneuse : balayez pour changer d'image, (i) pour les infos de prise de vue, grille pour analyser la composition.")
        B("Fichiers enregistrés dans Images/CanonRemote (RAW dans Téléchargements/CanonRemote).")
    },
    Section("sequences", "Séquences", Icons.Default.Timer) {
        H("Intervallomètre")
        B("Nombre d'images (0 = illimité), temps de pose, pause entre deux poses, délai de départ.")
        B("Au-delà de 30 s, la pose B est utilisée automatiquement (boîtier en M ou B).")
        B("« Début à début » : intervalle constant pour les timelapses.")
        H("HDR")
        B("3 à 9 images, écart de ⅓ à 3 IL. En M la vitesse varie, sinon la correction d'exposition. Réglage d'origine restauré.")
        B("Plusieurs séries + pause = HDR timelapse.")
        H("Focus stacking")
        B("Faites la mise au point sur le plan le plus proche, puis choisissez nombre d'images, taille et nombre de pas.")
        Tip("Les séquences continuent écran éteint ; la notification affiche la progression et un bouton Arrêter. Désactivez l'arrêt auto de l'appareil.")
    },
    Section("device", "Appareil et préférences", Icons.Default.Build) {
        B("Infos boîtier, batterie, objectif, température, espace libre.")
        B("Régler l'heure : copie la date et l'heure du téléphone dans l'appareil.")
        B("Transfert automatique : chaque nouvelle photo est copiée sur le téléphone (option RAW).")
        B("Visée : taille, écran de l'appareil éteint, histogramme, écran du téléphone toujours allumé.")
    },
    Section("trouble", "Dépannage", Icons.Default.Info) {
        B("« Connexion impossible » : même réseau ? CCAPI activée ? IP et port 8080 corrects ?")
        B("« Device busy » : l'appareil est occupé (enregistrement, menu ouvert) ; l'application réessaie seule.")
        B("Connexion perdue : l'appareil s'est mis en veille. Augmentez ou désactivez l'arrêt auto.")
        B("Bluetooth introuvable : appareil en mode appairage télécommande, Bluetooth et autorisations du téléphone activés. « Oublier » puis recommencer si besoin.")
        B("Commande refusée : certaines fonctions dépendent du modèle ou du mode (ex. vitesse en Av).")
    },
)

// ============================================================ onglet Mémo photo

private val photoSections = listOf(
    Section("triangle", "Le triangle d'exposition", Icons.Default.Camera) {
        P("Trois réglages dosent la lumière. Chacun a un effet secondaire :")
        Row2("Ouverture", "Profondeur de champ (flou d'arrière-plan)", Color(0xFF6D4C41))
        Row2("Vitesse", "Flou ou netteté du mouvement", Color(0xFF1565C0))
        Row2("ISO", "Bruit numérique (grain)", Color(0xFF2E7D32))
        P("1 IL (un « stop ») = deux fois plus ou moins de lumière. Ces trois expositions sont équivalentes :")
        B("f/8 · 1/125 s · ISO 100")
        B("f/5.6 · 1/250 s · ISO 100 (ouverture +1, vitesse −1)")
        B("f/8 · 1/250 s · ISO 200 (vitesse −1, ISO +1)")
        Tip("Choisissez d'abord le réglage dont l'effet compte (flou d'arrière-plan ? mouvement figé ?), puis compensez avec les deux autres.")
    },
    Section("aperture", "Ouverture (f/)", Icons.Default.FilterHdr) {
        Scale(listOf("1.4", "2", "2.8", "4", "5.6", "8", "11", "16", "22"), "Grande ouverture", "Petite ouverture")
        P("Petit chiffre = grande ouverture = plus de lumière et arrière-plan flou. Grand chiffre = moins de lumière et tout est net.")
        H("Repères")
        Row2("f/1.4 – f/2", "Portrait en intérieur, très faible profondeur de champ, peu de lumière")
        Row2("f/2.8 – f/4", "Portrait, sujet détaché, sport en salle")
        Row2("f/5.6 – f/8", "Groupes, rue, voyage : bon compromis de netteté")
        Row2("f/8 – f/11", "Paysage : premier plan et horizon nets")
        Row2("f/16 – f/22", "Grande profondeur, étoiles de soleil ; la diffraction adoucit l'image")
        Tip("La plupart des objectifs sont les plus nets 2 à 3 crans après leur ouverture maximale (souvent f/5.6 – f/8).")
    },
    Section("shutter", "Vitesse d'obturation", Icons.Default.ShutterSpeed) {
        Scale(listOf("30\"", "1\"", "1/4", "1/15", "1/60", "1/125", "1/250", "1/1000", "1/4000"), "Lent : plus de lumière, flou", "Rapide : moins de lumière, figé")
        H("Quelle vitesse ?")
        Row2("1/1000 – 1/4000", "Sport, oiseaux en vol, éclaboussures : tout est figé", Color(0xFF0D47A1))
        Row2("1/250 – 1/500", "Personnes qui marchent, rue, enfants", Color(0xFF00838F))
        Row2("1/60 – 1/125", "Photos du quotidien, portraits posés", Color(0xFF2E7D32))
        Row2("1/15 – 1/30", "Flou de mouvement créatif, filé ; attention au bougé", Color(0xFFEF8F00))
        Row2("1/8 – 1 s", "Ambiances de nuit, eau soyeuse : trépied", Color(0xFF6A1B9A))
        Row2("1 s – 30 s et B", "Paysages de nuit, filés de phares, light painting, filtres ND", Color(0xFF263238))
        Tip("À main levée, ne descendez pas sous 1/focale (50 mm → 1/50 s ; ×1,6 en APS-C). La stabilisation fait gagner 3 à 5 IL sur les sujets immobiles.")
        P("Pour les poses longues, utilisez la télécommande (Bluetooth ou Wi-Fi) ou le retardateur pour éviter de faire bouger l'appareil.")
    },
    Section("iso", "Sensibilité ISO", Icons.Default.Iso) {
        Scale(listOf("100", "200", "400", "800", "1600", "3200", "6400", "12800", "25600"), "Image propre", "Plus de bruit", from = cool, to = warm)
        Row2("100 – 200", "Plein soleil, trépied, qualité maximale", cool)
        Row2("400 – 800", "Temps couvert, ombre, fin de journée", Color(0xFF7CB342))
        Row2("1600 – 3200", "Intérieur lumineux, début de soirée", Color(0xFFF9A825))
        Row2("6400 – 12800", "Intérieur sombre, concert, nuit en ville", Color(0xFFEF6C00))
        Row2("25600 +", "Conditions extrêmes : mieux vaut une photo bruitée que floue", warm)
        Tip("Gardez l'ISO bas et montez-le seulement quand la vitesse deviendrait trop lente. L'ISO auto avec une vitesse minimale est un bon réglage.")
    },
    Section("sunny16", "Règle du « f/16 ensoleillé »", Icons.Default.WbSunny) {
        P("Sans posemètre : en plein soleil, f/16 et une vitesse proche de 1/ISO donnent une exposition correcte (ISO 100 → 1/125 s).")
        Row2("Plein soleil", "f/16", Color(0xFFF9A825))
        Row2("Léger voile", "f/11", Color(0xFFFBC02D))
        Row2("Couvert", "f/8", Color(0xFF90A4AE))
        Row2("Très couvert", "f/5.6", Color(0xFF78909C))
        Row2("Ombre / coucher", "f/4", Color(0xFF546E7A))
        Row2("Neige, sable", "f/22 (ou f/16 avec vitesse ×2)", Color(0xFFB0BEC5))
        Tip("Toute combinaison équivalente convient : f/8 à 1/500 s expose comme f/16 à 1/125 s. Choisissez selon le flou souhaité.")
    },
    Section("modes", "Modes de prise de vue", Icons.Default.Tune) {
        Row2("P", "Programme : l'appareil choisit ouverture et vitesse ; vous gardez ISO, correction, balance.")
        Row2("Av", "Priorité ouverture : vous contrôlez la profondeur de champ. Le plus polyvalent.")
        Row2("Tv", "Priorité vitesse : vous contrôlez le mouvement (sport, filé).")
        Row2("M", "Manuel : tout est fixé, exposition constante (studio, nuit, panoramas, HDR).")
        Row2("B", "Pose longue tant que le déclencheur est enfoncé (> 30 s).")
        Row2("Fv", "Flexible : chaque paramètre peut être auto ou manuel.")
    },
    Section("histo", "Correction d'exposition et histogramme", Icons.Default.BrightnessMedium) {
        P("L'histogramme montre la répartition des tons : ombres à gauche, hautes lumières à droite.")
        B("Collé à droite : hautes lumières brûlées → correction −.")
        B("Collé à gauche : ombres bouchées → correction +.")
        B("Neige, plage, sujet blanc : l'appareil sous-expose → +⅔ à +1⅓.")
        B("Scène sombre, sujet noir : l'appareil surexpose → −⅔ à −1.")
        Tip("En RAW, exposez « à droite » sans brûler : vous récupérez mieux les ombres en retouche.")
    },
    Section("wb", "Balance des blancs", Icons.Default.Thermostat) {
        Scale(listOf("2000K", "3200K", "4000K", "5500K", "6500K", "8000K"), "Lumière chaude", "Lumière froide",
            from = Color(0xFFFF8F00), to = Color(0xFF42A5F5))
        Row2("≈ 1900 K", "Bougie")
        Row2("≈ 3200 K", "Ampoule halogène / tungstène")
        Row2("≈ 5500 K", "Soleil de midi, flash")
        Row2("≈ 6500 K", "Ciel couvert")
        Row2("≈ 7500 K", "Ombre par beau temps")
        Tip("En RAW, la balance se corrige sans perte après coup. Pour un timelapse, fixez-la (pas d'AWB) pour éviter le scintillement.")
    },
    Section("focus", "Mise au point et mesure", Icons.Default.CenterFocusStrong) {
        Row2("One-Shot", "Sujet immobile : la mise au point se verrouille.")
        Row2("Servo", "Sujet en mouvement : suivi continu.")
        Row2("MF", "Manuel : macro, astro, focus stacking.")
        H("Mesure de lumière")
        Row2("Évaluative", "Toute l'image : usage général.")
        Row2("Sélective / spot", "Petite zone : contre-jour, scène de spectacle.")
        Row2("Pondérée centrale", "Priorité au centre : portraits.")
        Tip("Astro : mise au point manuelle sur une étoile brillante avec la visée agrandie, puis ne plus toucher la bague.")
    },
    Section("quality", "RAW ou JPEG ?", Icons.Default.PhotoLibrary) {
        Row2("RAW (CR3)", "Toutes les données du capteur : balance, exposition et couleurs ajustables. Fichiers lourds.")
        Row2("JPEG / HEIF", "Prêt à partager, style d'image appliqué. Retouche limitée.")
        Row2("RAW + JPEG", "Le JPEG pour partager depuis le téléphone, le RAW pour la retouche.")
    },
)

// ============================================================ onglet Composition

private val compositionSections: List<Section> = listOf(
    Section("guides", "Mires disponibles", Icons.Default.GridOn) {
        P("Dans la visée (et la visionneuse de la galerie), touchez la grille pour choisir une mire. Elles s'adaptent au format de l'image ; choisissez un format de cadre (1:1, 4:5, 16:9…) pour simuler un recadrage.")
        CompositionGuide.entries.filter { it != CompositionGuide.NONE }.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { g ->
                    Column(Modifier.weight(1f)) {
                        GuidePreview(g, Modifier.fillMaxWidth())
                        Text(g.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(g.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    },
    Section("tips", "Conseils de composition", Icons.Default.Lightbulb) {
        B("Horizon droit, et rarement au centre : 1/3 de ciel pour mettre le sol en valeur, 2/3 si le ciel est spectaculaire.")
        B("Lignes directrices : routes, barrières, rivières qui mènent l'œil vers le sujet.")
        B("Premier plan : un élément proche donne de la profondeur aux paysages.")
        B("Espace dans le sens du regard ou du mouvement du sujet.")
        B("Cadre dans le cadre : porte, fenêtre, branches.")
        B("Espace négatif : un sujet petit dans un grand vide crée de la force.")
        B("Symétrie et reflets : centrez franchement (mire Croix).")
        B("Simplifiez : retirez du cadre tout ce qui n'apporte rien.")
        Tip("Il n'y a pas de règle absolue : apprenez-les, puis brisez-les volontairement.")
    },
)
