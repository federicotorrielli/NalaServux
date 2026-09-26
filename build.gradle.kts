plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

val mcVersion = providers.gradleProperty("mcVersion").get()
version = "${providers.gradleProperty("servuxVersion").get()}-nala.${providers.gradleProperty("nalaBuild").get()}"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.127-stable")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion(providers.gradleProperty("mcVersion").get())
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to project.version.toString(), "mcVersion" to mcVersion)
        inputs.properties(props)
        filesMatching(listOf("paper-plugin.yml", "plugin.yml", "nalaservux.properties")) {
            expand(props)
        }
    }
}
