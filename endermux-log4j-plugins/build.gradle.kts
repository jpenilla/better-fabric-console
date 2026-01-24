plugins {
  alias(libs.plugins.indra)
  alias(libs.plugins.indraPublishing)
}

version = "0.0.1-SNAPSHOT"
group = "xyz.jpenilla"

indra {
  javaVersions().target(25)
}

dependencies {
  implementation(project(":endermux-common"))
  implementation(libs.adventureApi)
  implementation(libs.adventureTextSerializerAnsi)

  implementation(platform(libs.log4jBom))
  annotationProcessor(platform(libs.log4jBom))
  implementation(libs.log4jApi)
  implementation(libs.log4jCore)
  annotationProcessor(libs.log4jCore)
}
