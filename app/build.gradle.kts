plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.humangodkiller.luvia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.humangodkiller.luvia"
        minSdk = 33
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Firebase BoM – manages all Firebase library versions automatically
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")   // Realtime Database (live features)
    implementation("com.google.firebase:firebase-firestore")  // Firestore (user profiles & roles)

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // RecyclerView for medicine list
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // CardView for material design cards
    implementation("androidx.cardview:cardview:1.0.0")

    // CoordinatorLayout for FAB behavior
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    // HTTP client for Gemini API calls (optional, using HttpURLConnection instead)
    // implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}