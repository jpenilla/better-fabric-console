import me.modmuss50.mpp.ReleaseType
import xyz.jpenilla.resourcefactory.fabric.Environment

plugins {
  alias(libs.plugins.indra)
  alias(libs.plugins.indraCheckstyle)
  alias(libs.plugins.indraLicenserSpotless)
  id("xyz.jpenilla.quiet-fabric-loom")
  alias(libs.plugins.modPublishPlugin)
  alias(libs.plugins.resourceFactoryFabricConvention)
}

version = "2.0.0-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = libs.versions.minecraft.get()

dependencies {
  minecraft(libs.minecraft)
  implementation(libs.fabricLoader)
  implementation(libs.fabricApi)

  annotationProcessor(platform(libs.log4jBom))
  annotationProcessor(libs.log4jCore)

  implementation(libs.bundles.jline)
  include(libs.bundles.jline)

  implementation(libs.adventurePlatformFabric)

  transitiveInclude(libs.configurateHocon) {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  }
  implementation(libs.configurateHocon) {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  }
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
  depends("fabricloader", ">=${libs.versions.fabric.loader.get()}")
  depends("fabric-api", "*")
  depends("minecraft", ">=$minecraftVersion", "<26.2")
  depends("adventure-platform-fabric", "*")
  breaks("better_log4j_config", "*")
  breaks("jline4mcdsrv", "*")
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
  incompatible("better-log4j-config")
  incompatible("jline4mcdsrv")
}
