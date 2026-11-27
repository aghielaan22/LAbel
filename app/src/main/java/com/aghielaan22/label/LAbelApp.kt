package com.aghielaan22.label

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.filled.Groups

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Studio("Studio", Icons.Default.Groups),
    Library("Library", Icons.Default.Favorite)
}

@Composable
fun LAbelApp(
    openPlayerFromWidget: Boolean = false,
    onWidgetRequestHandled: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    val tabState = rememberSaveableStateHolder()
    val search = rememberSaveable(saver = SearchTabState.Saver) { SearchTabState() }

    LaunchedEffect(openPlayerFromWidget) {
        if (openPlayerFromWidget) {
            val hasSong = withTimeoutOrNull(5_000L) {
                snapshotFlow { PlayerController.currentSong != null }.first { it }
            }
            if (hasSong == true) showPlayer = true
            onWidgetRequestHandled()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val removal = PlayerController.pendingRemoval
    LaunchedEffect(removal) {
        if (removal != null) {
            val result = snackbar.showSnackbar(
                "Removed from queue", actionLabel = "Undo", withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            PlayerController.finishQueueRemoval(removal, undo = result == SnackbarResult.ActionPerformed)
        }
    }
    val glass = rememberGlassState()
    var dockHeightPx by remember { mutableIntStateOf(0) }
    val dockHeight = with(LocalDensity.current) { dockHeightPx.toDp() }
    val tabsAlpha by animateFloatAsState(
        targetValue = if (Navigator.current != null) 0f else 1f,
        animationSpec = tween(280),
        label = "tabsAlpha"
    )

    val context = LocalContext.current
    var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(context)) }
    crashReport?.let { report ->
        CrashReportDialog(
            report = report,
            onDismiss = {
                CrashReporter.clear(context)
                crashReport = null

            }
        )
    }
    LaunchedEffect(Unit) {
        if (UpdateChecker.autoCheck) UpdateChecker.check(context, manual = false)
    }
    UpdateChecker.dialogRelease?.let { UpdateDialog(it) }

    CompositionLocalProvider(LocalGlassState provides glass) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Layer 1: the backdrop. This is the only thing glass panels blur.
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource(glass),
                color = MaterialTheme.colorScheme.background
            ) {
                AuroraBackground()
            }

            // Keep backdrop sampling on the dock and player, off scrolling screens.
            CompositionLocalProvider(
                LocalGlassState provides null,
                LocalContentColor provides MaterialTheme.colorScheme.onBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = dockHeight)
                ) {
                    AnimatedVisibility(
                        visible = !NetworkMonitor.isOnline,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OfflineBanner()
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabsAlpha }
                        ) {
                            tabState.SaveableStateProvider(selectedTab.name) {
                                when (selectedTab) {
                                    Tab.Home -> HomeScreen()
                                    Tab.Search -> SearchScreen(search)
                                    Tab.Studio -> StudioScreen()
                                    Tab.Library -> LibraryScreen()
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = Navigator.current,
                            transitionSpec = {
                                (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith
                                        (slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)))
                            },
                            label = "pages"
                        ) { page ->
                            // The empty pointerInput stops touches reaching the hidden tab below
                            val pageModifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {}

                            when (page) {
                                is DetailPage.AlbumPage -> Surface(modifier = pageModifier) {
                                    AlbumScreen(page.album, onBack = { Navigator.back() })
                                }

                                is DetailPage.ArtistPage -> Surface(modifier = pageModifier) {
                                    ArtistScreen(page.artist, onBack = { Navigator.back() })
                                }

                                DetailPage.SettingsPage -> Surface(modifier = pageModifier) {
                                    SettingsScreen(onBack = { Navigator.back() })
                                }

                                DetailPage.SoundPage -> Surface(modifier = pageModifier) {
                                    SoundScreen(onBack = { Navigator.back() })
                                }
                                DetailPage.StatsPage -> Surface(modifier = pageModifier) {
                                    StatsScreen(onBack = { Navigator.back() })
                                }

                                DetailPage.ImportPage -> Surface(modifier = pageModifier) {
                                    ImportScreen(onBack = { Navigator.back() })
                                }

                                null -> Unit
                            }
                        }
                    }
                }
            }

            // Layer 3: the floating dock
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { dockHeightPx = it.height }
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    if (!showPlayer) PlaybackRecoveryNotice()
                    MiniPlayer(onClick = { showPlayer = true })
                    GlassNavBar(
                        selected = selectedTab,
                        onSelect = {
                            selectedTab = it
                            Navigator.clear()
                        }
                    )
                }
            }

            // Layer 4: the full player
            val playerVisible = showPlayer && PlayerController.currentSong != null
            AnimatedVisibility(
                visible = playerVisible,
                enter = slideInVertically(tween(380)) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(220))
            ) {
                NowPlayingScreen(visible = playerVisible, onClose = { showPlayer = false })
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding().padding(bottom = if (showPlayer) 8.dp else dockHeight)
            )
        }
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = "You're offline. Search and new songs need an internet connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
    }
}