package lt.setkus.pliuskis

import android.app.Application
import lt.setkus.pliuskis.core.di.coreModule
import lt.setkus.pliuskis.data.di.dataModule
import lt.setkus.pliuskis.feature.devices.devicesModule
import lt.setkus.pliuskis.main.mainModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber
import timber.log.Timber.DebugTree

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(DebugTree())

        startKoin {
            androidLogger()
            androidContext(this@MyApplication)
            modules(dataModule, coreModule, mainModule, devicesModule)
        }
    }
}