package lt.setkus.pliuskis.main

import lt.setkus.pliuskis.viewmodel.UiEffect
import lt.setkus.pliuskis.viewmodel.UiIntent
import lt.setkus.pliuskis.viewmodel.UiState

class MainContract {
    sealed class Intent : UiIntent {
        data object RequestSystemUpdate : Intent()
    }

    sealed class MainScreenState {
        object EmptyScreen : MainScreenState()
        data class SystemStatus(val status: String): MainScreenState()
        data class ErrorScreen(val error: String): MainScreenState()
    }

    data class State(val state: MainScreenState): UiState

    sealed class Effect : UiEffect {
        data object Loading : Effect()
        data object Loaded : Effect()
    }
}