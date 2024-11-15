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

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.gms)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

val localPropertiesExplanation = """
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chriscartland.garage"
        minSdk = 26
        targetSdk = 35
        versionCode = 83
        versionName = "2.0-" + generateVersionNameTimestamp()

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
        buildConfigField(
            "String",
            "SERVER_CONFIG_KEY",
            "\"${serverConfigKey}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${googleWebClientId}\"",
        )
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("release/app-debug.jks")
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
        }
        if (rootProject.file("release/app-release.jks").exists()) {
            val releaseKeystorePwd = "GARAGE_RELEASE_KEYSTORE_PWD".let {
                localProperties.getProperty(it) ?: project.findProperty(it) as? String
            }
            val releaseKeyPwd = "GARAGE_RELEASE_KEY_PWD".let {
                localProperties.getProperty(it) ?: project.findProperty(it) as? String
            }
            create("release") {
                storeFile = rootProject.file("release/app-release.jks")
                storePassword = releaseKeystorePwd
                keyAlias = "Garage"
                keyPassword = releaseKeyPwd
            }
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
        // Debug R8 optimizations with isMinifyEnabled = true
        val debugMinify by creating {
            initWith(debug)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "retrofit2.pro",
                "moshi.pro",
            )
            matchingFallbacks += listOf("release", "debug")
        }
        // Turn on R8 optimizations and sign with release key
        val release by getting {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "retrofit2.pro",
                "moshi.pro",
            )
            if (rootProject.file("release/app-release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // Build with debug key that tries to match release properties
        val benchmark by creating {
            initWith(release)
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    applicationVariants.all {
        outputs.map { it as ApkVariantOutputImpl }
            .forEach { output ->
                val variant = this
                output.outputFileName = StringBuilder().run {
                    append(variant.applicationId) // Package name
                    if (variant.flavorName.isNotEmpty()) {
                        append("_").append(variant.flavorName) // Optional flavor
                    }
                    append("_").append(variant.versionName) // Version name
                    append("_").append(variant.versionCode) // Version code
                    if (variant.buildType.name.isNotEmpty()) {
                        append("_").append(variant.buildType.name) // Optional build type
                    }
                    append(".apk")
                    toString()
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

fun generateVersionNameTimestamp(): String {
    val currentDateTime = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss")
    return currentDateTime.format(formatter)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Tracing
    implementation(libs.androidx.tracing)
    // Accompanist
    implementation(libs.accompanist.permissions)
    // Baseline Profiles
    implementation(libs.androidx.profileinstaller)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.auth)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Retrofit
    implementation(libs.squareup.moshi)
    implementation(libs.squareup.moshi.kotlin)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.moshi.converter)
    implementation(libs.okhttpLoggingInterceptor)
    ksp(libs.squareup.moshi.kotlin.codegen)
    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    // Room
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.messaging)
    // Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleId)
}
