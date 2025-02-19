package lt.setkus.pliuskis.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.main.MainActivityUiState.Connected
import lt.setkus.pliuskis.main.MainActivityUiState.Error
import lt.setkus.pliuskis.main.MainActivityUiState.Loading

class MainActivityViewModel(
    private val connectUseCase: ConnectUseCase,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> = connectUseCase(Unit)
        .map { either ->
            either.fold(
                ifRight = { Connected },
                ifLeft = { Error }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Loading
        )
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState
    data object Error : MainActivityUiState
    data object Connected : MainActivityUiState
}