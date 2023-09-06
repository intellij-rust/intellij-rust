package intellij_rust.conventions

import intellij_rust.internal.intellijRust
import org.gradle.api.JavaVersion.VERSION_17
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_7
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_8
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Conventions for Kotlin/JVM projects.
 */

plugins {
    id("intellij_rust.conventions.base")
    kotlin("jvm")
    `java-test-fixtures`
}

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
}

sourceSets {
    main {
        java.srcDirs("src/gen")
        resources.srcDirs("src/${intellijRust.platformVersion.get()}/main/resources")
    }
    test {
        resources.srcDirs("src/${intellijRust.platformVersion.get()}/test/resources")
    }
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("src/${intellijRust.platformVersion.get()}/main/kotlin")
        }
        test {
            kotlin.srcDirs("src/${intellijRust.platformVersion.get()}/test/kotlin")
        }
    }
}

java {
    sourceCompatibility = VERSION_17
    targetCompatibility = VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JVM_17)
        languageVersion.set(KOTLIN_1_8)
        // see https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
        apiVersion.set(KOTLIN_1_7)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
        )
    }
}

val kotlinSourcesJarProvider by configurations.registering {
    description = "provide the Kotlin sources JAR to subprojects"
    isCanBeConsumed = true
    isCanBeResolved = false

    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named("kotlin-sources-jar"))
    }

    outgoing {
        artifact(tasks.kotlinSourcesJar)
    }
}

val kotlinSourcesJar by configurations.registering {
    description = "fetch the Kotlin sources JAR from subprojects"
    isCanBeConsumed = false
    isCanBeResolved = true

    attributes {
        attribute(USAGE_ATTRIBUTE, objects.named("kotlin-sources-jar"))
    }
}
