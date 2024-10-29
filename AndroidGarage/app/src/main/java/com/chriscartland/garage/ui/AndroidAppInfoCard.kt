package com.chriscartland.garage.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chriscartland.garage.version.AppVersion

@Composable
fun AndroidAppInfoCard() {
    AndroidAppInfoCard(with(LocalContext.current) {
        AppVersion(
            packageName = packageName,
            versionCode = packageManager.getPackageInfo(
                packageName,
                0
            ).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            },
            versionName = packageManager.getPackageInfo(packageName, 0).versionName,
        )
    })
}

@Composable
fun AndroidAppInfoCard(appVersion: AppVersion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Android App Information", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Package name: ${appVersion.packageName}",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "Version name: ${appVersion.versionName}",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "Version code: ${appVersion.versionCode}",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Preview
@Composable
fun AndroidAppInfoCardPreview() {
    AndroidAppInfoCard(
        AppVersion(
            packageName = "com.example",
            versionCode = 1L,
            versionName = "1.0.0 20241028.095244"
        )
    )
}
