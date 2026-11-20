import Combine
import Shared

/// Owns the shared (Kotlin) ViewModels of one SwiftUI screen, the iOS counterpart of a `ViewModelStoreOwner`.
///
/// A screen holds it with `@StateObject` and gets each ViewModel through `viewModel(key:factory:)`, so the same
/// instance survives re-renders. When the screen leaves the view hierarchy, SwiftUI releases the owner, and `deinit`
/// clears the Kotlin `ViewModelStore`: each ViewModel's `onCleared` runs once and its `viewModelScope` is cancelled.
final class IosViewModelStoreOwner: ObservableObject {
    private let viewModels = ScreenViewModels()

    /// The ViewModel stored under `key`; `factory` creates and stores it on the first call.
    func viewModel<T: Lifecycle_viewmodelViewModel>(key: String, factory: () -> T) -> T {
        let existing = viewModels.find(key: key) as? T
        let viewModel = existing ?? factory()
        if existing == nil {
            viewModels.put(key: key, viewModel: viewModel)
        }
        return viewModel
    }

    deinit {
        viewModels.clear()
    }
}
