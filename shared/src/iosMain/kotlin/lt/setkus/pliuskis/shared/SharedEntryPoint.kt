package lt.setkus.pliuskis.shared

import kotlinx.coroutines.flow.first
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.di.coreModule
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.control.DeviceStatusMapper
import lt.setkus.pliuskis.core.presentation.control.newControlViewModel
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.core.presentation.devices.fromDomainModel
import lt.setkus.pliuskis.data.di.dataModule
import lt.setkus.pliuskis.data.di.iosDataModule
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/*
 * The Swift-facing entry point of the `Shared` framework (Swift: `SharedEntryPointKt`). Swift calls `initShared` once
 * at app start, then creates each ViewModel through a factory below. Swift never sees Koin or the use cases.
 */

/** The ViewModels Swift creates. Swift owns their lifetime, so they are plain factories. */
internal val sharedViewModelModule = module {
    factory {
        DevicesViewModel(
            connect = get(),
            observeDevices = get(),
            mapper = ::fromDomainModel,
        )
    }
    factory { params ->
        val connectUseCase: ConnectUseCase = get()
        newControlViewModel(
            deviceId = params.get(),
            // The connect call suspends without blocking, so it is safe on the main thread.
            connect = { connectUseCase(Unit).first() },
            systemStatusUseCase = get(),
            getSystemStatusUseUseCase = get(),
            executeCommandUseCase = get(),
            mapper = DeviceStatusMapper(),
        )
    }
}

/**
 * Starts the shared object graph with the broker [settings] (Swift: `SharedEntryPointKt.doInitShared(settings:)`).
 * Call it once, before any factory. A later call, while the graph is running, does nothing and keeps the first
 * settings.
 */
fun initShared(settings: MqttSettings) {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            modules(
                module { single { settings } },
                dataModule,
                iosDataModule,
                coreModule,
                sharedViewModelModule,
            )
        }
    }
}

/** A new device list ViewModel. Its `devices` state starts as Loading. Call [initShared] first. */
fun createDevicesViewModel(): DevicesViewModel = KoinPlatform.getKoin().get()

/**
 * A new control ViewModel for the device [deviceId]. Its `controlUiState` starts as Loading. Call [initShared] first.
 */
fun createControlViewModel(deviceId: String): ControlViewModel =
    KoinPlatform.getKoin().get { parametersOf(deviceId) }
