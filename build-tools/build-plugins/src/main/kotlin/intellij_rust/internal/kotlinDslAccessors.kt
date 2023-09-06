/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

/**
 * Utility functions for accessing Gradle extensions that are created by convention plugins
 * (because Gradle can't generate the nice DSL accessors inside the project that defines them).
 *
 * These functions are not needed outside the convention plugins project and should be marked as `internal`.
 */
package intellij_rust.internal

import intellij_rust.IntellijRustBuildProperties
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType


/** Retrieves the [intellijRust][intellij_rust.IntellijRustBuildProperties] extension. */
internal val Project.intellijRust: IntellijRustBuildProperties
    get() = extensions.getByType()

/** Configures the [intellijRust][intellij_rust.IntellijRustBuildProperties] extension. */
internal fun Project.intellijRust(configure: IntellijRustBuildProperties.() -> Unit): Unit =
    extensions.configure(configure)
