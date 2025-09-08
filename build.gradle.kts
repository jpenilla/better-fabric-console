import me.modmuss50.mpp.ReleaseType
import xyz.jpenilla.resourcefactory.fabric.Environment

plugins {
  val indraVersion = "3.2.0"
  id("net.kyori.indra") version indraVersion
  id("net.kyori.indra.checkstyle") version indraVersion
  id("net.kyori.indra.license-header") version indraVersion
  id("quiet-fabric-loom")
  id("me.modmuss50.mod-publish-plugin") version "0.8.4"
  id("xyz.jpenilla.resource-factory-fabric-convention") version "1.3.1"
}

version = "1.2.6-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = "1.21.8"

dependencies {
  minecraft("com.mojang", "minecraft", minecraftVersion)
  mappings(loom.officialMojangMappings())
  modImplementation("net.fabricmc", "fabric-loader", "0.16.14")
  modImplementation("net.fabricmc.fabric-api", "fabric-api", "0.129.0+1.21.8")

  annotationProcessor("org.apache.logging.log4j", "log4j-core", "2.25.1")

  val jlineVersion = "3.30.5"
  implementation(include("org.jline", "jline", jlineVersion))
  implementation(include("org.jline", "jline-terminal-jansi", jlineVersion))

  implementation(include("org.fusesource.jansi", "jansi", "2.4.2"))

  modImplementation(include("net.kyori", "adventure-platform-fabric", "6.6.0"))

  implementation(transitiveInclude("org.spongepowered:configurate-hocon:4.2.0") {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  })

  compileOnly("org.checkerframework", "checker-qual", "3.49.3")

  implementation(include("net.fabricmc", "mapping-io", "0.7.1"))
}

indra {
  javaVersions().target(21)
}

license {
  exclude("io/papermc/**")
}

fabricModJson {
  name = "Better Fabric Console"
  author("jmp")
  val githubUrl = "https://github.com/jpenilla/better-fabric-console"
  contact {
    homepage = githubUrl
    sources = githubUrl
    issues = "$githubUrl/issues"
  }
  mitLicense()
  icon("assets/better-fabric-console/icon.png")
  environment = Environment.SERVER
  mainEntrypoint("xyz.jpenilla.betterfabricconsole.BetterFabricConsole")
  entrypoint("preLaunch", "xyz.jpenilla.betterfabricconsole.BetterFabricConsolePreLaunch")
  mixin("better-fabric-console.mixins.json")
  depends("fabricloader", ">=0.16.14")
  depends("fabric-api", "*")
  depends("minecraft", minecraftVersion)
  depends("adventure-platform-fabric", "*")
}

tasks {
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
