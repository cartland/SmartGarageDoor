package com.chriscartland.garage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.chriscartland.garage.fcm.updateOpenDoorFcmSubscription
import com.chriscartland.garage.repository.FirebaseAuthRepository.Companion.RC_ONE_TAP_SIGN_IN
import com.chriscartland.garage.repository.FirebaseAuthRepository.Companion.TAG
import com.chriscartland.garage.ui.GarageApp
import com.chriscartland.garage.viewmodel.DoorViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DoorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val doorViewModel: DoorViewModel by viewModels()
        viewModel = doorViewModel
        enableEdgeToEdge()
        setContent {
            GarageApp()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val buildTimestamp = viewModel.fetchBuildTimestampCached()
            if (buildTimestamp == null) {
                Log.e("MainActivity", "Failed to register for FCM updates")
                return@launch
            }
            updateOpenDoorFcmSubscription(this@MainActivity, buildTimestamp)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult")
        when (requestCode) {
            RC_ONE_TAP_SIGN_IN -> {
                viewModel.handleSignIn(this, data)
            }
        }
    }
}
