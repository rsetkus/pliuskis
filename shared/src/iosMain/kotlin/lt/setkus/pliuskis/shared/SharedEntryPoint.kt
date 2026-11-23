package lt.setkus.pliuskis.shared

import kotlinx.coroutines.flow.first
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.di.coreModule
import lt.setkus.pliuskis.core.presentation.control.ControlViewModel
import lt.setkus.pliuskis.core.presentation.control.DeviceStatusMapper
import lt.setkus.pliuskis.core.presentation.control.newControlViewModel
import lt.setkus.pliuskis.core.presentation.devices.DevicesViewModel
import lt.setkus.pliuskis.core.presentation.devices.fromDomainModel
import lt.setkus.pliuskis.core.presentation.session.SessionViewModel
import lt.setkus.pliuskis.core.presentation.session.newSessionViewModel
import lt.setkus.pliuskis.core.presentation.signin.SignInViewModel
import lt.setkus.pliuskis.core.presentation.signin.newSignInViewModel
import lt.setkus.pliuskis.data.aws.AwsIotCredentialsCache
import lt.setkus.pliuskis.data.aws.AwsIotSettings
import lt.setkus.pliuskis.data.aws.SecurityTokenPlacement
import lt.setkus.pliuskis.data.aws.StaleAwsCredentialsException
import lt.setkus.pliuskis.data.di.dataModule
import lt.setkus.pliuskis.data.di.iosDataModule
import lt.setkus.pliuskis.data.mqttclient.MqttSettings
import lt.setkus.pliuskis.data.mqttclient.MqttTransport
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.coroutines.cancellation.CancellationException

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
            observeReconnects = get(),
            mapper = DeviceStatusMapper(),
        )
    }
    factory { NetworkStatusViewModel(connectivity = get()) }
    factory { newSignInViewModel(signIn = get()) }
    factory {
        newSessionViewModel(
            restoreSession = get(),
            observeSession = get(),
            signOut = get(),
            deleteAccount = get(),
        )
    }
}

/**
 * Starts the shared object graph with the broker [settings] and the app's MQTT socket [transport] (Swift:
 * `SharedEntryPointKt.doInitShared(settings:transport:)`, with a `CocoaMqttTransport`). The sign-in settings come from
 * the app's Info.plist (`Cognito*` keys). Call it once, before any factory. A later call, while the graph is running,
 * does nothing and keeps the first settings and transport.
 */
fun initShared(settings: MqttSettings, transport: MqttTransport) {
    startShared(settings, transport, awsIot = null)
}

/**
 * Starts the shared object graph like [initShared], but the MQTT client connects to AWS IoT with the signed-in
 * identity's temporary Cognito credentials (Swift: `SharedEntryPointKt.doInitSharedOverAwsIot(settings:transport:
 * tokenPlacement:)`). The identity pool, region and AWS IoT endpoint come from the app's Info.plist
 * (`CognitoIdentityPoolId`, `AwsRegion`, `AmazonIotEndpoint`); [settings] give the endpoint as host, port 443 and this
 * install's client ID, which becomes the suffix of `<identity ID>-<install suffix>`. [transport] must connect over
 * WebSocket to [signedAwsIotWebSocketUrl] on every attempt. [tokenPlacement] says where the signed URL carries the
 * session token. A later call, while the graph is running, does nothing.
 */
fun initSharedOverAwsIot(settings: MqttSettings, transport: MqttTransport, tokenPlacement: SecurityTokenPlacement) {
    startShared(settings, transport, awsIotSettingsFromInfoPlist(tokenPlacement))
}

/**
 * A newly signed `wss://<endpoint>/mqtt?...` URL of AWS IoT for one connect attempt, signed now with the cached
 * credentials (Swift: `try SharedEntryPointKt.signedAwsIotWebSocketUrl()`). It never waits for the network. It throws
 * [StaleAwsCredentialsException] when no fresh credentials are cached (they are being refreshed, so a later attempt
 * succeeds) or when the graph was started with [initShared] instead of [initSharedOverAwsIot]. The URL carries the
 * signature and the session token: never log it.
 */
@Throws(StaleAwsCredentialsException::class, CancellationException::class)
fun signedAwsIotWebSocketUrl(): String {
    val credentials = KoinPlatform.getKoinOrNull()?.getOrNull<AwsIotCredentialsCache>()
    return credentials?.signedUrlNow() ?: throw StaleAwsCredentialsException()
}

private fun startShared(settings: MqttSettings, transport: MqttTransport, awsIot: AwsIotSettings?) {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            modules(
                module {
                    single { settings }
                    single { signInSettingsFromInfoPlist() }
                    single { appleRevokeSettingsFromInfoPlist() }
                },
                dataModule,
                iosDataModule(transport, awsIot = awsIot),
                coreModule,
                sharedViewModelModule,
                networkStatusLogModule,
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

/**
 * A new network status ViewModel for the offline banner. Its `isOffline` state follows the phone's connectivity while
 * it is collected. Call [initShared] first.
 */
fun createNetworkStatusViewModel(): NetworkStatusViewModel = KoinPlatform.getKoin().get()

/**
 * A new sign-in ViewModel. Its `state` starts as `SignInStateSignedOut`; `signIn(provider:)` opens Cognito's managed
 * login. A failure is a `SignInStateError` state, never a thrown error. Call [initShared] first.
 */
fun createSignInViewModel(): SignInViewModel = KoinPlatform.getKoin().get()

/**
 * A new session ViewModel, for the whole app. Its `state` starts as `SessionStateChecking` while the stored session
 * (Keychain) is checked, then follows it: `SessionStateSignedOut` or `SessionStateSignedIn`. Call [initShared] first.
 */
fun createSessionViewModel(): SessionViewModel = KoinPlatform.getKoin().get()
