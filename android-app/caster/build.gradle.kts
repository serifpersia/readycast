plugins {
    id("java-library")
}

import java.util.Properties

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

fun findSdk(): File {
    for (env in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
        val v = System.getenv(env)
        if (!v.isNullOrBlank() && File(v).isDirectory) return File(v)
    }
    val props = Properties()
    rootProject.file("local.properties").inputStream().use { props.load(it) }
    return File(props.getProperty("sdk.dir") as String? ?: error("Android SDK not found: set ANDROID_HOME or sdk.dir"))
}

val sdk = findSdk()
val androidJar = sdk.resolve("platforms")
    .listFiles { f -> f.isDirectory && f.name.startsWith("android-") }
    ?.map { it.resolve("android.jar") }
    ?.filter { it.isFile }
    ?.maxByOrNull { it.parentFile.name }
    ?: error("android.jar not found under $sdk/platforms")
val d8 = sdk.resolve("build-tools")
    .listFiles { f -> f.isDirectory }
    ?.maxByOrNull { it.name }
    ?.resolve(if (System.getProperty("os.name").startsWith("Windows")) "d8.bat" else "d8")
    ?.takeIf { it.isFile }
    ?: error("d8 not found under $sdk/build-tools")

dependencies {
    compileOnly(files(androidJar))
}

tasks.register<Exec>("dexCaster") {
    dependsOn(tasks.named("jar"))
    val outDir = layout.buildDirectory.dir("dex").get().asFile
    doFirst { outDir.mkdirs() }
    val inputJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile
    commandLine(
        d8.absolutePath, "--lib", androidJar.absolutePath,
        "--min-api", "26", "--output", outDir.absolutePath,
        inputJar.absolutePath
    )
}

tasks.register<Jar>("casterServer") {
    dependsOn("dexCaster")
    from(layout.buildDirectory.dir("dex"))
    archiveFileName.set("caster-server")
    archiveExtension.set("")
    destinationDirectory.set(rootProject.project(":app").layout.buildDirectory.dir("generated/caster"))
}
