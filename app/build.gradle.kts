plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

android { namespace = "com.arthur.ereader"; compileSdk = 36
    compileSdkExtension = 19
    defaultConfig { applicationId = "com.arthur.ereader"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // Kept behind PdfContentExtractor because AndroidX PDF is still alpha.
    implementation("androidx.pdf:pdf-core:1.0.0-alpha19")
    implementation("androidx.pdf:pdf-document-service:1.0.0-alpha19")
    implementation("androidx.compose.material3:material3")
    // The manifest theme is an XML Material Components theme; Compose Material 3 alone does not provide it.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1"); ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("org.readium.kotlin-toolkit:readium-shared:3.0.3")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.0.3")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.0.3")
    implementation("com.google.dagger:hilt-android:2.52"); kapt("com.google.dagger:hilt-compiler:2.52"); implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01")); androidTestImplementation("androidx.test.ext:junit:1.2.1"); androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
