// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.17.0" apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}

subprojects {
    apply(from = "../scripts/ktlint.gradle")
    apply(from = "../scripts/detekt.gradle")
}

//tasks.register("clean", Delete) {
//    delete rootProject.buildDir
//}