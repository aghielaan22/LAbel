package com.aghielaan22.label

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aghielaan22.label.ui.theme.LAbelTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private var openPlayerFromWidget by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean("widget_player_pending") == true) openPlayerFromWidget = true
        handleWidgetIntent(intent)
        CrashReporter.install(this)
        enableEdgeToEdge()
        LibraryRepository.init(this)
        HistoryRepository.init(this)
        PlaylistRepository.init(this)
        SearchHistoryRepository.init(this)
        PlaybackSettings.init(this)
        AppSettings.init(this)
        NetworkMonitor.start(this)
        SoundEngine.init(this)
        StatsRepository.init(this)
        ListenBrainz.init(this)
        UpdateChecker.init(this)
        StudioController.init(this)
        PlayerController.connect(this)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val theme = AppSettings.appTheme
            val dark = theme.amoled || when (AppSettings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Keep the clock and battery icons readable on the chosen theme
            val view = LocalView.current
            SideEffect {
                val insets = WindowCompat.getInsetsController(window, view)
                insets.isAppearanceLightStatusBars = !dark
                insets.isAppearanceLightNavigationBars = !dark
            }

            LAbelAppTheme(darkTheme = dark) {
                LAbelApp(
                    openPlayerFromWidget = openPlayerFromWidget,
                    onWidgetRequestHandled = { openPlayerFromWidget = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent) {
        if (intent.action == NowPlayingWidget.OPEN_PLAYER) {
            openPlayerFromWidget = true
            intent.action = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("widget_player_pending", openPlayerFromWidget)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        PlayerController.savePlayback()
        super.onStop()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LAbelTheme {
        Greeting("Android")
    }
}