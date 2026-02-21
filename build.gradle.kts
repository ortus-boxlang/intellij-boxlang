plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.25"
  id("org.jetbrains.intellij.platform") version "2.3.0"
  id("com.diffplug.spotless") version "8.2.1"
}

group = "com.ortussolutions"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
  intellijPlatform {
    create("IC", "2024.2.5")
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

    // Add necessary plugin dependencies for compilation here, example:
    // bundledPlugin("com.intellij.java")
  }

  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.22.0")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.22.0")
  implementation("com.vdurmont:semver4j:3.1.0")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "242"
    }

    changeNotes = """
      Initial version
    """.trimIndent()
  }
}

spotless {
  java {
    target(
      fileTree(".") {
        include("**/*.java")
        exclude(
          "**/build/**",
          "bin/**",
          "examples/**",
          "src/main/java/ortus/boxlang/runtime/testing/**",
          "src/main/gen/**",
          "src/main/antlr/gen",
          "modules/**"
        )
      }
    )
    eclipse().configFile("workbench/ortus-java-style.xml")
    toggleOffOn()
  }
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
  }
}

tasks.named("check") {
  dependsOn("spotlessCheck")
}
