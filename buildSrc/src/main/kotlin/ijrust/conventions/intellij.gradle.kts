package ijrust.conventions

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.JavaVersion.VERSION_17
import ijrust.utils.not
import ijrust.IJRustBuildProperties.Companion.ijRustBuild
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("ijrust.conventions.base")
    kotlin("jvm")
   id("org.jetbrains.grammarkit")
   id("org.jetbrains.intellij")
   id("org.gradle.test-retry")
}

intellij {
    version.set(ijRustBuild.baseVersion)
    downloadSources.set(!ijRustBuild.isCI)
    updateSinceUntilBuild.set(true)
    instrumentCode.set(false)
    ideaDependencyCachePath.set(dependencyCachePath)
    sandboxDir.set("$buildDir/${ijRustBuild.baseIDE}-sandbox-${ijRustBuild.platformVersion}")
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        // If cache path doesn't exist, we need to create it manually
        // because otherwise gradle-intellij-plugin will ignore it
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }

sourceSets {
    main {
        java.srcDirs("src/gen")
        resources.srcDirs("src/${ijRustBuild.platformVersion.get()}/main/resources")
    }
    test {
        resources.srcDirs("src/${ijRustBuild.platformVersion.get()}/test/resources")
    }
}
kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("src/${ijRustBuild.platformVersion.get()}/main/kotlin")
        }
        test {
            kotlin.srcDirs("src/${ijRustBuild.platformVersion.get()}/test/kotlin")
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = VERSION_17
    targetCompatibility = VERSION_17
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = VERSION_17.toString()
            languageVersion = "1.8"
            // see https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
            apiVersion = "1.7"
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }
    withType<PatchPluginXmlTask> {
        sinceBuild.set(ijRustBuild.sinceBuild)
        untilBuild.set(ijRustBuild.untilBuild)
    }

    // All these tasks don't make sense for non-root subprojects
    // Root project (i.e. `:plugin`) enables them itself if needed
    runIde { enabled = false }
    prepareSandbox { enabled = false }
    buildSearchableOptions { enabled = false }

    test {
        systemProperty("java.awt.headless", "true")
        testLogging {
            showStandardStreams = ijRustBuild.showStandardStreams.get()
            afterSuite(
                KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                    if (desc.parent == null) { // will match the outermost suite
                        val output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
                        println(output)
                    }
                })
            )
        }
        if (ijRustBuild.isCI.get()) {
            retry {
                maxRetries.set(3)
                maxFailures.set(5)
            }
        }
    }

    // It makes sense to copy native binaries only for root ("intellij-rust") and "plugin" projects because:
    // - `intellij-rust` is supposed to provide all necessary functionality related to procedural macro expander.
    //   So the binaries are required for the corresponding tests.
    // - `plugin` is root project to build plugin artifact and exactly its sandbox is included into the plugin artifact
    if (project.name in listOf("intellij-rust", "plugin")) {
        task<Exec>(ijRustBuild.compileNativeCodeTaskName) {
            workingDir = rootDir.resolve("native-helper")
            executable = "cargo"
            // Hack to use unstable `--out-dir` option work for stable toolchain
            // https://doc.rust-lang.org/cargo/commands/cargo-build.html#output-options
            environment("RUSTC_BOOTSTRAP", "1")

            val hostPlatform = DefaultNativePlatform.host()
            val archName = when (val archName = hostPlatform.architecture.name) {
                "arm-v8", "aarch64" -> "arm64"
                else -> archName
            }
            val outDir = "${rootDir}/bin/${hostPlatform.operatingSystem.toFamilyName()}/$archName"
            args("build", "--release", "-Z", "unstable-options", "--out-dir", outDir)

            // It may be useful to disable compilation of native code.
            // For example, CI builds native code for each platform in separate tasks and puts it into `bin` dir manually
            // so there is no need to do it again.
            enabled = ijRustBuild.compileNativeCode.get()
        }
    }
}
