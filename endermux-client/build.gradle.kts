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
  implementation(project(":endermux-log4j-plugins"))
  annotationProcessor(libs.picocliCodegen)
  implementation(platform(libs.log4jBom))
  implementation(libs.log4jCore)
  implementation(libs.log4jApi)
  implementation(libs.bundles.jline)
  implementation(libs.jansi)
  implementation(libs.adventureTextSerializerAnsi)
  implementation(libs.adventureTextSerializerGson)
  implementation(libs.picocli)
  implementation(libs.gson)
  compileOnly(libs.jspecify)
}

application {
  mainClass = "xyz.jpenilla.endermux.client.EndermuxCli"
}

tasks {
  compileJava {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
  }
}
