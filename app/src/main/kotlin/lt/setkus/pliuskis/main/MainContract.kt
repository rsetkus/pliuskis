package lt.setkus.pliuskis.main

import lt.setkus.pliuskis.viewmodel.UiIntent
import lt.setkus.pliuskis.viewmodel.UiState

class MainContract {
    sealed class Intent : UiIntent {
        data object RequestSystemUpdate : Intent()
    }

    sealed class MainScreenState {
        object Loading : MainScreenState()
        data class SystemStatus(val status: String): MainScreenState()
        data class ErrorScreen(val error: String): MainScreenState()
    }

    data class State(val state: MainScreenState): UiState
}