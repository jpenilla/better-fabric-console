dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://repo.jpenilla.xyz/snapshots/") {
      mavenContent {
        snapshotsOnly()
        includeGroup("xyz.jpenilla")
      }
    }
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      mavenContent { snapshotsOnly() }
    }
    maven("https://maven.fabricmc.net/")
  }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.jpenilla.xyz/snapshots/")
  }
}

plugins {
  id("xyz.jpenilla.quiet-fabric-loom") version "1.15-SNAPSHOT" apply false
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

plugins.apply(net.fabricmc.loom.LoomRepositoryPlugin::class.java)

rootProject.name = "better-fabric-console"
