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

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.gms)
    alias(libs.plugins.baselineprofile)
}

val localPropertiesExplanation =
    """
    Make sure you have a local.properties file with the following properties:

    # local.properties
    SERVER_CONFIG_KEY=YourKey
    GOOGLE_WEB_CLIENT_ID=YourClientId
    # Keystore password and key password are required for release builds
    GARAGE_RELEASE_KEYSTORE_PWD=YourKeystorePassword
    GARAGE_RELEASE_KEY_PWD=YourKeyPassword
    """.trimIndent()
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    } else {
        println(localPropertiesExplanation)
    }
}

android {
    namespace = "com.chriscartland.garage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chriscartland.garage"
        minSdk = 26
        targetSdk = 36
        // versionCode comes from android/N tag via -PVERSION_CODE=N
        // Falls back to 1 for local development builds
        val tagVersionCode = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull()
        versionCode = tagVersionCode ?: 1
        // versionName from version.properties (manually bumped, semver)
        val versionProps = Properties().apply {
            rootProject.file("version.properties").inputStream().use { load(it) }
        }
        versionName = versionProps.getProperty("versionName")
        setProperty("archivesBaseName", "$applicationId-$versionName-$versionCode")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val serverConfigKey = "SERVER_CONFIG_KEY".let {
            localProperties.getProperty(it) ?: project.findProperty(it) as? String
        }
        val googleWebClientId = "GOOGLE_WEB_CLIENT_ID".let {
            localProperties.getProperty(it) ?: project.findProperty(it) as? String
        }
        if (serverConfigKey.isNullOrBlank()) {
            logger.warn("WARNING: SERVER_CONFIG_KEY is not set. The app will not be able to fetch server config.")
        }
        if (googleWebClientId.isNullOrBlank()) {
            logger.warn("WARNING: GOOGLE_WEB_CLIENT_ID is not set. Google Sign-In will not work.")
        }
        buildConfigField(
            "String",
            "SERVER_CONFIG_KEY",
            "\"${serverConfigKey ?: ""}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${googleWebClientId ?: ""}\"",
        )
        val buildTimestamp = DateTimeFormatter
            .ofPattern("yyyyMMdd.HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        buildConfigField(
            "String",
            "BUILD_TIMESTAMP",
            "\"$buildTimestamp\"",
        )
        buildConfigField(
            "String",
            "BASE_URL",
            "\"https://us-central1-escape-echo.cloudfunctions.net/\"",
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("release/debug.keystore")
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
        }
        create("release") {
            val releaseKeystorePwd = "GARAGE_RELEASE_KEYSTORE_PWD".let {
                localProperties.getProperty(it) ?: project.findProperty(it) as? String
            }
            val releaseKeyPwd = "GARAGE_RELEASE_KEY_PWD".let {
                localProperties.getProperty(it) ?: project.findProperty(it) as? String
            }
            storeFile = rootProject.file("release/app-release.jks")
            storePassword = releaseKeystorePwd
            keyAlias = "Garage"
            keyPassword = releaseKeyPwd
        }
    }

    buildTypes {
        // Add .debug suffix so debug can be installed on same device as release
        val debug by getting {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        // Turn on R8 optimizations and sign with release key
        val release by getting {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // Build with debug key that tries to match release properties
        val benchmark by creating {
            initWith(release)
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += listOf("release")
            // Default: perf-accurate (not debuggable). Toggle on with
            // `-PdebuggableBenchmark=true` to run instrumented tests
            // against an R8-enabled build and reproduce release-only bugs
            // (e.g., StateFlow propagation under minification).
            isDebuggable = providers.gradleProperty("debuggableBenchmark").orNull == "true"
        }
    }

    // When `-PtestR8=true`, route `connectedAndroidTest` (and friends) to
    // the benchmark variant so instrumented tests execute against R8-minified
    // bytecode. Must be combined with `-PdebuggableBenchmark=true` so the
    // installed APK allows test instrumentation. See AndroidGarage/docs for
    // the full investigation recipe.
    if (providers.gradleProperty("testR8").orNull == "true") {
        testBuildType = "benchmark"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        managedDevices {
            localDevices {
                create("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set(
                    StringBuilder().run {
                        if (variant.applicationId.isPresent) {
                            append(variant.applicationId.get()) // Package name
                        }
                        if (output.versionName.isPresent) {
                            append("_").append(output.versionName.get()) // Version name
                        }
                        if (output.versionCode.isPresent) {
                            append("_").append(output.versionCode.get()) // Version code
                        }
                        if (!variant.flavorName.isNullOrBlank()) {
                            append("_").append(variant.flavorName) // Optional flavor
                        }
                        if (!variant.buildType.isNullOrBlank()) {
                            append("_").append(variant.buildType) // Optional build type
                        }
                        append(".apk")
                        toString()
                    },
                )
            }
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":data-local"))
    implementation(project(":usecase"))
    implementation(project(":presentation-model"))
    implementation(libs.kermit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // Tracing
    implementation(libs.androidx.tracing)
    // Accompanist
    implementation(libs.accompanist.permissions)
    // Baseline Profiles
    implementation(libs.androidx.profileinstaller)
    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.play.services.auth)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":test-common"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Needed by SnoozeStateInstrumentedPropagationTest to wire the real
    // NetworkSnoozeRepository + DefaultRemoteButtonViewModel around fakes.
    androidTestImplementation(project(":test-common"))
    androidTestImplementation(project(":data"))
    androidTestImplementation(project(":domain"))
    androidTestImplementation(project(":usecase"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // kotlinx.serialization (Ktor HTTP client is in :data module)
    implementation(libs.kotlinx.serialization.json)
    // kotlin-inject (replaced Hilt — see docs/DI-MIGRATION.md)
    implementation(libs.kotlin.inject.runtime)
    ksp(libs.kotlin.inject.compiler)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.messaging)
    // Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleId)
}
