/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

/**
 * Rust lint warning levels.
 */
enum class RsLintLevel(
    val id: String
) {
    /**
     * Warnings are suppressed.
     */
    ALLOW("allow"),

    /**
     * Warnings.
     */
    WARN("warn"),

    /**
     * Compiler errors.
     */
    DENY("deny"),

    /**
     * Compiler errors, but also forbids changing the lint level afterwards.
     */
    FORBID("forbid");

    companion object {
        fun valueForId(id: String) = values().firstOrNull { it.id == id }
    }
}
