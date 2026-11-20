package com.aghielaan22.label

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Search("Search", Icons.Default.Search),
    Library("Library", Icons.Default.Favorite)
}

@Composable
fun LAbelApp() {
    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var showPlayer by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(onClick = { showPlayer = true })
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    Navigator.clear()
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
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
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(250),
                        label = "tabs"
                    ) { tab ->
                        when (tab) {
                            Tab.Home -> HomeScreen()
                            Tab.Search -> SearchScreen()
                            Tab.Library -> LibraryScreen()
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
                        when (page) {
                            is DetailPage.AlbumPage -> Surface(modifier = Modifier.fillMaxSize()) {
                                AlbumScreen(page.album, onBack = { Navigator.back() })
                            }

                            is DetailPage.ArtistPage -> Surface(modifier = Modifier.fillMaxSize()) {
                                ArtistScreen(page.artist, onBack = { Navigator.back() })
                            }

                            DetailPage.SettingsPage -> Surface(modifier = Modifier.fillMaxSize()) {
                                SettingsScreen(onBack = { Navigator.back() })
                            }

                            null -> Unit
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && PlayerController.currentSong != null,
            enter = slideInVertically(tween(380)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(220))
        ) {
            NowPlayingScreen(onClose = { showPlayer = false })
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