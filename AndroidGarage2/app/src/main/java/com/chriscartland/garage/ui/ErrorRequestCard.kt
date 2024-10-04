package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ErrorRequestCard(
    text: String,
    buttonText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier,
        colors = CardColors(
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = onClick) {
                Text(
                    buttonText,
                    maxLines = 2,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}
