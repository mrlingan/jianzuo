plugins { id("com.android.application") }
android {
    namespace = "com.jianzuo.writing"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.jianzuo.writing"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "1.12.0"
        resValue("string", "app_version", versionName!!)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { resValues = true }
    bundle { language { enableSplit = false } } // Both interface languages must be available offline.
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
