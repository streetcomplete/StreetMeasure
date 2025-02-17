plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "de.westnordost.streetmeasure"
    compileSdk = 35

    signingConfigs {
        create("release") {
            keyAlias = properties["streetmeasureKeyAlias"] as String?
            storePassword = properties["streetmeasureStorePassword"] as String?
            keyPassword = properties["streetmeasureKeyPassword"] as String?
            storeFile = file(properties["streetmeasureStoreFile"] as String)
        }
    }

    defaultConfig {
        applicationId = "de.westnordost.streetmeasure"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    val kotlinxCoroutinesVersion = "1.10.1"

    // core android stuff
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")

    // measuring distance with AR
    implementation("com.google.ar:core:1.47.0")
    implementation("com.google.ar.sceneform:core:1.17.1")
}

tasks.register<UpdateAppTranslationsTask>("updateTranslations") {
    group = "streetmeasure"
    apiToken = properties["POEditorAPIToken"] as String
    projectId = "97843"
    targetFiles = { "$projectDir/src/main/res/values${if (it.isNotEmpty()) "-$it" else ""}/strings.xml" }
    strings = setOf(
        "ar_core_error_sdk_too_old",
        "ar_core_tracking_error_bad_state",
        "ar_core_tracking_error_insufficient_light",
        "ar_core_tracking_error_excessive_motion",
        "ar_core_tracking_error_insufficient_features",
        "ar_core_tracking_error_camera_unavailable",
        "ar_core_tracking_error_too_steep_angle",
        "ar_core_tracking_error_no_plane_hit",
        "ar_core_tracking_hint_tap_to_measure",
        "no_camera_permission_warning_title",
        "no_camera_permission_warning",
        "no_camera_permission_toast",
        "about_title_privacy_statement",
        "privacy_html_arcore",
        "measure_info_title",
        "measure_info_html_description"
    )
}