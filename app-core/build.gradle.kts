plugins {
    alias(libs.plugins.pliuskis.android.library)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    namespace = "lt.setkus.core"
}

dependencies {

    // Kotlin
    api(libs.androidx.core.ktx)
    api(libs.kotlin.coroutines.android)

    //Tools
    api(libs.timber)

    // Amazon Iot
    implementation(libs.amazonaws.android.iot)

    // Network
    implementation(libs.gson)

    // Unit tests
    testApi(libs.junit)
    testApi(libs.mockk)
    testApi(libs.kotlinx.coroutines.test)
    testApi(libs.kotest.runner.junit5)
    testApi(libs.kotest.assertions.core)
    testApi(libs.turbine)
    testApi(libs.androidx.arch.core)
}