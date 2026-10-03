plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val readycastVersionName = "1.0.3"
val readycastVersionCode = 4

android {
    namespace = "app.readycast"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.readycast"
        minSdk = 26
        targetSdk = 35
        versionCode = readycastVersionCode
        versionName = readycastVersionName

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    signingConfigs {
        create("release") {
            val store = rootProject.file("readycast-release.keystore")
            if (store.exists()) {
                storeFile = store
                storePassword = "readycast"
                keyAlias = "readycast"
                keyPassword = "readycast"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (rootProject.file("readycast-release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName =
                "com.serifpersia.readycast-$readycastVersionName-${buildType.name}.apk"
        }
    }

    sourceSets.getByName("main").assets.srcDir("${layout.buildDirectory.get().asFile}/generated/caster")
}

tasks.named("preBuild") { dependsOn(":caster:casterServer") }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(project(":connectsdk"))
    implementation("dev.rikka.shizuku:api:11.0.2")
    implementation("dev.rikka.shizuku:provider:11.0.2")
}
