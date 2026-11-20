package lt.setkus.pliuskis.data.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * STORY-2: the iOS [NetworkConnectivity] turns the path updates of its [NetworkPathSource] (NWPathMonitor in the app)
 * into `isOnline`. The manual half of AC15 (Wi-Fi off and on in the running app) is a skipped XCTest with the steps,
 * `iosApp/iosAppTests/Story2ManualChecksTests.swift`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Story2IosNetworkConnectivityTest : FunSpec({

    // AC15
    // GIVEN isOnline is collected
    // WHEN the path source reports an unsatisfied path, then a satisfied one
    // THEN isOnline emits false, then true
    test("testStory2_isOnlineEmitsFalseThenTrueWhenThePathGoesUnsatisfiedThenSatisfied") {
        runTest {
            val pathSource = FakeNetworkPathSource()
            val connectivity = IosNetworkConnectivity(pathSource)
            val received = mutableListOf<Boolean>()
            val job = launch { connectivity.isOnline.toList(received) }
            runCurrent()

            pathSource.report(satisfied = false)
            runCurrent()
            pathSource.report(satisfied = true)
            runCurrent()

            received shouldBe listOf(false, true)
            job.cancel()
        }
    }
})
