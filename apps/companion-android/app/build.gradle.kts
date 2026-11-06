import java.io.File
import java.security.MessageDigest
import groovy.json.JsonSlurper

plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "fr.nexuscompanion.android"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig {
  applicationId = "fr.nexuscompanion.android"
  minSdk = 31
  targetSdk = 35
  versionCode = 6
  versionName = "0.4.0-native-alpha"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { buildConfig = true }
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
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.17")
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
tasks.named("preBuild") { dependsOn(verifyNativeBrain) }
