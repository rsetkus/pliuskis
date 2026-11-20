import Shared
import SwiftUI
import UIKit
import XCTest
@testable import iosApp

/// STORY-1, AC5: a SwiftUI screen owns its shared ViewModel through `IosViewModelStoreOwner`. When the screen leaves
/// the view hierarchy, the owner clears its store, and the ViewModel's `onCleared` runs exactly once.
final class Story1ViewModelStoreOwnerTests: XCTestCase {

    @MainActor
    func testStory1_removingScreenClearsItsViewModelOnce() {
        let counter = ClearedCounter()
        let window = UIWindow(frame: UIScreen.main.bounds)
        let host = UIHostingController(rootView: AnyView(Story1OwnerScreen(counter: counter)))
        window.rootViewController = host
        window.makeKeyAndVisible()
        host.view.layoutIfNeeded()
        spinMainRunLoop(seconds: 0.2)

        XCTAssertEqual(counter.count, 0, "onCleared ran while the screen was still shown")

        host.rootView = AnyView(EmptyView())
        host.view.layoutIfNeeded()
        waitUntil(seconds: 2) { counter.count > 0 }
        spinMainRunLoop(seconds: 0.2)

        XCTAssertEqual(counter.count, 1)
    }

    @MainActor
    func testStory1_releasingOwnerClearsEveryViewModelOnce() {
        let counter = ClearedCounter()
        var owner: IosViewModelStoreOwner? = IosViewModelStoreOwner()
        let first: CountingViewModel? = owner?.viewModel(key: "first") { CountingViewModel(counter: counter) }
        let second: CountingViewModel? = owner?.viewModel(key: "second") { CountingViewModel(counter: counter) }
        XCTAssertNotNil(first)
        XCTAssertNotNil(second)
        XCTAssertEqual(counter.count, 0)

        owner = nil

        XCTAssertEqual(counter.count, 2)
    }

    @MainActor
    private func spinMainRunLoop(seconds: TimeInterval) {
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    }

    @MainActor
    private func waitUntil(seconds: TimeInterval, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(seconds)
        while !condition() && Date() < deadline {
            spinMainRunLoop(seconds: 0.05)
        }
    }
}

/// Counts `onCleared` calls across ViewModels.
private final class ClearedCounter {
    var count = 0
}

/// A shared (Kotlin) ViewModel subclassed in Swift, so the test sees when the store clears it.
private final class CountingViewModel: Lifecycle_viewmodelViewModel {
    private let counter: ClearedCounter

    init(counter: ClearedCounter) {
        self.counter = counter
        super.init()
    }

    override func onCleared() {
        counter.count += 1
        super.onCleared()
    }
}

/// A screen that holds one ViewModel through the bridge, the way the app's screens do.
private struct Story1OwnerScreen: View {
    @StateObject private var owner = IosViewModelStoreOwner()
    let counter: ClearedCounter

    var body: some View {
        let viewModel: CountingViewModel = owner.viewModel(key: "story1") { CountingViewModel(counter: counter) }
        Text(String(describing: type(of: viewModel)))
    }
}
