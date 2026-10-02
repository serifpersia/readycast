plugins {
    id("com.android.library")
}

android {
    namespace = "com.connectsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    useLibrary("org.apache.http.legacy")

    buildFeatures {
        buildConfig = true  // AGP 8 dropped it by default, upstream code uses BuildConfig
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src")
            manifest.srcFile("AndroidManifest.xml")
            res.srcDirs("res")
            jniLibs.srcDirs("jniLibs")
        }
    }
}

dependencies {
    implementation(files("libs/lgcast-android-lib.jar"))
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("javax.jmdns:jmdns:3.4.1")
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.googlecode.plist:dd-plist:1.23")
    implementation("com.nimbusds:srp6a:2.1.0")
    implementation("net.i2p.crypto:eddsa:0.3.0")
}
