package lt.setkus.pliuskis.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<in I: UiIntent, S: UiState, E: UiEffect> : ViewModel() {

    private val initialState: S by lazy { setInitialState() }
    abstract fun setInitialState(): S

    private val _viewState: MutableLiveData<S> = MutableLiveData(initialState)
    val viewState: LiveData<S> = _viewState

    private val _intent: MutableSharedFlow<I> = MutableSharedFlow()

    private val _effect: Channel<E> = Channel()
    val effect = _effect.receiveAsFlow()

    init {
        subscribeToEvents()
    }

    fun setIntent(intent: I) = viewModelScope.launch { _intent.emit(intent) }

    private fun subscribeToEvents() {
        viewModelScope.launch {
            _intent.collect {
                handleIntents(it)
            }
        }
    }

    protected abstract fun handleIntents(it: I)

    protected fun setState(reducer: S.() -> S) {
        viewState.value?.let {
            val newState = it.reducer()
            _viewState.value = newState
        }
    }

    protected fun setEffect(builder: () -> E) {
        val effectValue = builder()
        viewModelScope.launch { _effect.send(effectValue) }
    }
}