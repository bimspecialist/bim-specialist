import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A signed build is only available when CI provides a persistent, private keystore.
val bimSigningEnabled = listOf(
    "BIM_ANDROID_KEYSTORE_PATH", "BIM_ANDROID_STORE_PASSWORD",
    "BIM_ANDROID_KEY_ALIAS", "BIM_ANDROID_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bimspecialist.measure3d"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.bimspecialist.measure3d"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.1-beta"
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    signingConfigs {
        if (bimSigningEnabled) {
            create("bimProduction") {
                storeFile = file(System.getenv("BIM_ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("BIM_ANDROID_STORE_PASSWORD")
                keyAlias = System.getenv("BIM_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("BIM_ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (bimSigningEnabled) signingConfig = signingConfigs.getByName("bimProduction")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("io.github.sceneview:arsceneview:4.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
}
