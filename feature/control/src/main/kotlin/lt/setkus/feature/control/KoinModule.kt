package lt.setkus.feature.control

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val controlModule = module {
    viewModel { ControlViewModel(get(), get(), get()) }
}