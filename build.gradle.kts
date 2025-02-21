import me.modmuss50.mpp.ReleaseType

plugins {
  val indraVersion = "3.1.3"
  id("net.kyori.indra") version indraVersion
  id("net.kyori.indra.checkstyle") version indraVersion
  id("net.kyori.indra.license-header") version indraVersion
  id("quiet-fabric-loom")
  id("me.modmuss50.mod-publish-plugin") version "0.8.4"
}

version = "1.2.3-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = "1.21.4"

dependencies {
  minecraft("com.mojang", "minecraft", minecraftVersion)
  mappings(loom.officialMojangMappings())
  modImplementation("net.fabricmc", "fabric-loader", "0.16.10")
  modImplementation("net.fabricmc.fabric-api", "fabric-api", "0.118.0+1.21.4")

  annotationProcessor("org.apache.logging.log4j", "log4j-core", "2.24.3")

  val jlineVersion = "3.29.0"
  implementation(include("org.jline", "jline", jlineVersion))
  implementation(include("org.jline", "jline-terminal-jansi", jlineVersion))

  implementation(include("org.fusesource.jansi", "jansi", "2.4.1"))

  modImplementation(include("net.kyori", "adventure-platform-fabric", "6.2.0"))

  implementation(transitiveInclude("org.spongepowered:configurate-hocon:4.1.2")!!)

  compileOnly("org.checkerframework", "checker-qual", "3.49.0")

  implementation(include("net.fabricmc", "mapping-io", "0.7.1"))
}

indra {
  javaVersions().target(21)
}

license {
  exclude("io/papermc/**")
}

tasks {
  processResources {
    val props = mapOf(
      "name" to "Better Fabric Console",
      "description" to project.description,
      "version" to project.version,
      "githubUrl" to "https://github.com/jpenilla/better-fabric-console"
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
      expand(props)
    }
  }
  jar {
    from("LICENSE")
  }
  remapJar {
    archiveFileName.set("${project.name}-mc$minecraftVersion-${project.version}.jar")
  }
}

publishMods.modrinth {
  projectId = "Y8o1j1Sf"
  type = ReleaseType.STABLE
  file = tasks.remapJar.flatMap { it.archiveFile }
  changelog = providers.environmentVariable("RELEASE_NOTES")
  accessToken = providers.environmentVariable("MODRINTH_TOKEN")
  minecraftVersions.add(minecraftVersion)
  modLoaders.add("fabric")
  requires("fabric-api")
}
