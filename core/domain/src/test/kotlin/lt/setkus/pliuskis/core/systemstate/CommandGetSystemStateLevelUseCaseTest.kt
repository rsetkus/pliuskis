package lt.setkus.pliuskis.core.systemstate

import app.cash.turbine.test
import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk

class CommandGetSystemStateLevelUseCaseTest : FunSpec({

    val mockRepository = mockk<SystemStateRequestable>(relaxed = true)
    val target = RequestSystemStateUseCase(mockRepository)

    test("""
        GIVEN water level repository,
        WHEN use case to get water level is executed,
        THEN should initiate repository to send a signal
    """) {
        val param = "param"
        target(param).test {
            awaitItem() shouldBe Either.Right(Unit)
            awaitComplete()
        }
        coVerify(exactly = 1) { mockRepository.requestSystemState(param) }
    }
})
