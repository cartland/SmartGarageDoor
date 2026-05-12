import importboundary.ImportBoundaryCheckTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// `:usecase` is pure-Kotlin business logic — no Android framework
// dependencies. Pre-2.16.27 the `androidx.lifecycle.` prefix was
// allowed because the 5 screen-scoped ViewModels lived in this module;
// they moved to `:viewmodel` in the same release, so the allowance
// is no longer needed.
tasks.register<ImportBoundaryCheckTask>("checkImportBoundary") {
    sourceDir = "$projectDir/src/commonMain/kotlin"
    allowedPrefixes = emptyList()
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
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
