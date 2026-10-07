plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

// ─── Version: read from gradle.properties (overridable from CI) ───────────────
val appVersionName: String = project.findProperty("APP_VERSION_NAME") as String? ?: "0.1.0"
val appVersionCode: Int    = (project.findProperty("APP_VERSION_CODE") as String?)?.toInt() ?: 100

android {
    namespace = "com.datathrottle"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.datathrottle"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose version to BuildConfig for use in UI
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
    }

    val keystorePath = project.findProperty("KEYSTORE_FILE") as String? ?: System.getenv("KEYSTORE_FILE")
    val hasReleaseKeystore = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = project.findProperty("STORE_PASSWORD") as String? ?: System.getenv("STORE_PASSWORD")
                keyAlias = project.findProperty("KEY_ALIAS") as String? ?: System.getenv("KEY_ALIAS")
                keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Tree-shake dependencies (notably material-icons-extended) and keep
            // installs small/fast. debuggable builds stay unobfuscated (R8 keeps
            // line numbers + coroutine debug probes).
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                // Test devices are arm64; shipping 4 ABIs triples the native payload.
                abiFilters += "arm64-v8a"
            }
        }
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true  // Enable BuildConfig generation
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.okhttp)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}