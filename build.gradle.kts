plugins {
  id("fabric-loom") version "0.8-SNAPSHOT"
  `maven-publish`
  `java-library`
}

version = "0.3.0"
group = "org.chrisoft"
description = "Enables command history, auto completion, and syntax highlighting on the server console."

repositories {
  mavenCentral()
  maven("https://maven.fabricmc.net/")
  maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

val minecraftVersion = "1.17"

dependencies {
  minecraft("com.mojang", "minecraft", minecraftVersion)
  mappings(minecraft.officialMojangMappings())
  modImplementation("net.fabricmc", "fabric-loader", "0.11.3")
  //modImplementation("net.fabricmc.fabric-api", "fabric-api", "0.34.8+1.17")

  annotationProcessor("org.apache.logging.log4j", "log4j-core", "2.14.1")
  val jlineVersion = "3.20.0"
  implementation(include("org.jline", "jline", jlineVersion))
  implementation(include("org.jline", "jline-terminal-jansi", jlineVersion))
  implementation(include("org.fusesource.jansi", "jansi", "2.3.2"))

  implementation(include("net.kyori", "adventure-text-serializer-legacy", "4.8.0"))
  modImplementation(include("net.kyori", "adventure-platform-fabric", "4.1.0-SNAPSHOT"))
  modImplementation(include("ca.stellardrift", "confabricate", "2.1.0"))
  compileOnly("org.checkerframework", "checker-qual", "3.14.0")
}

tasks {
  processResources {
    filesMatching("fabric.mod.json") {
      mapOf(
        "\${NAME}" to "JLine for Minecraft Dedicated Server",
        "\${DESCRIPTION}" to project.description as String,
        "\${VERSION}" to project.version as String
      ).forEach { (token, replacement) ->
        filter { it.replace(token, replacement) }
      }
    }
  }
  withType<JavaCompile> {
    options.encoding = Charsets.UTF_8.toString()
  }
  jar {
    from("LICENSE")
  }
  remapJar {
    archiveFileName.set("${project.name}-mc$minecraftVersion-${project.version}.jar")
    archiveBaseName.set(project.name)
  }
}

java {
  sourceCompatibility = JavaVersion.toVersion(16)
  targetCompatibility = JavaVersion.toVersion(16)
  withSourcesJar()
}
