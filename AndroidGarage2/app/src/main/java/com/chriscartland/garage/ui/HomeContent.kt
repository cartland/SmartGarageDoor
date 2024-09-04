package com.chriscartland.garage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.Result
import com.chriscartland.garage.repository.dataOrNull
import com.chriscartland.garage.viewmodel.HomeViewModel

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState()
    val recentDoorEvents = viewModel.recentDoorEvents.collectAsState()
    HomeContent(
        currentDoorEvent = currentDoorEvent.value,
        recentDoorEvents = recentDoorEvents.value,
        modifier = modifier,
        onFetchCurrentDoorEvent = { viewModel.fetchCurrentDoorEvent() },
        onFetchRecentDoorEvents = { viewModel.fetchRecentDoorEvents() },
    )
}

@Composable
fun HomeContent(
    currentDoorEvent: Result<DoorEvent?>,
    recentDoorEvents: Result<List<DoorEvent>?>,
    modifier: Modifier = Modifier,
    onFetchCurrentDoorEvent: () -> Unit = {},
    onFetchRecentDoorEvents: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CurrentEventCard(
                currentDoorEvent = currentDoorEvent,
                modifier = Modifier,
                onFetchCurrentDoorEvent = onFetchCurrentDoorEvent,
            )
        }

        when (recentDoorEvents) {
            is Result.Error ->
                item {
                    Box(modifier = Modifier.clickable { onFetchRecentDoorEvents() }) {
                        Text(
                            text = recentDoorEvents.dataOrNull<List<DoorEvent>?>().toString(),
                        )
                    }
                }
            is Result.Loading -> {
                item {
                    Text(text = "Loading...")
                }
                items(recentDoorEvents.dataOrNull() ?: emptyList()) { item ->
                    Box(modifier = Modifier.clickable { onFetchRecentDoorEvents() }) {
                        RecentDoorEventListItem(item)
                    }
                }
            }
            is Result.Success -> {
                items(recentDoorEvents.dataOrNull() ?: emptyList()) { item ->
                    Box(modifier = Modifier.clickable { onFetchRecentDoorEvents() }) {
                        RecentDoorEventListItem(item)
                    }
                }
            }
        }
    }
}


@Composable
fun CurrentEventCard(
    currentDoorEvent: Result<DoorEvent?>,
    modifier: Modifier = Modifier,
    onFetchCurrentDoorEvent: () -> Unit = {},
) {
    when (currentDoorEvent) {
        is Result.Error ->
            Box(modifier = modifier.clickable { onFetchCurrentDoorEvent() }) {
                Column {
                    Text(text = "Error")
                    Text(text = currentDoorEvent.toString())
                }
            }
        is Result.Loading ->
            Box(modifier = modifier.clickable { onFetchCurrentDoorEvent() }) {
                Column {
                    Text(text = "Loading...")
                    currentDoorEvent.dataOrNull()?.let { doorEvent ->
                        DoorStatusCard(doorEvent)
                    }
                }
            }
        is Result.Success ->
            Box(modifier = modifier.clickable { onFetchCurrentDoorEvent() }) {
                DoorStatusCard(currentDoorEvent.dataOrNull())
            }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeContentPreview() {
    HomeContent(
        currentDoorEvent = Result.Success(demoDoorEvents.firstOrNull()),
        recentDoorEvents = Result.Success(demoDoorEvents),
        modifier = Modifier,
        onFetchCurrentDoorEvent = {},
        onFetchRecentDoorEvents = {},
    )
}

@Preview(showBackground = true)
@Composable
fun CurrentEventCardSuccessPreview() {
    CurrentEventCard(
        currentDoorEvent = Result.Success(demoDoorEvents.firstOrNull()),
    )
}

@Preview(showBackground = true)
@Composable
fun CurrentEventCardLoadingPreview() {
    CurrentEventCard(
        currentDoorEvent = Result.Loading(demoDoorEvents[1]),
    )
}

@Preview(showBackground = true)
@Composable
fun CurrentEventCardErrorPreview() {
    CurrentEventCard(
        currentDoorEvent = Result.Error(Exception("Error")),
    )
}
