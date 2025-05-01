dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven("https://repo.jpenilla.xyz/snapshots/") {
      mavenContent {
        includeGroup("xyz.jpenilla")
        snapshotsOnly()
      }
    }
    maven("https://maven.fabricmc.net/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
      mavenContent { snapshotsOnly() }
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
      mavenContent { snapshotsOnly() }
    }
  }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.jpenilla.xyz/snapshots/") {
      mavenContent { snapshotsOnly() }
    }
  }
}

plugins {
  id("quiet-fabric-loom") version "1.10-SNAPSHOT"
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "better-fabric-console"
