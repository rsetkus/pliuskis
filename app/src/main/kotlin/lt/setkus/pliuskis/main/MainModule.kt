package lt.setkus.pliuskis.main

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val mainModule = module {
    viewModelOf(::MainActivityViewModel)
}
