/*
 * Copyright 2024 Chris Cartland. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

// `:iosFramework` — Gradle module that produces the iOS `.framework`
// binary consumed by the SwiftUI app at `AndroidGarage/iosApp/`. iOS-only;
// no Android target. Mirrors battery-butler's `:ios-swift-di` module.
//
// Future PRs will add:
//  - SKIE plugin (StateFlow → AsyncSequence + Combine, sealed → Swift enum)
//  - kotlin-inject KSP wiring for the iOS `NativeComponent` DI graph
//  - Framework exports of :domain, :viewmodel, :presentation-model,
//    and androidx.lifecycle.viewmodel (so Swift can name those types)
//  - `implementation` deps on :data, :data-local, :usecase (pulled into
//    the framework binary but not exposed as public types to Swift)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "shared"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
        }
    }
}
