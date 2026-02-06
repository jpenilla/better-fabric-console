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

version = "1.3.0-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = libs.versions.minecraft.get()

dependencies {
  minecraft(libs.minecraft)
  implementation(libs.fabricLoader)
  implementation(libs.fabricApi)

  implementation(project(":endermux-common"))
  include(project(":endermux-common"))
  implementation(project(":endermux-server"))
  include(project(":endermux-server"))

  implementation(libs.bundles.jline)
  include(libs.bundles.jline)

  // Fallback in case native access flags not passed - TODO verify this works as intended
  implementation(libs.jansi)
  include(libs.jansi)
  implementation(libs.jline.terminal.jansi)
  include(libs.jline.terminal.jansi)

  implementation(libs.adventurePlatformFabric)

  transitiveInclude(libs.configurateHocon) {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  }
  implementation(libs.configurateHocon) {
    exclude("net.kyori", "option") // provided by adventure-platform-fabric
  }

  compileOnly(libs.jspecify)
  
  implementation("com.google.code.gson:gson:2.10.1")
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
  runServer {
    // jvmArgs("-Dmixin.debug=true")
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
