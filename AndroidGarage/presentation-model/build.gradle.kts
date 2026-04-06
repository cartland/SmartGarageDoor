import importboundary.ImportBoundaryCheckTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

tasks.register<ImportBoundaryCheckTask>("checkImportBoundary") {
    sourceDir = "$projectDir/src/commonMain/kotlin"
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.chriscartland.garage.presentation"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
