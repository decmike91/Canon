package com.decmike.canonremote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.data.ConnState
import com.decmike.canonremote.ui.screens.BluetoothScreen
import com.decmike.canonremote.ui.screens.ConnectScreen
import com.decmike.canonremote.ui.screens.DeviceScreen
import com.decmike.canonremote.ui.screens.GalleryScreen
import com.decmike.canonremote.ui.screens.RemoteScreen
import com.decmike.canonremote.ui.screens.SequenceScreen
import com.decmike.canonremote.ui.screens.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Remote("remote", "Télécommande", Icons.Default.CameraAlt),
    Settings("settings", "Réglages", Icons.Default.Tune),
    Gallery("gallery", "Galerie", Icons.Default.PhotoLibrary),
    Sequences("sequences", "Séquences", Icons.Default.Timer),
    Device("device", "Appareil", Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonRemoteApp() {
    val state by CameraRepository.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { CameraRepository.messages.collect { snackbar.showSnackbar(it) } }

    var showBle by rememberSaveable { mutableStateOf(false) }
    if (showBle) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            Box(Modifier.padding(bottom = pad.calculateBottomPadding())) { BluetoothScreen(onBack = { showBle = false }) }
        }
        return
    }

    val connected = state as? ConnState.Connected
    if (connected == null) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
            ConnectScreen(state, onBluetooth = { showBle = true }, modifier = Modifier.padding(pad))
        }
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val status by CameraRepository.status.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Text(
                        connected.info.productName.ifBlank { "Appareil Canon" },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    status.batteryLevel?.let { lvl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    lvl >= 75 -> Icons.Default.BatteryFull
                                    lvl >= 40 -> Icons.Default.Battery5Bar
                                    else -> Icons.Default.Battery2Bar
                                }, "Batterie",
                                tint = if (lvl < 25) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text("$lvl%", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    IconButton(onClick = { showBle = true }) {
                        Icon(Icons.Default.Bluetooth, "Télécommande Bluetooth")
                    }
                    IconButton(onClick = { CameraRepository.disconnect() }) {
                        Icon(Icons.Default.LinkOff, "Déconnecter")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.Remote.route, modifier = Modifier.padding(pad)) {
            composable(Tab.Remote.route) { RemoteScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(Tab.Gallery.route) { GalleryScreen() }
            composable(Tab.Sequences.route) { SequenceScreen() }
            composable(Tab.Device.route) { DeviceScreen() }
        }
    }
}
