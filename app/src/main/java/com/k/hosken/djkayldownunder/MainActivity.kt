package com.k.hosken.djkayldownunder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Required on Android 13+ for the playback notification (and therefore the
        // lock-screen media controls) to be allowed to show at all.
        requestNotificationPermissionIfNeeded()

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
}
