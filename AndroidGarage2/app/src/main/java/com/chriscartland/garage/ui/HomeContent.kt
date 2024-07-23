package com.chriscartland.garage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.chriscartland.garage.model.DoorEvent
import com.chriscartland.garage.repository.dataOrNull
import com.chriscartland.garage.viewmodel.HomeViewModel

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val currentDoorEvent = viewModel.currentDoorEvent.collectAsState().value.dataOrNull()
    HomeContent(
        currentDoorEvent = currentDoorEvent,
        modifier = modifier,
    )
}

@Composable
fun HomeContent(
    currentDoorEvent: DoorEvent?,
    modifier: Modifier = Modifier,
) {
    DoorStatusCard(currentDoorEvent, modifier = modifier)
}
