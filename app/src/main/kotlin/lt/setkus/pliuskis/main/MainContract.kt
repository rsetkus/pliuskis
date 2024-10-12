package lt.setkus.pliuskis.main

import lt.setkus.pliuskis.viewmodel.UiIntent
import lt.setkus.pliuskis.viewmodel.UiState

class MainContract {
    sealed class Intent : UiIntent {
        data object ConnectToBroker : Intent()
    }

    sealed class MainScreenState {
        data object Loading : MainScreenState()
        data object Connected : MainScreenState()
    }

    data class State(val state: MainScreenState): UiState
}