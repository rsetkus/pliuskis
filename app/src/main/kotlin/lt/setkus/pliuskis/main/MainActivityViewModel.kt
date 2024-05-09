package lt.setkus.pliuskis.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onErrorReturn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.systemstate.CommandGetSystemStateLevelUseCase
import lt.setkus.pliuskis.main.MainContract.Effect
import lt.setkus.pliuskis.main.MainContract.Intent
import lt.setkus.pliuskis.main.MainContract.MainScreenState.EmptyScreen
import lt.setkus.pliuskis.main.MainContract.State
import lt.setkus.pliuskis.viewmodel.BaseViewModel
import timber.log.Timber

class MainActivityViewModel(
    private val useCase: CommandGetSystemStateLevelUseCase
) : BaseViewModel<Intent, State, Effect>() {

    override fun setInitialState() = State(EmptyScreen)

    override fun handleIntents(it: Intent) {
        viewModelScope.launch {
            useCase.invoke(Unit)
                .onStart { Timber.d("Started") }
                .catch { e: Throwable -> Timber.d("error: $e") }
                .collect {
                    Timber.d("Collected: $it")
                }
        }
    }
}