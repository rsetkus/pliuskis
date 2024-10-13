package lt.setkus.pliuskis.core.systemstate

import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.mockk.coVerify
import io.mockk.mockk

class CommandGetSystemStateLevelUseCaseTest : FunSpec({

    val mockRepository = mockk<SystemStateRequestable>(relaxed = true)
    val target = CommandGetSystemStateLevelUseCase(mockRepository)

    test("""
        GIVEN water level repository,
        WHEN use case to get water level is executed,
        THEN should initiate repository to send a signal
    """) {
        val param = "param"
        target(param).test { awaitComplete() }
        coVerify(exactly = 1) { mockRepository.requestSystemState(param) }
    }
})