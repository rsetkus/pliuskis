plugins {
    alias(libs.plugins.pliuskis.android.application.compose)
    alias(libs.plugins.pliuskis.android.application)
}

android {
    namespace = "lt.setkus.pliuskis"

    defaultConfig {
        applicationId = "lt.setkus.pliuskis"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    packagingOptions {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

dependencies {

    implementation(project(":app-core"))
    implementation(project(":app-data"))

    implementation(libs.androidx.lifecycle.ktx)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.material3)
    // Android Studio Preview support
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Optional - Integration with activities
    implementation(libs.androidx.activity.compose)
    // Optional - Integration with ViewModels
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Android
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)

    // Unit tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)

    // Espresso tests
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}