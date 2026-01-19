import org.gradle.api.file.DuplicatesStrategy

plugins {
    `maven-publish`
    id("hytale-mod") version "0.+"
}

group = "dev.chasem"
version = findProperty("plugin_version")?.toString() ?: "0.1.0"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    // Optional: Preview mod icon in-game via /modlist command
    runtimeOnly(libs.bettermodlist)
}

val fatJarDependencies by configurations.creating {
    extendsFrom(configurations.runtimeClasspath.get())
    exclude(group = "com.buuz135", module = "BetterModlist")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    val replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

hytale {

}

// Configure Vineflower decompiler with fewer threads to prevent OOM
tasks.matching { it.name == "decompileServer" }.configureEach {
    if (this is JavaExec) {
        jvmArgs("-Xmx14G")
        args("--thread-count=2")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
    from(sourceSets.main.get().output)
    dependsOn(fatJarDependencies)
    from({
        fatJarDependencies.filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
}

tasks.named("build") {
    dependsOn(fatJar)
}

publishing {
    repositories {
        // Add publish repositories here if needed
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}
