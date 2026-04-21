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

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

tasks.register<codestyle.NoFullyQualifiedNamesTask>("checkNoFullyQualifiedNames") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
        "$rootDir/androidApp/src/test/java",
        "$rootDir/domain/src/commonMain/kotlin",
        "$rootDir/data/src/commonMain/kotlin",
        "$rootDir/data-local/src/commonMain/kotlin",
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/presentation-model/src/commonMain/kotlin",
    )
}

tasks.register<codestyle.NoNav2ImportsTask>("checkNoNav2Imports") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
    )
}

tasks.register<architecture.ArchitectureCheckTask>("checkArchitecture") {
    // Capture module dependencies at configuration time (Gradle 9 compatible)
    moduleDependencies = subprojects
        .flatMap { subproject ->
            val moduleName = ":" + subproject.path.removePrefix(":")
            subproject.configurations
                .filter { config ->
                    val name = config.name.lowercase()
                    !name.contains("test") && !name.contains("ksp") && !name.contains("detekt")
                }.flatMap { config ->
                    config.dependencies
                        .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                        .map { dep -> "$moduleName -> ${dep.path}" }
                }
        }.distinct()
}

tasks.register<architecture.SingletonGuardTask>("checkSingletonGuard") {
    sourceDir = "$rootDir/androidApp/src/main/java"
    guardedMethodPatterns = listOf(
        // Framework singletons — multiple instances crash or corrupt.
        "provideAppDatabase",
        "provideAppSettings",
        "provideHttpClient",
        // ADR-022 state-owning repositories — if not @Singleton, the owned
        // StateFlow is instantiated per-caller and every subscriber sees its
        // own timeline (the android/164-168 class of bug).
        "provideAuthRepository",
        "provideSnoozeRepository",
        "provideDoorRepository",
        "provideServerConfigRepository",
        "provideDoorFcmRepository",
        "provideFcmRegistrationManager",
    )
}

tasks.register<architecture.LayerImportCheckTask>("checkLayerImports") {
    sourceDirs = listOf(
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/androidApp/src/main/java",
    )
    rules = listOf(
        // ViewModels must not import DataSource or concrete Repository/Bridge implementations
        listOf(
            ".*ViewModel\\.kt",
            "com.chriscartland.garage.data.Local,com.chriscartland.garage.data.Network,com.chriscartland.garage.data.ktor.,com.chriscartland.garage.data.repository.,com.chriscartland.garage.datalocal.",
            "ViewModels must depend on UseCases and domain interfaces, not data layer implementations.",
        ),
        // ViewModels must not import domain repository interfaces — only UseCases.
        // Phase 43: ViewModels orchestrate UI state via UseCases, never reach into the
        // repository layer directly. This keeps the dependency graph clean and
        // enables future module separation (battery-butler pattern).
        listOf(
            ".*ViewModel\\.kt",
            "com.chriscartland.garage.domain.repository.",
            "ViewModels must depend on UseCases, not domain repository interfaces. Wrap repository access in a UseCase.",
        ),
        // UseCases must not import DataSource or concrete implementations
        listOf(
            ".*UseCase\\.kt",
            "com.chriscartland.garage.data.Local,com.chriscartland.garage.data.Network,com.chriscartland.garage.data.ktor.,com.chriscartland.garage.data.repository.,com.chriscartland.garage.datalocal.",
            "UseCases must depend on domain repository interfaces, not data layer implementations.",
        ),
    )
}

tasks.register<architecture.HardcodedColorCheckTask>("checkHardcodedColors") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
    )
    // Only these files may define Color(0x...) — all others must use theme colors.
    allowedFilePatterns = listOf(
        "Color\\.kt",
        "DoorStatusColorScheme\\.kt",
        // Canvas/drawing files use Color for rendering, not text contrast.
        "GarageDoorCanvas\\.kt",
        "AnimatableGarageDoor\\.kt",
    )
}

tasks.register<codestyle.RememberSaveableGuardTask>("checkRememberSaveable") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
    )
}

tasks.register<architecture.ViewModelStateFlowCheckTask>("checkViewModelStateFlow") {
    sourceDirs = listOf(
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/androidApp/src/main/java",
    )
}

tasks.register<architecture.FakePublicVarCheckTask>("checkFakePublicVar") {
    sourceDirs = listOf(
        "$rootDir/test-common/src/commonMain/kotlin",
    )
}

tasks.register<testcoverage.TestCoverageCheckTask>("checkTestCoverage") {
    sourceDir = "$rootDir/androidApp/src/main/java/com/chriscartland/garage"
    testDir = "$rootDir/androidApp/src/test/java/com/chriscartland/garage"
    exemptionsFile = "$rootDir/test-coverage-exemptions.txt"
    patterns = listOf("ViewModel", "Repository")
}

tasks.register<architecture.SingletonCachingCheckTask>("checkSingletonCaching") {
    appComponentPath = "$rootDir/androidApp/src/main/java/com/chriscartland/garage/di/AppComponent.kt"
    generatedComponentPath =
        "$rootDir/androidApp/build/generated/ksp/debug/kotlin/com/chriscartland/garage/di/InjectAppComponent.kt"
    // The generated file only exists after KSP runs. Depend on the debug KSP task
    // so `./gradlew checkSingletonCaching` works standalone.
    dependsOn(":androidApp:kspDebugKotlin")
}

tasks.register<architecture.NoRawDispatchersTask>("checkNoRawDispatchers") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/presentation-model/src/commonMain/kotlin",
    )
}

tasks.register<architecture.NoBareTopLevelFunctionsTask>("checkNoBareTopLevelFunctions") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/presentation-model/src/commonMain/kotlin",
        "$rootDir/domain/src/commonMain/kotlin",
        "$rootDir/data/src/commonMain/kotlin",
        "$rootDir/data-local/src/commonMain/kotlin",
    )
}

tasks.register<architecture.NoImplSuffixTask>("checkNoImplSuffix") {
    sourceDirs = listOf(
        "$rootDir/androidApp/src/main/java",
        "$rootDir/usecase/src/commonMain/kotlin",
        "$rootDir/presentation-model/src/commonMain/kotlin",
        "$rootDir/domain/src/commonMain/kotlin",
        "$rootDir/data/src/commonMain/kotlin",
        "$rootDir/data-local/src/commonMain/kotlin",
        "$rootDir/test-common/src/commonMain/kotlin",
    )
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.files("detekt.yml"))
        buildUponDefaultConfig = true
        val baselineFile = file("$projectDir/detekt-baseline.xml")
        if (baselineFile.exists()) {
            baseline = baselineFile
        }
    }
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("${layout.buildDirectory}/**/*.kt")
            ktlint()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint()
        }
    }
}
