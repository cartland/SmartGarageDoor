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

// Compose preview rendering happens inside the test worker JVM (the
// "Layoutlib Render Thread"). The default test heap is too small for the
// preview set in this module — `ComponentsScreenshotTest` OOMed at 2 GB,
// then again at 4 GB. Bump the test worker heap and add a Metaspace cap
// so per-class metadata doesn't accumulate across the long suite.
tasks.withType<Test>().configureEach {
    maxHeapSize = "4g"
    jvmArgs("-XX:MaxMetaspaceSize=1g")
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
    val taskName = name
    if (taskName in listOf("updateDebugScreenshotTest", "validateDebugScreenshotTest")) {
        // Capture flag/property state at configuration time so the
        // configuration cache can serialize the doFirst action. AGP's
        // screenshot tasks aren't `Test` subclasses, so the standard
        // filter API is unavailable — read `--tests` from the raw
        // start-parameter task-request args instead.
        val isSequentialScript = project.hasProperty("retainedReferenceScreenshots")
        val isForced = project.hasProperty("forceAllScreenshots")
        val passedTestsArg = gradle.startParameter.taskRequests
            .flatMap { it.args }
            .contains("--tests")
        val refDirCapture = file("src/screenshotTestDebug/reference")

        doFirst("Screenshot OOM gate") {
            if (!isSequentialScript && !isForced && !passedTestsArg) {
                error(
                    """

                    ===========================================================
                    BLOCKED: Running all screenshot tests in a single Gradle
                    invocation may cause OutOfMemoryError.
                    ===========================================================

                    Use the sequential script for the full suite:
                      ./scripts/generate-android-screenshots.sh

                    Or target one class with --tests (no property needed):
                      ./gradlew :android-screenshot-tests:$taskName \
                        --tests com.chriscartland.garage.screenshottests.HomeRedesignScreenshotTestKt

                    To force the full single-invocation run, add:
                      -PforceAllScreenshots

                    ===========================================================

                    """.trimIndent(),
                )
            }

            // Selective clean: only wipe ALL references on a full update.
            // Subset runs (`--tests`) and the sequential script preserve siblings.
            if (taskName == "updateDebugScreenshotTest" &&
                !isSequentialScript && !passedTestsArg
            ) {
                if (refDirCapture.exists()) {
                    refDirCapture.deleteRecursively()
                    println("Deleted reference screenshots: $refDirCapture")
                }
            }
        }
    }
}
