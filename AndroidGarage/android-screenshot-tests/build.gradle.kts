plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.chriscartland.garage.screenshottests"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.test.junit4)
    screenshotTestImplementation(libs.androidx.ui.test.manifest)

    implementation(project(":androidApp"))
    implementation(project(":domain"))
}

tasks.register("cleanReferenceScreenshots") {
    group = "screenshot"
    description = "Cleans the reference screenshots directory."
    doLast {
        val referenceDir = file("src/screenshotTestDebug/reference")
        if (referenceDir.exists()) {
            referenceDir.deleteRecursively()
            println("Deleted reference screenshots: $referenceDir")
        }
    }
}

tasks.register<Exec>("generateScreenshotGallery") {
    group = "screenshot"
    description = "Generates a Markdown gallery of all reference screenshots."
    workingDir = rootProject.projectDir.parentFile
    commandLine("./scripts/generate-android-screenshot-gallery.sh")
}

tasks.whenTaskAdded {
    if (name == "updateDebugScreenshotTest") {
        if (!project.hasProperty("retainedReferenceScreenshots")) {
            dependsOn("cleanReferenceScreenshots")
        }
    }
    if (name in listOf("updateDebugScreenshotTest", "validateDebugScreenshotTest")) {
        doFirst {
            val isSequentialScript = project.hasProperty("retainedReferenceScreenshots")
            val isForced = project.hasProperty("forceAllScreenshots")
            if (!isSequentialScript && !isForced) {
                error(
                    """

                    ===========================================================
                    BLOCKED: Running all screenshot tests in a single Gradle
                    invocation may cause OutOfMemoryError.
                    ===========================================================

                    Use the sequential script instead:
                      ./scripts/generate-android-screenshots.sh

                    To force a single-invocation run, add:
                      -PforceAllScreenshots

                    ===========================================================

                    """.trimIndent(),
                )
            }
        }
    }
}
