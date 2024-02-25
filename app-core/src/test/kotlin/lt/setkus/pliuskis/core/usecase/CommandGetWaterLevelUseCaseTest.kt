package lt.setkus.pliuskis.core.usecase

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify
import lt.setkus.pliuskis.core.systemstate.CommandGetSystemStateLevelUseCase
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CommandGetWaterLevelUseCaseTest : FunSpec({

    val mockRepository = mockk<SystemStateRequestable>(relaxed = true)
    val target = CommandGetSystemStateLevelUseCase(mockRepository)

    test("""
        GIVEN water level repository,
        WHEN use case to to get water level is executed,
        THEN should initiate repository to send a signal
    """) {
        target(Unit).test { awaitComplete() }
        verify(exactly = 1) { mockRepository.requestSystemState() }
    }
})