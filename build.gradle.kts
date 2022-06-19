plugins {
  val indraVersion = "2.1.1"
  id("net.kyori.indra") version indraVersion
  id("net.kyori.indra.checkstyle") version indraVersion
  id("net.kyori.indra.license-header") version indraVersion
  id("quiet-fabric-loom")
}

version = "1.0.6-SNAPSHOT"
group = "xyz.jpenilla"
description = "Server-side Fabric mod enhancing the console with tab completions, colored log output, command syntax highlighting, command history, and more."

val minecraftVersion = "1.19"

dependencies {
  minecraft("com.mojang", "minecraft", minecraftVersion)
  mappings(loom.officialMojangMappings())
  modImplementation("net.fabricmc", "fabric-loader", "0.14.7")

  annotationProcessor("org.apache.logging.log4j", "log4j-core", "2.17.1")

  val jlineVersion = "3.21.0"
  implementation(include("org.jline", "jline", jlineVersion))
  implementation(include("org.jline", "jline-terminal-jansi", jlineVersion))

  implementation(include("org.fusesource.jansi", "jansi", "2.4.0"))

  modImplementation(include("net.kyori", "adventure-platform-fabric", "5.4.0-SNAPSHOT"))
  implementation(include("net.kyori", "adventure-text-serializer-legacy", "4.11.0"))

  modImplementation(include("ca.stellardrift", "confabricate", "3.0.0-SNAPSHOT"))
  compileOnly("org.checkerframework", "checker-qual", "3.22.1")

  implementation(include("net.fabricmc", "mapping-io", "0.3.0"))
}

indra {
  javaVersions().target(17)
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
