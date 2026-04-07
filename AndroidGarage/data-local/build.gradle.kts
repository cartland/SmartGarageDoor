import importboundary.ImportBoundaryCheckTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.room)
    alias(libs.plugins.google.devtools.ksp)
}

tasks.register<ImportBoundaryCheckTask>("checkImportBoundary") {
    sourceDir = "$projectDir/src/commonMain/kotlin"
    allowedPrefixes = listOf(
        "androidx.room.",
        "androidx.sqlite.",
        "androidx.datastore.",
    )
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            implementation(project(":data"))
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.chriscartland.garage.datalocal"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
