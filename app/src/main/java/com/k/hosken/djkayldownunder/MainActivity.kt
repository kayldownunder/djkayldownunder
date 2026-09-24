package com.k.hosken.djkayldownunder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k.hosken.djkayldownunder.navigation.AppNavHost
import com.k.hosken.djkayldownunder.ui.FontPreferencesViewModel
import com.k.hosken.djkayldownunder.ui.theme.DJKaylTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied - if denied, playback still works, just without the
           notification / lock-screen controls, so there's nothing else to do here. */ }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Granted or denied via the system dialog - either way there's nothing else to do
           here; if denied, background playback just remains subject to the OS killing the
           process while backgrounded, same as before this request existed. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required on Android 13+ for the playback notification (and therefore the
        // lock-screen media controls) to be allowed to show at all.
        requestNotificationPermissionIfNeeded()

        // Without this, Android (Samsung's aggressive background-app management especially)
        // can kill MusicPlaybackService's process while the app is backgrounded, which stops
        // playback outright and resets in-memory UI state such as isShuffleAllActive back to
        // its default - confirmed via on-device logcat investigation of exactly this symptom.
        requestBatteryOptimizationExemptionIfNeeded()

        setContent {
            val fontPreferencesViewModel: FontPreferencesViewModel = viewModel()
            val fontPrefs by fontPreferencesViewModel.fontPrefs.collectAsState()
            DJKaylTheme(fontPrefs = fontPrefs) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        // Some OEM builds/ROMs omit this settings screen entirely; don't crash if so - the
        // app just stays subject to normal background battery management in that case.
        if (intent.resolveActivity(packageManager) != null) {
            batteryOptimizationLauncher.launch(intent)
        }
    }
}
