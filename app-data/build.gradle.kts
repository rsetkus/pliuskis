plugins {
    alias(libs.plugins.pliuskis.android.library)
    alias(libs.plugins.secrets)
}

android {
    namespace = "lt.setkus.pliuskis.data"
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
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "default.secrets.properties"
}

dependencies {

    implementation(project(":app-core"))

    implementation(libs.amazonaws.android.iot)
    implementation(libs.hiveMqClient)
    implementation(libs.kotlin.coroutines.rx2)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.turbine)
}