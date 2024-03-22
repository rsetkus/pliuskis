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
}