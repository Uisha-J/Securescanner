plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") // 최신 버전에서는 alias(libs.plugins.google.devtools.ksp) 로 쓰는 것이 더 좋습니다.
}

android {
    namespace = "com.example.securescanner"
    compileSdk = 34 // compileSdk 36은 아직 릴리즈되지 않았으므로 안정적인 34로 변경합니다.

    defaultConfig {
        applicationId = "com.example.securescanner"
        minSdk = 26
        targetSdk = 34 // targetSdk도 compileSdk와 맞춥니다.
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
        sourceCompatibility = JavaVersion.VERSION_1_8 // Room, KSP 와의 호환성을 위해 1.8 또는 11 을 많이 사용합니다. 11도 괜찮습니다.
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
