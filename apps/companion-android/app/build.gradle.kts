import java.io.File

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
  versionCode = 3
  versionName = "0.2.1-native-alpha"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
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
 testImplementation("junit:junit:4.13.2")
}
