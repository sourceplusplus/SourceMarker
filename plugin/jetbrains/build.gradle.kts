import org.jetbrains.changelog.markdownToHTML

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.1.3"
    id("org.jetbrains.changelog") version "1.2.0"
    id("maven-publish")
}

val vertxVersion = ext.get("vertxVersion")
val kotlinVersion = ext.get("kotlinVersion")

// Import variables from gradle.properties file
val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project
val pluginVerifierIdeVersions: String by project

val platformType: String by project
val platformVersion: String by project
val platformPlugins: String by project
val platformDownloadSources: String by project

group = pluginGroup
version = pluginVersion

intellij {
    pluginName.set("SourceMarker")
    version.set(platformVersion)
    type.set(platformType)
    downloadSources.set(platformDownloadSources.toBoolean())
    updateSinceUntilBuild.set(true)

    plugins.set(platformPlugins.split(',').map(String::trim).filter(String::isNotEmpty).toMutableList())
}
tasks.getByName("buildSearchableOptions").onlyIf { false } //todo: figure out how to remove
tasks.getByName<JavaExec>("runIde") {
    systemProperty("sourcemarker.debug.capture_logs", true)
}

changelog {
    version = pluginVersion
}

repositories {
    maven(url = "https://jitpack.io") { name = "jitpack" }
}

dependencies {
    implementation(project(":mapper"))
    implementation(project(":marker"))
    implementation(project(":monitor:skywalking"))
    implementation(project(":protocol"))
    implementation(project(":portal"))

    implementation("com.github.sh5i:git-stein:v0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinVersion")
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    //implementation("io.vertx:vertx-service-discovery:$vertxVersion")
    implementation(files(".ext/vertx-service-discovery-4.0.3-SNAPSHOT.jar"))
    //implementation("io.vertx:vertx-service-proxy:$vertxVersion")
    implementation(files(".ext/vertx-service-proxy-4.0.2.jar"))
    implementation("io.vertx:vertx-tcp-eventbus-bridge:$vertxVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.12.4")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-guava:2.12.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("io.dropwizard.metrics:metrics-core:4.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")
    implementation("org.jooq:jooq:3.15.1")
    implementation("org.eclipse.mylyn.github:org.eclipse.egit.github.core:2.1.5")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("eu.geekplace.javapinning:java-pinning-core:1.2.0")
    implementation("ch.qos.logback:logback-core:1.2.3")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}

tasks {
    patchPluginXml {
        version.set(pluginVersion)
        sinceBuild.set(pluginSinceBuild)
        untilBuild.set(pluginUntilBuild)

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            File(rootProject.projectDir, "./README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md file:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(changelog.getLatest().toHTML())
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        channels.set(listOf(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first()))
    }
    runPluginVerifier {
        ideVersions.set(pluginVerifierIdeVersions.split(",").map { it.trim() })
    }

    //todo: should be a way to just add implementation() to dependencies
    getByName("processResources") {
        dependsOn(":portal:build")
        doLast {
            copy {
                from(file("$rootDir/portal/build/distributions/portal.js"))
                into(file("$rootDir/plugin/jetbrains/build/resources/main"))
            }
            copy {
                from(file("$rootDir/portal/build/distributions/portal.js.map"))
                into(file("$rootDir/plugin/jetbrains/build/resources/main"))
            }
        }
    }
}

sourceSets.main.get().java.srcDirs(
    sourceSets.main.get().java.srcDirs, "$rootDir/protocol/build/generated/source/kapt/main"
)

tasks {
    test {
        useJUnitPlatform()
    }
}
