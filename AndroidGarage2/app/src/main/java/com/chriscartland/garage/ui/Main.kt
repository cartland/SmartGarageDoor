package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chriscartland.garage.ui.theme.AppTheme

@Preview
@Composable
fun GarageApp() {
    AppTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
            Text(
                "Garage",
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
