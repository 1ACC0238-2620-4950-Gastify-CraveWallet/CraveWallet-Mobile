plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cravewallet.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cravewallet.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        val apiUrl = providers.gradleProperty("API_BASE_URL").getOrElse("https://cravewallet-api.onrender.com")
        buildConfigField("String", "API_BASE_URL", "\"${apiUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

android.testOptions.unitTests.isIncludeAndroidResources = true

// Changing the target server must rerun the real integration test.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val liveBackend = providers.environmentVariable("CRAVE_LIVE_BACKEND").orElse("")
    inputs.property("liveBackend", liveBackend)
    environment("CRAVE_LIVE_BACKEND", liveBackend.get())
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
}
