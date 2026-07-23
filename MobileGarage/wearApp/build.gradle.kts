/*
 * Copyright 2026 Chris Cartland. All rights reserved.
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

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.gms)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

// Wear artifact versionCodes share the phone's applicationId and must be
// globally unique against android/N (= N) codes. wear/N maps to
// WEAR_VERSION_CODE_OFFSET + N, keeping the two sequences disjoint.
val wearVersionCodeOffset = 1_000_000

android {
    namespace = "com.chriscartland.garage.wear"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app: Wear OS apps ship on the Wear
        // form-factor track of the SAME Play listing, and sharing the id lets
        // the module reuse the phone's google-services.json (copied in by
        // release/decrypt-secrets.sh; CI drops a placeholder when secrets are
        // absent).
        applicationId = "com.chriscartland.garage"
        // Wear OS 3 (API 30) and newer.
        minSdk = 30
        targetSdk = 36
        // versionCode comes from the wear/N tag via -PWEAR_VERSION_CODE=N
        // (release-wear.yml), offset per wearVersionCodeOffset above.
        // Local builds fall back to N=0, always below any real release.
        val tagVersionCode = (project.findProperty("WEAR_VERSION_CODE") as? String)?.toIntOrNull()
        versionCode = wearVersionCodeOffset + (tagVersionCode ?: 0)
        // versionName from wearApp/version.properties (manually bumped, semver).
        // release-wear.sh gates on a matching wearApp/CHANGELOG.md heading.
        val versionProps = Properties().apply {
            project.file("version.properties").inputStream().use { load(it) }
        }
        versionName = versionProps.getProperty("versionName")
        setProperty("archivesBaseName", "$applicationId-wear-$versionName-$versionCode")

        val serverConfigKey = "SERVER_CONFIG_KEY".let {
            localProperties.getProperty(it) ?: project.findProperty(it) as? String
        }
        val googleWebClientId = "GOOGLE_WEB_CLIENT_ID".let {
            localProperties.getProperty(it) ?: project.findProperty(it) as? String
        }
        if (serverConfigKey.isNullOrBlank()) {
            logger.warn("WARNING: SERVER_CONFIG_KEY is not set. The Wear app will not be able to fetch server config.")
        }
        if (googleWebClientId.isNullOrBlank()) {
            logger.warn("WARNING: GOOGLE_WEB_CLIENT_ID is not set. Google Sign-In will not work on Wear.")
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
        // (mirrors :androidApp; also keeps the shared google-services.json valid).
        val debug by getting {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        val release by getting {
            // Minification is deliberately OFF for the first Wear cut: the
            // phone app needed hand-tuned R8 keep rules for kotlinx.serialization
            // (ADR-020) and there is no CLI way to verify a minified Wear build
            // end-to-end yet. Enable + port the keep rules before any Play upload.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":usecase"))
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Phone Compose runtime/ui come from the BOM; Wear Compose is versioned
    // separately (androidx.wear.compose is NOT in the compose-bom).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.tooling.preview)
    // kotlin-inject — dedicated WearComponent (mirrors iosFramework's NativeComponent).
    implementation(libs.kotlin.inject.runtime)
    ksp(libs.kotlin.inject.compiler)
    // Firebase Auth (sign-in state + ID tokens for server calls).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    // Credential Manager Sign in with Google (Wear OS 5.1+).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleId)
    // Phone auth relay over the Wearable Data Layer (secondary auth —
    // Credential Manager sign-in fails on some watches).
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":test-common"))
    debugImplementation(libs.androidx.ui.tooling)
}
