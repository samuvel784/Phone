import java.io.File
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

val releaseKeystorePath = signingProperties.getProperty("storeFile")
val releaseStorePassword = signingProperties.getProperty("storePassword")
val releaseKeyAlias = signingProperties.getProperty("keyAlias")
val releaseKeyPassword = signingProperties.getProperty("keyPassword")

val releaseKeystoreFile = releaseKeystorePath
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }

val hasReleaseSigningCredentials =
    releaseKeystoreFile != null &&
    releaseKeystoreFile.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val requestedTasks = gradle.startParameter.taskNames.joinToString(" ")
val isDebugBuild = requestedTasks.let {
    it.contains("Debug", ignoreCase = true) ||
    it.contains("test", ignoreCase = true)
}
val isBundleTask = requestedTasks.contains("bundle", ignoreCase = true)
val isReleaseTask = requestedTasks.contains("release", ignoreCase = true)

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
        versionCode = 4
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // APK builds can use ABI splits. App Bundles must not generate
    // multiple ABI-split APK resource outputs during bundle processing.
    splits {
        abi {
            isEnable = !isDebugBuild && !isBundleTask
            reset()
            include(
                "armeabi-v7a",
                "arm64-v8a",
                "x86",
                "x86_64"
            )
            isUniversalApk = true
        }
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
                "proguard-rules.pro"
            )

            if (hasReleaseSigningCredentials) {
                signingConfig = signingConfigs.getByName("release")
            } else if (isReleaseTask) {
                throw GradleException(
                    "Release build failed: Missing or invalid required release signing credentials " +
                    "(storeFile, storePassword, keyAlias, keyPassword in key.properties)."
                )
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
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExtraTranslation"
        disable += "MissingTranslation"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
    implementation(libs.androidx.paging.compose)
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
