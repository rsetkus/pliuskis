package lt.setkus.pliuskis.core.di

import lt.setkus.pliuskis.core.auth.DeleteAccountUseCase
import lt.setkus.pliuskis.core.auth.ObserveSessionUseCase
import lt.setkus.pliuskis.core.auth.RestoreSessionUseCase
import lt.setkus.pliuskis.core.auth.SignInUseCase
import lt.setkus.pliuskis.core.auth.SignOutUseCase
import lt.setkus.pliuskis.core.command.water.ExecuteWaterCommandUseCase
import lt.setkus.pliuskis.core.connect.ConnectUseCase
import lt.setkus.pliuskis.core.connect.ObserveReconnectsUseCase
import lt.setkus.pliuskis.core.devicelist.ObserveDevicesUseCase
import lt.setkus.pliuskis.core.systemstate.GetSystemStatusUseUseCase
import lt.setkus.pliuskis.core.systemstate.RequestSystemStateUseCase
import org.koin.dsl.module

val coreModule = module {
    factory { RequestSystemStateUseCase(get()) }
    factory { ConnectUseCase(get()) }
    factory { ObserveReconnectsUseCase(get()) }
    factory { ObserveDevicesUseCase(get()) }
    factory { GetSystemStatusUseUseCase(get()) }
    factory { ExecuteWaterCommandUseCase(get()) }

    // Sign-in and session. The Authenticator, TokenStore, AppleAuthorization and AppleTokenRevoker ports come from the
    // platform data module.
    factory { RestoreSessionUseCase(get(), get()) }
    factory { ObserveSessionUseCase(get()) }
    factory { SignInUseCase(get(), get()) }
    factory { SignOutUseCase(get(), get()) }
    factory { DeleteAccountUseCase(get(), get(), get(), get()) }
}
