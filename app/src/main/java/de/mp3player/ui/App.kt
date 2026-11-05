package de.mp3player.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.mp3player.data.GroupType
import de.mp3player.data.Song
import de.mp3player.data.tags.TagChanges
import kotlinx.coroutines.launch

@Composable
fun Mp3PlayerApp() {
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissions = remember {
        buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    fun isGranted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted(audioPermission)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = isGranted(audioPermission)
        asked = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isGranted(audioPermission) }
    LaunchedEffect(Unit) {
        if (!asked && permissions.any { !isGranted(it) }) launcher.launch(permissions)
    }
    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }

    if (granted) {
        MainScaffold(vm)
    } else {
        PermissionScreen(
            asked = asked,
            onRequest = { launcher.launch(permissions) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}

@Composable
private fun PermissionScreen(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.LibraryMusic, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("Zugriff auf deine Musik", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Damit der Player deine MP3s findet und nach Künstler, Album und Genre sortieren kann, " +
                    "braucht er Zugriff auf die Audiodateien auf diesem Gerät.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequest) { Text("Zugriff erlauben") }
            if (asked) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSettings) { Text("App-Einstellungen öffnen") }
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var tab by rememberSaveable { mutableStateOf(HomeTab.SONGS) }
    val snackbar = remember { SnackbarHostState() }
    val library by vm.library.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var addSongs by remember { mutableStateOf<List<Song>?>(null) }
    var editSongs by remember { mutableStateOf<List<Song>?>(null) }
    var legacyEdit by remember { mutableStateOf<Pair<List<Song>, TagChanges>?>(null) }
    val context = LocalContext.current

    // Systemdialog „Darf MP3 Player diese Audiodateien ändern?“
    val writeApproval by vm.writeApproval.collectAsStateWithLifecycle()
    val approvalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.onWriteApprovalResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(writeApproval) {
        writeApproval?.let { approvalLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    // Android 8/9: Schreibberechtigung für den Speicher
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        legacyEdit?.let { (songs, changes) -> if (ok) vm.editTags(songs, changes) }
        legacyEdit = null
    }

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(msg) }
        }
    }

    val actions = remember(vm, nav) {
        UiActions(
            vm = vm,
            openGroup = { type, key -> nav.navigate("group/${type.name}/${Uri.encode(key)}") },
            openPlaylist = { id -> nav.navigate("playlist/$id") },
            openPlayer = { nav.navigate("player") { launchSingleTop = true } },
            addToPlaylist = { songs -> addSongs = songs },
            editTags = { songs -> editSongs = songs },
            back = { nav.popBackStack() },
        )
    }
    val currentSong = playerState.currentId?.let { library.songsById[it] }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route != "player") {
                Column {
                    if (currentSong != null) MiniPlayer(currentSong, playerState, actions)
                    if (route == "home") {
                        NavigationBar {
                            HomeTab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = t == tab,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label, maxLines = 1) },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(tab, actions) }
            composable("group/{type}/{key}") { e ->
                val type = runCatching { GroupType.valueOf(e.arguments?.getString("type").orEmpty()) }
                    .getOrDefault(GroupType.ARTIST)
                GroupDetailScreen(type, e.arguments?.getString("key").orEmpty(), actions)
            }
            composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PlaylistDetailScreen(e.arguments?.getLong("id") ?: -1L, actions)
            }
            composable("player") { PlayerScreen(actions) }
        }
    }

    editSongs?.let { songs ->
        TagEditDialog(
            songs = songs,
            genres = library.genres.map { it.title }.filter { it != de.mp3player.data.MusicRepository.UNKNOWN_GENRE },
            onDismiss = { editSongs = null },
        ) { changes ->
            editSongs = null
            val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            if (needsLegacyPermission) {
                legacyEdit = songs to changes
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                vm.editTags(songs, changes)
            }
        }
    }

    addSongs?.let { songs ->
        AddToPlaylistDialog(
            songs = songs,
            playlists = playlists,
            onDismiss = { addSongs = null },
            onPick = { id -> vm.addToPlaylist(id, songs); addSongs = null },
            onCreate = { name -> vm.createPlaylist(name, songs); addSongs = null },
        )
    }
}
