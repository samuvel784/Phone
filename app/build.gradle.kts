import java.io.File
import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
}

// Read local release-signing credentials securely from key.properties
val signingPropertiesFile = rootProject.file("key.properties")
val signingProperties = Properties()

if (signingPropertiesFile.exists() && signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use { input ->
        signingProperties.load(input)
    }
}

val releaseKeystorePath: String? = signingProperties.getProperty("storeFile")
val releaseStorePassword: String? = signingProperties.getProperty("storePassword")
val releaseKeyAlias: String? = signingProperties.getProperty("keyAlias")
val releaseKeyPassword: String? = signingProperties.getProperty("keyPassword")

val releaseKeystoreFile = releaseKeystorePath
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }

val hasReleaseSigningCredentials =
    ((releaseKeystoreFile != null) &&
        releaseKeystoreFile.exists() &&
        (!releaseStorePassword.isNullOrBlank()) &&
        (!releaseKeyAlias.isNullOrBlank()) &&
        (!releaseKeyPassword.isNullOrBlank()))

android {
    namespace = "com.example"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.novadial.kpxm"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningCredentials && releaseKeystoreFile != null) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isCrunchPngs = true
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Release builds must fail on lint errors, and all shipped translations must stay
        // complete. Warnings remain visible in reports without being silently promoted.
        checkReleaseBuilds = true
        abortOnError = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// A single hung Robolectric test used to block the build for hours (no JUnit timeouts were set).
// A hard task timeout turns that into a clear failure instead of a silent 5-hour run.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(25))
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Validate by exact packaging task, not by substring-matching whatever the user typed on the
// command line. This keeps release/test aggregate invocations from being misclassified while
// still failing closed before an unsigned release artifact can be produced.
val releasePackagingTaskNames = setOf(
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "packageReleaseBundle",
    "signReleaseBundle"
)
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    description = "Validates release signing credentials for release packaging tasks."
    // Store the result as a task input so the action never captures the Gradle script object.
    inputs.property("signingCredentialsPresent", hasReleaseSigningCredentials)
    doLast {
        if (!(inputs.properties["signingCredentialsPresent"] as Boolean)) {
            throw GradleException(
                "Release build failed: Missing or invalid required release signing credentials " +
                    "(storeFile, storePassword, keyAlias, keyPassword in key.properties)."
            )
        }
    }
}
val releaseSigningAvailable = hasReleaseSigningCredentials
tasks.configureEach {
    if (name in releasePackagingTaskNames) {
        dependsOn(validateReleaseSigning)
        if (!releaseSigningAvailable) {
            doFirst {
                throw GradleException(
                    "Release build failed: Missing or invalid required release signing credentials " +
                        "(storeFile, storePassword, keyAlias, keyPassword in key.properties)."
                )
            }
        }
    }
}

ksp {
    // Keep Room schemas available for migration review and testing.
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    resolutionStrategy {
        force("org.bouncycastle:bcprov-jdk18on:1.84")
        force("io.netty:netty-codec-http2:4.1.118.Final")
        force("io.netty:netty-codec-http:4.1.118.Final")
        force("io.netty:netty-handler:4.1.118.Final")
        force("io.netty:netty-codec:4.1.118.Final")
        force("io.netty:netty-common:4.1.118.Final")
        force("io.netty:netty-buffer:4.1.118.Final")
        force("io.netty:netty-transport:4.1.118.Final")
        force("org.apache.httpcomponents:httpclient:4.5.14")
        force("org.apache.commons:commons-lang3:3.14.0")
        force("org.jdom:jdom2:2.0.6.1")
        force("org.bitbucket.b_c:jose4j:0.9.6")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)

    // Baseline profile installation for startup and scrolling performance.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.sqlcipher)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    ksp(libs.androidx.room.compiler)
}
