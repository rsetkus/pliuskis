import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.pliuskis.android.application.compose)
    alias(libs.plugins.pliuskis.android.application)
    alias(libs.plugins.secrets)
    alias(libs.plugins.pliuskis.android.code.quality)
}

// The version name both apps share, defined once in version.properties (iOS reads it as MARKETING_VERSION)
val versionFile = rootProject.layout.projectDirectory.file("version.properties")
val versionProperties = Properties().apply {
    load(StringReader(providers.fileContents(versionFile).asText.get()))
}

android {
    namespace = "lt.setkus.pliuskis"
    // brokerModule reads the broker and keystore settings from BuildConfig (filled by the secrets plugin)
    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "lt.setkus.pliuskis"
        versionCode = 1
        versionName = checkNotNull(versionProperties.getProperty("VERSION_NAME")) {
            "VERSION_NAME is missing in version.properties"
        }

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

    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    testOptions.unitTests.all { it.useJUnitPlatform() }
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "default.secrets.properties"
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

    implementation(libs.koin)
    implementation(libs.koin.compose)

    implementation(libs.timber)

    implementation(libs.androidx.compose.material3)
    // Android Studio Preview support
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
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
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)

    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)

    // Espresso tests
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
}
