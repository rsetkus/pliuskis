package lt.setkus.pliuskis.main

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication

/**
 * STORY-2: DevicesViewModel connects then discovers; app launch no longer connects.
 * Covers AC12.
 *
 * mainModule is the app-shell module MainActivity resolves from (MainActivityViewModel, if kept).
 * It is started on its own, without the core/data modules that provide ConnectUseCase, so any
 * definition in it that still depends on a connection fails to resolve. With no connection
 * dependency, MainActivity's splash keep-on-screen condition has no connection result to wait on.
 */
@OptIn(KoinInternalApi::class)
class Story2AppLaunchTest : FunSpec({

    // AC12
    // GIVEN the app's Koin graph
    // WHEN MainActivity's dependencies are resolved
    // THEN nothing reachable from MainActivity should depend on ConnectUseCase
    test("testStory2_appShellResolvesWithoutConnectUseCase") {
        val koinApp = koinApplication { modules(mainModule) }
        try {
            mainModule.mappings.values
                .map { it.beanDefinition }
                .distinct()
                .forEach { definition ->
                    shouldNotThrowAny {
                        koinApp.koin.get<Any>(clazz = definition.primaryType, qualifier = definition.qualifier)
                    }
                }
        } finally {
            koinApp.close()
        }
    }
})
