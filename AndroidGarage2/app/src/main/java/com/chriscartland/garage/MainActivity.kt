package com.chriscartland.garage

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chriscartland.garage.fcm.updateOpenDoorFcmSubscription
import com.chriscartland.garage.ui.GarageApp
import com.chriscartland.garage.viewmodel.DoorViewModel
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
        val doorViewModel: DoorViewModel by viewModels()
        lifecycleScope.launch(Dispatchers.IO) {
            val buildTimestamp = doorViewModel.buildTimestamp()
            if (buildTimestamp == null) {
                Log.e("MainActivity", "Failed to register for FCM updates")
                return@launch
            }
            updateOpenDoorFcmSubscription(this@MainActivity, buildTimestamp)
        }
    }
}
