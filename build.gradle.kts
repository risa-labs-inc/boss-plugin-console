import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
version = "1.2.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Auto-detect CI environment
val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    if (useLocalDependencies) {
        // Local development: newest boss-plugin-api jar from the sibling repo, so this path
        // never needs hand-bumping on api releases. It was pinned to 1.0.59, which stopped
        // existing long ago; compileOnly(files(...)) resolves a missing path to nothing
        // silently, so every api symbol went unresolved and a clean local build produced
        // 213 errors that named the symbols rather than the pin. CI is unaffected - it
        // resolves the 'latest' release into build/downloaded-deps.
        //
        // Compiling against the newest jar does NOT lower the install floor: plugin.json
        // declares apiVersion 1.0.59 and that is what gates hosts. Adding a call that only
        // exists in a newer api will compile here and fail on an older host, so check the
        // manifest before reaching for a new symbol.
        //
        // The lookup lives in a provider so it runs at dependency-RESOLUTION time, not
        // configuration time: clean/help/tasks still work on a fresh checkout with no
        // sibling jar built, and compilation fails with this actionable message instead.
        val newestApiJar = provider {
            val apiJarPattern = Regex("""boss-plugin-api-(\d+)\.(\d+)\.(\d+)\.jar""")
            file("$bossPluginApiPath/build/libs").listFiles()
                ?.mapNotNull { jar -> apiJarPattern.matchEntire(jar.name)?.let { m -> jar to m } }
                // Compare (major, minor, patch) numerically: 1.0.9 sorts above 1.0.71 as a
                // string, which would silently pick an ancient jar.
                ?.maxWithOrNull(
                    compareBy(
                        { it.second.groupValues[1].toInt() },
                        { it.second.groupValues[2].toInt() },
                        { it.second.groupValues[3].toInt() },
                    ),
                )?.first
                ?: error(
                    "No boss-plugin-api jar found in $bossPluginApiPath/build/libs - " +
                        "run ./gradlew buildPluginJar in the sibling boss-plugin-api checkout first."
                )
        }
        compileOnly(files(newestApiJar))
    } else {
        // CI: use downloaded JAR
        compileOnly(files("build/downloaded-deps/boss-plugin-api.jar"))
    }
    
    // Compose dependencies
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    
    // Decompose for ComponentContext
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

// Task to build plugin JAR with compiled classes only (dependencies provided by host)
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-console-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes(
            "Implementation-Title" to "BOSS Console Plugin",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.dynamic.console.ConsoleDynamicPlugin"
        )
    }
    
    // Include compiled classes
    from(sourceSets.main.get().output)
    
    // Include plugin manifest
    from("src/main/resources")
}

// Sync version from build.gradle.kts into plugin.json (single source of truth)
tasks.processResources {
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "\$version"""")
        }
    }
}

tasks.build {
    dependsOn("buildPluginJar")
}

// Fat JAR for out-of-process plugin execution
tasks.register<Jar>("shadowJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "ai.rever.boss.plugin.runtime.PluginProcessMainKt"
        )
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)
    from("src/main/resources")
}
