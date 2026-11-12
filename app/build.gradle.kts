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
        versionCode = 5
        versionName = "0.5.0"
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

val generateBrandLogo by tasks.registering {
    val encodedLogo = layout.projectDirectory.file("src/main/assets/mark_audio_logo.webp.b64")
    val generatedLogo = layout.buildDirectory.file("generated/brand-res/drawable-nodpi/mark_logo.webp")
    inputs.file(encodedLogo)
    outputs.file(generatedLogo)
    doLast {
        val target = generatedLogo.get().asFile
        target.parentFile.mkdirs()
        target.writeBytes(Base64.getDecoder().decode(encodedLogo.asFile.readText().trim()))
    }
}
tasks.named("preBuild").configure { dependsOn(generateBrandLogo) }
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