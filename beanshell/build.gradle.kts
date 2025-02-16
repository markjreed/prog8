import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("application")
    kotlin("jvm")
}

val serverMainClassName = "prog8lsp.MainKt"
val applicationName = "prog8-beanshell"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Prog8"
    // applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        filePermissions {
            user {
                read=true
                execute=true
                write=true
            }
            other.execute = true
            group.execute = true
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("lib/bsh-3.0.0-SNAPSHOT.jar"))
}

configurations.forEach { config ->
    config.resolutionStrategy {
        preferProjectModules()
    }
}

sourceSets.main {
    java.srcDir("src")
    resources.srcDir("resources")
}

tasks.startScripts {
    applicationName = "prog8-beanshell"
}

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine("chmod", "+x", "${tasks.installDist.get().destinationDir}/bin/prog8-beanshell")
}

tasks.installDist {
    finalizedBy("fixFilePermissions")
}

tasks.build {
    finalizedBy("installDist")
}
