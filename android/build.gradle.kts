plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

// Keep generated binaries out of cloud-synced source folders when configured locally.
val localConfig = java.util.Properties().apply {
    file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
localConfig.getProperty("cadence.buildRoot")?.let { buildRoot ->
    layout.buildDirectory.set(file("$buildRoot/root"))
    subprojects { layout.buildDirectory.set(file("$buildRoot/$name")) }
}
