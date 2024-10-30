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
import java.io.FileInputStream
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
}

println(rootProject.file("release/app-release.jks").exists())

// local.properties
// PRIORITIZE_LOCAL_PROPERTIES=true
// SERVER_CONFIG_KEY=YourKey
// GOOGLE_WEB_CLIENT_ID=YourClientId
// GARAGE_RELEASE_KEYSTORE_PWD=YourKeystorePassword
// GARAGE_RELEASE_KEY_PWD=YourKeyPassword
class PropertyFinder(private val project: Project, private val localProperties: Properties) {
    /**
     * Find the build property from config files or command line arguments.
     *
     * Default:
     * - gradle command line argument: gradlew -PKEY_NAME=value
     * - gradle.properties: KEY_NAME=value
     *
     * @param name The name of the property.
     * @param default Default: null. The default value to use if the property is not found.
     * @param prioritizeLocal Default: false. Whether use check the local.properties file first.
     */
    fun find(
        name: String,
        default: String? = null,
        prioritizeLocal: Boolean = false,
    ): String? {
        fun getLocal(): String? {
            if (localProperties.containsKey(name)) {
                return localProperties.getProperty(name)
            } else {
                return null
            }
        }
        fun getProject(): String? {
            // This will prioritize command line arguments over gradle.properties
            return (project.findProperty(name) as? String)
        }
        return if (prioritizeLocal) {
            getLocal() ?: getProject() ?: default
        } else {
            getProject() ?: getLocal() ?: default
        }
    }
}

val localProperties = Properties().apply {
    load(FileInputStream(File(rootProject.rootDir, "local.properties")))
}
val finder = PropertyFinder(project = project, localProperties = localProperties)

val useLocalProperties = finder.find("PRIORITIZE_LOCAL_PROPERTIES", default = "false").toBoolean()
val serverConfigKey = finder.find("SERVER_CONFIG_KEY", prioritizeLocal = useLocalProperties)
val googleWebClientId = finder.find("GOOGLE_WEB_CLIENT_ID", prioritizeLocal = useLocalProperties)

android {
    namespace = "com.chriscartland.garage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chriscartland.garage"
        minSdk = 26
        targetSdk = 35
        versionCode = 56
        versionName = "2.0-" + generateVersionNameTime()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
            create("release") {
                storeFile = rootProject.file("release/app-release.jks")
                storePassword = finder.find("GARAGE_RELEASE_KEYSTORE_PWD").orEmpty()
                keyAlias = "Garage"
                keyPassword = finder.find("GARAGE_RELEASE_KEY_PWD").orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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

fun generateVersionNameTime(): String {
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
    // Accompanist
    implementation(libs.accompanist.permissions)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.auth)
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Retrofit
    implementation(libs.squareup.moshi)
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
