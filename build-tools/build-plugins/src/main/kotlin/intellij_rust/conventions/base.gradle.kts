package intellij_rust.conventions

import intellij_rust.IntellijRustBuildProperties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    base
    id("org.gradle.test-retry")
}

val intellijRust = extensions.create<IntellijRustBuildProperties>("intellijRust")

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")

    jvmArgs = listOf("-Xmx2g", "-XX:-OmitStackTraceInFastThrow")

    // We need to prevent the platform-specific shared JNA library to loading from the system library paths,
    // because otherwise it can lead to compatibility issues.
    // Also note that IDEA does the same thing at startup, and not only for tests.
    systemProperty("jna.nosys", "true")

    // The factory should be set up automatically in `IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool`,
    // but when tests are launched by Gradle this may not happen because Gradle can use the pool earlier.
    // Setting this factory is critical for `ReadMostlyRWLock` performance, so ensure it is properly set
    systemProperty(
        "java.util.concurrent.ForkJoinPool.common.threadFactory",
        "com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory"
    )

    if (intellijRust.isTeamcity.get()) {
        // Make TeamCity builds green if only muted tests fail
        // https://youtrack.jetbrains.com/issue/TW-16784
        ignoreFailures = true
    }

    if (intellijRust.isCI.get()) {
        retry {
            maxRetries.set(3)
            maxFailures.set(5)
        }
    }

    exclude(intellijRust.excludeTests.get())
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        if (intellijRust.showTestStatus.get()) {
            events(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STARTED,
                //TestLogEvent.STANDARD_ERROR,
                //TestLogEvent.STANDARD_OUT,
            )
        }

        exceptionFormat = TestExceptionFormat.FULL
    }
    testLogging {
        showStandardStreams = intellijRust.showStandardStreams.get()
        afterSuite(
            KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
                if (desc.parent == null) { // will match the outermost suite
                    val output = "Results: ${result.resultType} (${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped)"
                    println(output)
                }
            })
        )
    }
}
