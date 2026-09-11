plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.lifeline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.lifeline"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Google Nearby Connections
    implementation("com.google.android.gms:play-services-nearby:19.0.0")

    // Location Services
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Room Database
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // ── Testing ─────────────────────────────────────────────────────────
    // JUnit
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Mockito (for mocking Android classes in unit tests)
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")

    // Robolectric (run Android tests on JVM without device)
    testImplementation("org.robolectric:robolectric:4.11.1")

    // Google Truth (fluent assertions)
    testImplementation("com.google.truth:truth:1.1.5")

    // Coroutines Test (for testing suspend functions)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // AndroidX Test Core + Runner
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")

    // Room Testing (in-memory database + migration helper)
    testImplementation("androidx.room:room-testing:$roomVersion")

    // Architecture Components Testing (InstantTaskExecutorRule for LiveData)
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Activity KTX for viewModels()
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore")

    // Offline Maps (osmdroid)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}
