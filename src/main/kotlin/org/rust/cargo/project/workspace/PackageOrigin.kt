/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

/**
 * Defines a reason a package is in a project.
 */
enum class PackageOrigin(private val level: Int) {
    /**
     * The package comes from the standard library.
     */
    STDLIB(0),

    /**
     * The package is a part of our workspace.
     */
    WORKSPACE(1),

    /**
     * The package is a dependency defined in Cargo.toml.
     */
    DEPENDENCY(2),

    /**
     * The package is a dependency of one of our dependencies.
     */
    TRANSITIVE_DEPENDENCY(3);

    companion object {
        fun min(o1: PackageOrigin, o2: PackageOrigin) = if (o1.level < o2.level) o1 else o2
    }
}
