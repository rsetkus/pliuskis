package lt.setkus.pliuskis.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.systemstate.CommandGetSystemStateLevelUseCase
import lt.setkus.pliuskis.main.MainContract.Intent
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Loading
import lt.setkus.pliuskis.main.MainContract.MainScreenState.SystemStatus
import lt.setkus.pliuskis.main.MainContract.State
import lt.setkus.pliuskis.viewmodel.BaseViewModel
import timber.log.Timber

class MainActivityViewModel(
    private val useCase: CommandGetSystemStateLevelUseCase
) : BaseViewModel<Intent, State>() {

    override fun setInitialState() = State(Loading)

    override fun handleIntents(it: Intent) {
        viewModelScope.launch {
            useCase.invoke(Unit)
                .onStart { Timber.d("Started") }
                .catch { e: Throwable -> Timber.d("error: $e") }
                .collect { result ->
                    setState {
                        copy(state = SystemStatus(result))
                    }
                }
        }
    }
}