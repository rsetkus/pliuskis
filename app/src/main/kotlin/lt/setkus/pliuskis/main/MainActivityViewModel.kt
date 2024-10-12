package lt.setkus.pliuskis.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.main.MainContract.Intent
import lt.setkus.pliuskis.main.MainContract.Intent.ConnectToBroker
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Connected
import lt.setkus.pliuskis.main.MainContract.MainScreenState.Loading
import lt.setkus.pliuskis.main.MainContract.State
import lt.setkus.pliuskis.viewmodel.BaseViewModel
import timber.log.Timber

class MainActivityViewModel(
    private val connectUseCase: ConnectUseCase,
) : BaseViewModel<Intent, State>() {

    override fun setInitialState() = State(Loading)

    override fun handleIntents(it: Intent) = when (it) {
        is ConnectToBroker -> handleConnectToBroker()
    }

    private fun handleConnectToBroker() {
        viewModelScope.launch {
            connectUseCase.invoke(Unit)
                .collect { result ->
                    result.fold(
                        ifRight = ::renderSystemStatus,
                        ifLeft = { Timber.d("error: $it") }
                    )
                }
        }
    }

    private fun renderSystemStatus(unit: String) {
        setState {
            copy(state = Connected)
        }
    }
}