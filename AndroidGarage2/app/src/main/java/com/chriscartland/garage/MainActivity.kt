package com.chriscartland.garage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.chriscartland.garage.fcm.updateOpenDoorFcmSubscription
import com.chriscartland.garage.ui.GarageApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GarageApp()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            updateOpenDoorFcmSubscription(this@MainActivity, APP_CONFIG.buildTimestamp)
        }
    }
}
