package lt.setkus.pliuskis.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * The ViewModels of one SwiftUI screen, kept in an androidx [ViewModelStore] (Swift: `ScreenViewModels`). The Swift
 * class `IosViewModelStoreOwner` in `iosApp` holds one per screen and calls [clear] when the screen goes away, which
 * runs each ViewModel's `onCleared` once. Only the store can do that: `ViewModel.clear()` is internal to the library.
 */
class ScreenViewModels : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    /** The ViewModel stored under [key], or null when there is none. */
    fun find(key: String): ViewModel? = viewModelStore[key]

    /** Stores [viewModel] under [key]. A different ViewModel stored under the same key is cleared. */
    fun put(key: String, viewModel: ViewModel) {
        viewModelStore.put(key, viewModel)
    }

    /** Clears and removes every stored ViewModel. Call it once, when the screen goes away. */
    fun clear() {
        viewModelStore.clear()
    }
}
