plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.jw.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.jw.phone"
        minSdk = 21
        targetSdk = 35
        // Local builds use these defaults. Release builds get both values from
        // the git tag via -PreleaseVersionCode / -PreleaseVersionName
        // (.github/workflows/release.yml), shared with :app.
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // Signed with the debug key so the release artifact installs over
            // earlier releases via the self-update channel (same as :app).
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
        // Skips recomposition of composables with unstable params (e.g. the
        // ViewModel) and memoizes lambdas: fewer recompositions per frame.
        freeCompilerArgs += listOf(
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Shared platform-neutral code: api/, data/, MainViewModel, UpdateManager,
    // createAppImageLoader. Room/coroutines/serialization/coil come from here.
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose (touch UI — Material3, NO androidx.tv dependencies)
    val composeVersion = "1.6.8"
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeVersion")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:$composeVersion")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Media3 (ExoPlayer)
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
}
