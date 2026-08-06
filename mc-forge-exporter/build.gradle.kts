plugins {
    id("net.minecraftforge.gradle")
    kotlin("jvm") version "2.0.0"
    id("io.github.goooler.shadow")
}

val minecraftVersion: String by project
val forgeVersion: String by project
val parchmentVersion: String by project
val parchmentMappingsVersion: String by project

base {
    archivesName.set("atm10-prometheus-exporter-${project.version}")
}

// Forge task configuration
minecraft {
    mappingChannel.set(parchmentVersion)
    mappingVersion.set("${parchmentMappingsVersion}-${minecraftVersion}")

    runs {
        create("client") {
            workingDirectory(file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods.create("atm10_prometheus_exporter") {
                source(sourceSets.main.get())
            }
        }

        create("server") {
            workingDirectory(file("run-server"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods.create("atm10_prometheus_exporter") {
                source(sourceSets.main.get())
            }
        }
    }
}

configurations {
    create("shadowImplementation") extendsFrom(configurations.compileOnly.get())
}

dependencies {
    // Forge
    minecraft("net.minecraftforge:forge:${minecraftVersion}-${forgeVersion}")

    // Prometheus SimpleClient (shaded)
    shadowImplementation(platform("io.prometheus:simpleclient_bom:0.16.0"))
    shadowImplementation("io.prometheus:simpleclient")
    shadowImplementation("io.prometheus:simpleclient_httpserver")
    shadowImplementation("io.prometheus:simpleclient_tracer_common")

    // Micrometer (shaded) - for unified metrics abstraction
    shadowImplementation(platform("io.micrometer:micrometer-bom:1.13.5"))
    shadowImplementation("io.micrometer:micrometer-core")

    // Logging (slf4j bound by Forge/Log4j2, we use API)
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    // Annotation processing for code generation where needed
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    compileOnly("org.projectlombok:lombok:1.18.32")
}

// Shadow configuration - package dependencies inside the jar
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    minimize()
    relocate("io.prometheus", "com.atm10.prometheus.shaded.prometheus")
    relocate("io.micrometer", "com.atm10.prometheus.shaded.micrometer")
    archiveClassifier.set("")
    mergeServiceFiles()

    manifest {
        attributes(
            mapOf(
                "FMLModType" to "LIBRARY",
                "Implementation-Title" to base.archivesName.get(),
                "Implementation-Version" to project.version,
            )
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "mod_version" to version,
        "minecraft_version" to minecraftVersion,
        "forge_version_range" to "[${forgeVersion.split('+')[0]},)",
        "java_version" to "21",
    )

    inputs.properties(props)

    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(21)
    options.encoding = Charsets.UTF_8.name()

    // Modern Java compiler flags
    options.compilerArgs.addAll(
        listOf(
            "-parameters",           // Preserve parameter names for reflection
            "--enable-preview",      // Enable preview features if desired
            "-Xlint:unchecked",
            "-Werror"                // Treat warnings as errors in CI
        )
    )
}

// Repobuild task for IDE configuration
tasks.named("reobfJar").configure {
    enabled = false // Not needed with Forge 1.20.5+ unified mappings
}
