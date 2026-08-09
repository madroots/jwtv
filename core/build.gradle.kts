plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-kapt")
}

android {
    namespace = "org.jw.tv.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation("androidx.core:core-ktx:1.13.1")
    // AndroidViewModel + viewModelScope for MainViewModel.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")

    // Compose runtime only: MainViewModel holds @Immutable / mutableStateOf state.
    // No compose-ui, no androidx.tv — this module stays platform-neutral.
    implementation("androidx.compose.runtime:runtime:1.6.8")

    // Coil — api: createAppImageLoader returns coil.ImageLoader to consumers.
    api("io.coil-kt:coil:2.6.0")

    // OkHttp + Serialization.
    // `api` for types that leak into consumer compile classpaths:
    // app UI calls MediatorClient.json (kotlinx.serialization.json.Json) and
    // launches coroutines on DAO suspend functions.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room Database — api: consumers compile against AppDatabase (a RoomDatabase
    // subtype) and DAO interfaces.
    val roomVersion = "2.6.1"
    api("androidx.room:room-runtime:$roomVersion")
    api("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
