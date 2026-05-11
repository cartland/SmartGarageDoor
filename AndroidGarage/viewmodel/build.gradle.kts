import importboundary.ImportBoundaryCheckTask

// `:viewmodel` — screen-scoped ViewModels (post-2.16.27 / ADR-026).
// One VM per screen. Layer-dep rule: production code (commonMain) may
// only depend on `:domain` (types) and `:usecase` (orchestration). It
// MUST NOT depend on `:data`, `:data-local`, or any concrete repository
// implementation — VMs orchestrate UI state via UseCases, never reach
// into the repository layer directly. Tests (commonTest) additionally
// depend on `:test-common` (fakes) and `:data` (real Network*Repository
// for the propagation-style tests). Source-level enforcement: the
// `:.*ViewModel\.kt` rules in `checkLayerImports` (in root
// build.gradle.kts) block VM files from importing `data.*` or
// `domain.repository.*` types regardless of which Gradle module they
// live in.
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
            implementation(project(":usecase"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(project(":test-common"))
            // Test-only: lets us wire the REAL NetworkSnoozeRepository into VM
            // tests so propagation goes through production code, not fakes.
            implementation(project(":data"))
            implementation(project(":usecase"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.chriscartland.garage.viewmodel"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
