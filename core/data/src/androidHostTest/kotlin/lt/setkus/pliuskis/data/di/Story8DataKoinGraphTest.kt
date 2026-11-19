package lt.setkus.pliuskis.data.di

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import lt.setkus.pliuskis.core.command.CommandExecutable
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.Connectable
import lt.setkus.pliuskis.core.devicelist.DeviceSubscribable
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.di.coreModule
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import lt.setkus.pliuskis.core.systemstate.SystemStateRequestable
import lt.setkus.pliuskis.core.systemstate.SystemStateSubscribable
import lt.setkus.pliuskis.data.IotManager
import lt.setkus.pliuskis.data.clientid.ClientIdStore
import lt.setkus.pliuskis.data.clientid.InMemoryClientIdStore
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.TlsMode
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

private const val CLIENT_ID = "abc123"
private const val DEBUG_HOST = "10.0.2.2"
private const val DEBUG_PORT = 1883

/**
 * What `app` adds to the data modules on Android, without a Context: debug broker settings (no TLS, so no
 * MqttTlsSetup) and an in-memory client ID store in place of the SharedPreferences one.
 */
private val appTestModule = module {
    single { MqttSettings(CLIENT_ID, DEBUG_HOST, DEBUG_PORT, TlsMode.NONE) }
    single<ClientIdStore> { InMemoryClientIdStore(CLIENT_ID) }
}

/**
 * Runs [block] on the Android data graph: the common data module, the Android platform data module, the app's
 * settings and any [extraModules].
 */
private fun <T> withAndroidDataGraph(vararg extraModules: Module, block: (KoinApplication) -> T): T {
    val koinApp = koinApplication { modules(listOf(dataModule, androidDataModule, appTestModule) + extraModules) }
    return try {
        block(koinApp)
    } finally {
        koinApp.close()
    }
}

/**
 * STORY-8 AC38: core:data's Koin setup is split into a common module (`dataModule`, commonMain) and an Android
 * platform module (`androidDataModule`, androidMain). Together with the settings `app` provides, they resolve the
 * IotManager, the subscribables and the command executors, and the domain use cases built on them.
 *
 * Resolving builds the HiveMQ client but does not connect, so no broker is needed.
 */
class Story8DataKoinGraphTest : FunSpec({

    // AC38
    // GIVEN the common and Android data modules and debug settings with client ID abc123
    // WHEN IotManager is resolved
    // THEN it is the client built from those settings, and the graph shares one instance
    test("testStory8_androidGraphResolvesOneIotManagerForTheSettingsClient") {
        withAndroidDataGraph { koinApp ->
            val manager = koinApp.koin.get<IotManager>()

            manager.getClientId() shouldBe CLIENT_ID
            koinApp.koin.get<IotManager>() shouldBeSameInstanceAs manager
        }
    }

    // AC38
    // GIVEN the common and Android data modules and debug settings
    // WHEN the subscribables, the status request, the connect source and the command executor are resolved
    // THEN each resolves
    test("testStory8_androidGraphResolvesSubscribablesAndCommandExecutors") {
        withAndroidDataGraph { koinApp ->
            shouldNotThrowAny {
                koinApp.koin.get<DeviceSubscribable>()
                koinApp.koin.get<SystemStateSubscribable>()
                koinApp.koin.get<SystemStateRequestable>()
                koinApp.koin.get<Connectable>()
                koinApp.koin.get<CommandExecutable<String>>()
            }
        }
    }

    // AC38
    // GIVEN the Android data graph plus the domain module, as the app loads them
    // WHEN every domain use case is resolved
    // THEN each resolves from the data bindings
    test("testStory8_androidGraphResolvesDomainUseCasesFromDataBindings") {
        withAndroidDataGraph(coreModule) { koinApp ->
            shouldNotThrowAny {
                koinApp.koin.get<ConnectUseCase>()
                koinApp.koin.get<ObserveDevicesUseCase>()
                koinApp.koin.get<RequestSystemStateUseCase>()
                koinApp.koin.get<GetSystemStatusUseUseCase>()
                koinApp.koin.get<ExecuteWaterCommandUseCase>()
            }
        }
    }
})
