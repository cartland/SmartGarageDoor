package com.chriscartland.garage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
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

    when (currentDoorEvent.value) {
        is Result.Error -> HomeError(currentDoorEvent.value as Result.Error)
        is Result.Loading ->
            HomeLoading(
                currentDoorEvent = currentDoorEvent.value.dataOrNull(),
                modifier = modifier,
            )
        is Result.Success ->
            HomeContent(
                currentDoorEvent = currentDoorEvent.value.dataOrNull(),
                modifier = modifier,
            )
    }
}

@Composable
fun HomeError(
    error: Result.Error,
    modifier: Modifier = Modifier,
) {
    Text(text = error.toString())
}

@Composable
fun HomeLoading(
    currentDoorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
) {
    Column {
        Text(text = "Loading...")
        DoorStatusCard(currentDoorEvent, modifier = modifier)
    }
}

@Composable
fun HomeContent(
    currentDoorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
) {
    DoorStatusCard(currentDoorEvent, modifier = modifier)
}
