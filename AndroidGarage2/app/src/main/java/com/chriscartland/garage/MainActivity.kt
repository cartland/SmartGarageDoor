package com.chriscartland.garage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chriscartland.garage.auth.AuthViewModelImpl
import com.chriscartland.garage.repository.FirebaseAuthRepository.Companion.RC_ONE_TAP_SIGN_IN
import com.chriscartland.garage.ui.GarageApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Edge-to-edge required on Android 15+ (target SDK 35).
        setContent {
            GarageApp()
        }
    }

    // TODO: Migrate away from onActivityResult with Activity Result API and ActivityResultContract.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("MainActivity", "onActivityResult")
        when (requestCode) {
            RC_ONE_TAP_SIGN_IN -> {
                Log.d("MainActivity", "RC_ONE_TAP_SIGN_IN")
                if (data == null) {
                    Log.e("MainActivity", "onActivityResult: data is null")
                    return
                }
                val authViewModel: AuthViewModelImpl by viewModels()
                authViewModel.processGoogleSignInResult(data)
            }
        }
    }
}
