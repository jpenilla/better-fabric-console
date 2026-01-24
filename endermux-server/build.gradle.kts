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
  api(project(":endermux-common"))
  implementation(libs.slf4jApi)
  compileOnlyApi(libs.jspecify)
  compileOnly(libs.adventureApi)
}
