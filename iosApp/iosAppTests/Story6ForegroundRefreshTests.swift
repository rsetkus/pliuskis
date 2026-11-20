import SwiftUI
import XCTest
@testable import iosApp

/// STORY-6, AC36 (Swift side): `ForegroundRefresh` maps SwiftUI's `scenePhase` changes to the visible screen's
/// refresh (`viewModel.refresh()` of the shared ViewModel). It refreshes once each time the app comes back from the
/// background, and not on the first launch or on a short `.inactive` trip (Control Center, an alert), which never left
/// the foreground. Coming back from the background passes through `.inactive` on its way to `.active`, and so does
/// Device > Lock. The Kotlin side (connect only when needed, the status request, no second water command) is tested in
/// `shared/src/iosTest` (`Story6ForegroundReconnectTest`, `Story6WaterNotRepeatedTest`).
final class Story6ForegroundRefreshTests: XCTestCase {

    func testStory6_returningFromBackgroundRefreshesOnce() {
        var refreshes = 0
        var target = ForegroundRefresh { refreshes += 1 }

        target.scenePhaseChanged(to: .active)
        target.scenePhaseChanged(to: .inactive)
        target.scenePhaseChanged(to: .background)
        XCTAssertEqual(refreshes, 0, "refreshed before the app came back")

        target.scenePhaseChanged(to: .inactive)
        target.scenePhaseChanged(to: .active)

        XCTAssertEqual(refreshes, 1)
    }

    func testStory6_backgroundStraightToActiveRefreshesOnce() {
        var refreshes = 0
        var target = ForegroundRefresh { refreshes += 1 }

        target.scenePhaseChanged(to: .active)
        target.scenePhaseChanged(to: .background)
        target.scenePhaseChanged(to: .active)

        XCTAssertEqual(refreshes, 1)
    }

    func testStory6_firstLaunchDoesNotRefresh() {
        var refreshes = 0
        var target = ForegroundRefresh { refreshes += 1 }

        target.scenePhaseChanged(to: .inactive)
        target.scenePhaseChanged(to: .active)

        XCTAssertEqual(refreshes, 0, "the screen loads its data itself on first launch")
    }

    func testStory6_inactiveTripWithoutBackgroundDoesNotRefresh() {
        var refreshes = 0
        var target = ForegroundRefresh { refreshes += 1 }

        target.scenePhaseChanged(to: .active)
        target.scenePhaseChanged(to: .inactive)
        target.scenePhaseChanged(to: .active)

        XCTAssertEqual(refreshes, 0)
    }

    func testStory6_everyReturnFromBackgroundRefreshesOnceEach() {
        var refreshes = 0
        var target = ForegroundRefresh { refreshes += 1 }
        target.scenePhaseChanged(to: .active)

        for _ in 0..<2 {
            target.scenePhaseChanged(to: .inactive)
            target.scenePhaseChanged(to: .background)
            target.scenePhaseChanged(to: .inactive)
            target.scenePhaseChanged(to: .active)
        }
        target.scenePhaseChanged(to: .active)

        XCTAssertEqual(refreshes, 2)
    }
}
