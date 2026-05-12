// `:iosFramework` — Gradle module that produces the iOS `.framework`
// binary consumed by the SwiftUI app at `AndroidGarage/iosApp/`. iOS-only;
// no Android target. Mirrors battery-butler's `:ios-swift-di` module.
//
// Plugin stack:
//  - SKIE (co.touchlab.skie): bridges Kotlin StateFlow → Swift
//    AsyncSequence / Combine Publisher, sealed → Swift enum
//  - kotlin-inject KSP: generates `InjectNativeComponent` (the iOS DI
//    graph subclass of `NativeComponent`)
//
// Framework exports:
//  - :domain, :viewmodel, :presentation-model (Swift names these types)
//  - androidx.lifecycle.viewmodel (Swift sees ViewModel / ViewModelStore)
//  - kermit (Swift may call Logger for parity with Kotlin diagnostics)
//
// Internal deps (not exported — types stay opaque to Swift):
//  - :data, :data-local, :usecase (pulled into the framework binary)
//
// The framework is dynamic (isStatic = false) because static frameworks
// can't re-export imported dependency symbols — required for the
// `androidx.lifecycle.viewmodel` export.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.skie)
}

kotlin {
    applyDefaultHierarchyTemplate()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = false
            export(project(":domain"))
            export(project(":viewmodel"))
            export(project(":presentation-model"))
            export(libs.androidx.lifecycle.viewmodel)
            export(libs.kermit)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(project(":viewmodel"))
            api(project(":presentation-model"))
            implementation(project(":data"))
            implementation(project(":data-local"))
            implementation(project(":usecase"))
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }

        // iosTest hosts the runtime DI graph identity tests
        // (`NativeComponentTest`). Shared by all 3 per-target test source
        // sets; runs locally via `:iosFramework:iosSimulatorArm64Test`.
        val iosTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        // Shared source code for all 3 iOS targets — lets `IosNativeHelper`
        // access the per-target KSP-generated `InjectNativeComponent` class
        // without duplicating the helper file.
        val iosShared = "src/iosShared/kotlin"
        val iosArm64Main by getting { kotlin.srcDir(iosShared) }
        val iosSimulatorArm64Main by getting { kotlin.srcDir(iosShared) }
        val iosX64Main by getting { kotlin.srcDir(iosShared) }
    }
}

dependencies {
    add("kspIosX64", libs.kotlin.inject.compiler)
    add("kspIosArm64", libs.kotlin.inject.compiler)
    add("kspIosSimulatorArm64", libs.kotlin.inject.compiler)
}
