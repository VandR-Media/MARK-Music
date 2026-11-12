import java.util.Base64

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
        versionCode = 6
        versionName = "0.5.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/brand-res"))
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val generateBrandArtwork by tasks.registering {
    val encodedLogo = layout.projectDirectory.file("src/main/assets/mark_audio_logo.webp.b64")
    val encodedWelcome = layout.projectDirectory.file("src/main/assets/mark_welcome.webp.b64")
    val generatedDir = layout.buildDirectory.dir("generated/brand-res/drawable-nodpi")
    val generatedLogo = generatedDir.map { it.file("mark_logo.webp") }
    val generatedWelcome = generatedDir.map { it.file("mark_welcome.webp") }
    inputs.files(encodedLogo, encodedWelcome)
    outputs.files(generatedLogo, generatedWelcome)
    doLast {
        val targetDir = generatedDir.get().asFile
        targetDir.mkdirs()
        targetDir.resolve("mark_logo.webp").writeBytes(
            Base64.getDecoder().decode(encodedLogo.asFile.readText().trim())
        )
        targetDir.resolve("mark_welcome.webp").writeBytes(
            Base64.getDecoder().decode(encodedWelcome.asFile.readText().trim())
        )
    }
}
tasks.named("preBuild").configure { dependsOn(generateBrandArtwork) }
dependencies {
    val media3 = "1.11.1"
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}