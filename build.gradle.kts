import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    id("intellij_rust.conventions.intellij")
    id("intellij_rust.conventions.rust-compile")
    id("net.saliman.properties") version "1.5.2"
}

description = "root/core module"

idea {
    module {
        // https://github.com/gradle/kotlin-dsl/issues/537/
        excludeDirs = excludeDirs +
            file("testData") +
            file("deps") +
            file("bin") +
            file("${intellijRust.grammarKitFakePsiDeps}/src/main/kotlin")
    }
}

intellij {
    plugins.set(listOf(intellijRust.tomlPlugin))
}

sourceSets {
    main {
        if (intellijRust.channel.get() == "nightly" || intellijRust.channel.get() == "dev") {
            resources.srcDirs("src/main/resources-nightly")
            resources.srcDirs("src/${intellijRust.platformVersion.get()}/main/resources-nightly")
        } else {
            resources.srcDirs("src/main/resources-stable")
            resources.srcDirs("src/$${intellijRust.platformVersion.get()}/main/resources-stable")
        }
    }
}

dependencies {
    implementation(libs.jackson.dataformatToml) {
        exclude(module = "jackson-core")
        exclude(module = "jackson-databind")
        exclude(module = "jackson-annotations")
    }
    api(libs.z4kn4fein.semver) {
        excludeKotlinDeps()
    }
    implementation(libs.eclipse.jgit) {
        exclude("org.slf4j")
    }
    testFixturesImplementation(libs.okhttp.mockwebserver)
}

val grammarKitImplementation by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true

    withDependencies {
        add(projects.grammarKitFakePsiDeps)
    }
}

tasks {
    generateLexer {
        sourceFile.set(file("src/main/grammars/RustLexer.flex"))
        targetDir.set("src/gen/org/rust/lang/core/lexer")
        targetClass.set("_RustLexer")
        purgeOldFiles.set(true)
    }
    generateParser {
        sourceFile.set(file("src/main/grammars/RustParser.bnf"))
        targetRoot.set("src/gen")
        pathToParser.set("org/rust/lang/core/parser/RustParser.java")
        pathToPsiRoot.set("org/rust/lang/core/psi")
        purgeOldFiles.set(true)
        classpath(grammarKitImplementation)
    }
    withType<KotlinCompile> {
        dependsOn(generateLexer, generateParser)
    }
}

// In tests `resources` directory is used instead of `sandbox`
tasks.processTestResources {
    dependsOn(tasks.compileNativeCode)
    from("${rootDir}/bin") {
        into("bin")
        include("**")
    }
}

tasks.register("resolveDependencies") {
    @Suppress("UnstableApiUsage")
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    doLast {
        rootProject.allprojects
            .flatMap { it.configurations.matching(Configuration::isCanBeResolved) }
            .forEach { it.resolve() }
    }
}

val updateCargoOptions by tasks.registering(intellij_rust.tasks.UpdateCargoOptions::class) {
    cargoOptions.set(layout.projectDirectory.file("src/main/kotlin/org/rust/cargo/util/CargoOptions.kt"))
}

fun <T : ModuleDependency> T.excludeKotlinDeps() {
    exclude(module = "kotlin-reflect")
    exclude(module = "kotlin-runtime")
    exclude(module = "kotlin-stdlib")
    exclude(module = "kotlin-stdlib-common")
    exclude(module = "kotlin-stdlib-jdk8")
    exclude(module = "kotlinx-serialization-core")
}
