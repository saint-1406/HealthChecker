import java.util.Properties
plugins { alias(libs.plugins.android); alias(libs.plugins.compose); alias(libs.plugins.serialization); alias(libs.plugins.ksp) }
val signing = Properties().apply { rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
    namespace = "az.healthchecker"
    compileSdk = 36
    defaultConfig {
        applicationId = "az.healthchecker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        // The release number (v11 → "11"), so Android's app info and the profile tell the user which update is installed.
        versionName = providers.gradleProperty("versionName").orNull ?: versionCode.toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs { create("localRelease") { if(signing.isNotEmpty()) { storeFile = file(signing.getProperty("storeFile")); storePassword = signing.getProperty("storePassword"); keyAlias = signing.getProperty("keyAlias"); keyPassword = signing.getProperty("keyPassword") } } }
    buildTypes { release {
        // R8 strips unused code (most of material-icons-extended) and Compose debug paths; keep rules are in proguard-rules.pro.
        isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("localRelease")
    } }
    buildFeatures { compose = true }
    // The language is switched inside the app, so every bundle must carry both Russian and English resources.
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
