plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.markmusic.player"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.markmusic.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 26
        versionName = "0.7.8"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val media3 = "1.11.1"
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
    
// The Mayak image is a real public-domain photograph, downloaded only when
// building the APK; the installed music player remains fully offline.
val prepareMayakPhoto by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/fetch-mayak-photo.py")
    outputs.file("src/main/res/drawable-nodpi/mayak_photo.jpg")
}
tasks.named("preBuild").configure { dependsOn(prepareMayakPhoto) }
