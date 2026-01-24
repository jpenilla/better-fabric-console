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
  implementation(libs.gson)
  api(libs.jline)
  compileOnly(libs.jspecify)
}
