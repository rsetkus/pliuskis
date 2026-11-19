package lt.setkus.pliuskis.core.systemstate

import app.cash.turbine.test
import arrow.core.Either
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CommandGetSystemStateLevelUseCaseTest : FunSpec({

    val repository = RecordingSystemStateRequestable()
    val target = RequestSystemStateUseCase(repository)

    test(
        """
        GIVEN water level repository,
        WHEN use case to get water level is executed,
        THEN should initiate repository to send a signal
    """
    ) {
        val param = "param"
        target(param).test {
            awaitItem() shouldBe Either.Right(Unit)
            awaitComplete()
        }
        repository.requestedDeviceIds shouldBe listOf(param)
    }
})
