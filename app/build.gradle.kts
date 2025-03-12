plugins {
    alias(libs.plugins.pliuskis.android.application.compose)
    alias(libs.plugins.pliuskis.android.application)
    alias(libs.plugins.pliuskis.android.code.quality)
}

android {
    namespace = "lt.setkus.pliuskis"

    defaultConfig {
        applicationId = "lt.setkus.pliuskis"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "lt.setkus.pliuskis.core.testing.PliuskisTestRunner"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packagingOptions {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {
    implementation(projects.feature.devices)
    implementation(projects.feature.control)

    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.lifecycle.ktx)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.koin.compose)

    implementation(libs.androidx.compose.material3)
    // Android Studio Preview support
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // Optional - Integration with activities
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    // Optional - Integration with ViewModels
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime.livedata)

    // Android
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)

    // Unit tests
    testImplementation(libs.kotlin.test)
    testImplementation(projects.core.testing)
    testImplementation(projects.core.screenshotTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.navigation.testing)

    // Android UI tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)

    // Espresso tests
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}
