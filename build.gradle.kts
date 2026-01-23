import me.modmuss50.mpp.ReleaseType
import xyz.jpenilla.resourcefactory.fabric.Environment

plugins {
  val indraVersion = "4.0.0"
  id("net.kyori.indra") version indraVersion
  id("net.kyori.indra.checkstyle") version indraVersion
  id("net.kyori.indra.licenser.spotless") version indraVersion
  id("xyz.jpenilla.quiet-fabric-loom")
  id("me.modmuss50.mod-publish-plugin") version "1.1.0"
  id("xyz.jpenilla.resource-factory-fabric-convention") version "1.3.1"
}

version = "1.2.10-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = "26.1-snapshot-4"

dependencies {
  minecraft("com.mojang:minecraft:$minecraftVersion")
  implementation("net.fabricmc:fabric-loader:0.18.4")
  implementation("net.fabricmc.fabric-api:fabric-api:0.142.1+26.1")

  annotationProcessor("org.apache.logging.log4j:log4j-core:2.25.3")

  val jlineVersion = "3.30.6"
  implementation("org.jline:jline:$jlineVersion")
  include("org.jline:jline:$jlineVersion")
  implementation("org.jline:jline-terminal-jansi:$jlineVersion")
  include("org.jline:jline-terminal-jansi:$jlineVersion")

  implementation("org.fusesource.jansi:jansi:2.4.2")
  include("org.fusesource.jansi:jansi:2.4.2")

  implementation("net.kyori:adventure-platform-fabric:6.9.0-SNAPSHOT")

  implementation(transitiveInclude("org.spongepowered:configurate-hocon:4.2.0") {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  })

  compileOnly("org.jspecify:jspecify:1.0.0")

  implementation("net.fabricmc:mapping-io:0.8.0")
  include("net.fabricmc:mapping-io:0.8.0")
}

indra {
  javaVersions().target(25)
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
  depends("fabricloader", ">=0.18.4")
  depends("fabric-api", "*")
  depends("minecraft", ">1.21.11", "<26.2") // TODO ">=$minecraftVersion")
  depends("adventure-platform-fabric", "*")
}

tasks {
  jar {
    from("LICENSE")
    archiveFileName.set("${project.name}-mc$minecraftVersion-${project.version}.jar")
  }
}

publishMods.modrinth {
  projectId = "Y8o1j1Sf"
  type = ReleaseType.STABLE
  file = tasks.jar.flatMap { it.archiveFile }
  changelog = providers.environmentVariable("RELEASE_NOTES")
  accessToken = providers.environmentVariable("MODRINTH_TOKEN")
  minecraftVersions.add(minecraftVersion)
  modLoaders.add("fabric")
  requires("fabric-api")
  requires("adventure-platform-mod")
}
