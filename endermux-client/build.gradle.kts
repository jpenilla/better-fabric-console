plugins {
  alias(libs.plugins.indra)
  application
}

version = "0.0.1-SNAPSHOT"
group = "xyz.jpenilla"

indra {
  javaVersions().target(25)
}

dependencies {
  api(project(":endermux-common"))
  implementation(libs.picocli)
  annotationProcessor(libs.picocliCodegen)

  implementation(platform(libs.log4jBom))
  annotationProcessor(platform(libs.log4jBom))
  implementation(libs.log4jApi)
  implementation(libs.log4jCore)
  implementation(libs.log4jIostreams)
  implementation(libs.log4jSlf4j2Impl)
  implementation(libs.slf4jApi)
  annotationProcessor(libs.log4jCore)

  implementation(libs.bundles.jline)
  implementation(libs.adventureTextSerializerAnsi)
  implementation(libs.adventureTextSerializerGson)
  implementation(libs.adventureTextLoggerSlf4j)
}

application {
  mainClass = "xyz.jpenilla.endermux.client.EndermuxCli"
  applicationDefaultJvmArgs = listOf(
    //"--enable-native-access=org.jline.terminal.ffm", // For jline 4
    "--enable-native-access=ALL-UNNAMED"
  )
}

tasks {
  jar {
    manifest {
      attributes(
        "Implementation-Version" to project.version,
      )
    }
  }
  compileJava {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
  }
}
