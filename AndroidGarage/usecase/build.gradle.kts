import importboundary.ImportBoundaryCheckTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

tasks.register<ImportBoundaryCheckTask>("checkImportBoundary") {
    sourceDir = "$projectDir/src/commonMain/kotlin"
    allowedPrefixes = listOf("androidx.lifecycle.")
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(project(":test-common"))
            // Test-only: lets us wire the REAL NetworkSnoozeRepository into VM
            // tests so propagation goes through production code, not fakes.
            implementation(project(":data"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.chriscartland.garage.usecase"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
