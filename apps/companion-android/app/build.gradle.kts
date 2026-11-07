import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import groovy.json.JsonSlurper

plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
val nexusPreview = providers.gradleProperty("nexusPreview").getOrElse("false").toBooleanStrict()
val frenchModelId = "vosk-model-small-fr-0.22"
val frenchModelSha256 = "cabf6180e177eb9b3a9a9d43a437bd5e549f3a7d09525e5d69a3fed787be12ad"
val frenchModelBytes = 42233323L
val frenchModelAssets = layout.buildDirectory.dir("generated/assets/french-recognition")
android {
 namespace = "fr.nexuscompanion.android"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig {
  applicationId = if (nexusPreview) "fr.nexuscompanion.android.preview" else "fr.nexuscompanion.android"
  minSdk = 31
  targetSdk = 35
  versionCode = 12
  versionName = "0.4.6-native-alpha" + if (nexusPreview) "-preview" else ""
  ndk { abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) }
  buildConfigField("boolean", "IS_PREVIEW", nexusPreview.toString())
  buildConfigField("String", "FRENCH_MODEL_ID", "\"$frenchModelId\"")
  buildConfigField("String", "FRENCH_MODEL_CACHE_ID", "\"$frenchModelId-${frenchModelSha256.take(12)}\"")
  manifestPlaceholders["nexusLabel"] = if (nexusPreview) "Nexus Companion Test" else "@string/app_name"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { buildConfig = true }
 sourceSets.getByName("main").assets.srcDir(frenchModelAssets)
 // Keep the universal APK small enough for the installed app's 64 MiB update limit.
 packaging.jniLibs.useLegacyPackaging = true
 testOptions { unitTests.isIncludeAndroidResources = true }
 val releaseKeystore = providers.environmentVariable("NEXUS_ANDROID_KEYSTORE_PATH").orNull
 val releaseStorePassword = providers.environmentVariable("NEXUS_ANDROID_STORE_PASSWORD").orNull
 val releaseKeyAlias = providers.environmentVariable("NEXUS_ANDROID_KEY_ALIAS").orNull
 val releaseKeyPassword = providers.environmentVariable("NEXUS_ANDROID_KEY_PASSWORD").orNull
 val hasReleaseKey = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
 val hasAnyReleaseKey = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).any { !it.isNullOrBlank() }
 require(!hasAnyReleaseKey || hasReleaseKey) { "Incomplete release signing configuration" }
 if (hasReleaseKey) {
  signingConfigs {
   create("nexusRelease") {
    storeFile = File(releaseKeystore!!)
    storePassword = releaseStorePassword!!
    keyAlias = releaseKeyAlias!!
    keyPassword = releaseKeyPassword!!
   }
  }
 }
 buildTypes {
  getByName("release") {
   if (hasReleaseKey) signingConfig = signingConfigs.getByName("nexusRelease")
  }
 }

}

dependencies {
 implementation("androidx.core:core:1.15.0")
 implementation("org.mozilla:rhino:1.9.1")
 implementation("net.java.dev.jna:jna:5.18.1@aar")
 implementation("com.alphacephei:vosk-android:0.3.75@aar")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.17")
}

val prepareFrenchRecognitionModel by tasks.registering {
 val output = frenchModelAssets.map { it.file("speech/$frenchModelId.zip") }
 inputs.property("sha256", frenchModelSha256)
 outputs.file(output)
 doLast {
  fun valid(file: File): Boolean {
   if (!file.isFile || file.length() != frenchModelBytes) return false
   val digest = MessageDigest.getInstance("SHA-256")
   file.inputStream().buffered().use { input ->
    val buffer = ByteArray(65536)
    while (true) {
     val count = input.read(buffer)
     if (count < 0) break
     digest.update(buffer, 0, count)
    }
   }
   return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == frenchModelSha256
  }
  val cached = gradle.gradleUserHomeDir.resolve("nexus-companion-models/$frenchModelId-$frenchModelSha256.zip")
  if (!valid(cached)) {
   cached.parentFile.mkdirs()
   val temp = Files.createTempFile(cached.parentFile.toPath(), "$frenchModelId-", ".part").toFile()
   try {
    val connection = URI("https://alphacephei.com/vosk/models/$frenchModelId.zip").toURL().openConnection().apply {
     connectTimeout = 20_000
     readTimeout = 30_000
    }
    connection.getInputStream().buffered().use { input ->
     temp.outputStream().buffered().use { stream ->
      val buffer = ByteArray(65536)
      var length = 0L
      while (true) {
       val count = input.read(buffer)
       if (count < 0) break
       length += count
       require(length <= frenchModelBytes) { "Unexpected French recognition model size" }
       stream.write(buffer, 0, count)
      }
     }
    }
    require(valid(temp)) { "Invalid French recognition model checksum" }
    Files.move(temp.toPath(), cached.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
   } finally { temp.delete() }
  }
  val asset = output.get().asFile
  asset.parentFile.mkdirs()
  cached.copyTo(asset, overwrite = true)
 }
}

tasks.withType<Test>().configureEach {
 systemProperty("nexus.brain.asset", layout.projectDirectory.file("src/main/assets/brain/nexus-brain.js").asFile.absolutePath)
 systemProperty("robolectric.dependency.repo.url", System.getProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2"))
 listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { key ->
  System.getProperty(key)?.let { systemProperty(key, it) }
 }
 jvmArgs(
  "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
  "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
  "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
  "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
 )
}

val verifyNativeBrain by tasks.registering {
 val repo = rootProject.projectDir.resolve("../..").canonicalFile
 val asset = layout.projectDirectory.file("src/main/assets/brain/nexus-brain.js").asFile
 val manifest = layout.projectDirectory.file("src/main/assets/brain/manifest.json").asFile
 inputs.files(asset, manifest)
 inputs.dir(repo.resolve("packages/companion-core/src"))
 inputs.files(repo.resolve("scripts/build-native-brain.mjs"), repo.resolve("package.json"), repo.resolve("package-lock.json"))
 doLast {
  fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
  val metadata = JsonSlurper().parseText(manifest.readText()) as Map<*, *>
  require(digest(asset) == metadata["sha256"]) { "Android brain asset is invalid. Run npm run build:native-brain." }
  val sources = metadata["sources"] as Map<*, *>
  require(sources.isNotEmpty())
  sources.forEach { (name, expected) ->
   val source = repo.resolve(name as String).canonicalFile
   require(source.toPath().startsWith(repo.toPath()) && source.isFile && digest(source) == expected) {
    "Android brain is stale. Use the full repository and run npm run build:native-brain."
   }
  }
 }
}
tasks.named("preBuild") { dependsOn(verifyNativeBrain, prepareFrenchRecognitionModel) }
