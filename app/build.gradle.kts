plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fundosa.fundosatv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fundosa.fundosatv"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Glide for image loading
    implementation ("com.github.bumptech.glide:glide:4.16.0") // 使用 Glide 的最新稳定版
    // annotationProcessor 'com.github.bumptech.glide:compiler:4.12.0' // 如果使用 GlideApp

    // Kotlin Coroutines for background tasks
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // 或最新版
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // 或最新版

    // Lifecycle KTX for lifecycleScope
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // 或最新版

    // OkHttp for network requests
    implementation ("com.squareup.okhttp3:okhttp:4.12.0") // 或最新版

    // JSON parsing (Android自带的 org.json)
    // 无需额外添加，但要注意使用
}