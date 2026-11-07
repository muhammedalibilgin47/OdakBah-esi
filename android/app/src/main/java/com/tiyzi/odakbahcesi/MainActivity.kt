package com.tiyzi.odakbahcesi

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tiyzi.odakbahcesi.notify.Notifier
import com.tiyzi.odakbahcesi.ui.App
import com.tiyzi.odakbahcesi.ui.AppTheme
import com.tiyzi.odakbahcesi.ui.FocusTheme

class MainActivity : ComponentActivity() {

    private val vm: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        setContent {
            val profile by vm.profile.collectAsState()
            FocusTheme(AppTheme.of(profile.theme)) {
                App(vm)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_FOCUS, false) == true) {
            intent.removeExtra(EXTRA_START_FOCUS)
            vm.startFocusFromTile()
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppReturned()
    }

    override fun onStop() {
        super.onStop()
        val screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: false
        if (!isChangingConfigurations && screenOn) {
            vm.onAppLeft()
        }
    }

    companion object {
        const val EXTRA_START_FOCUS = "start_focus"
    }
}
