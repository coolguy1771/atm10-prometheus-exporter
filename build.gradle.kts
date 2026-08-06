plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.8" apply false
    id("net.minecraftforge.gradle") version "6.0.24" apply false
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
    }

    group = "com.atm10.prometheus"
    version = rootProject.extra["version"]?.toString() ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        mavenLocal()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
}

extra["version"] = "1.0.0-alpha"
