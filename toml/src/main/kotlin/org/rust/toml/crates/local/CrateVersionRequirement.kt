/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates.local

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.constraints.satisfiedBy
import io.github.z4kn4fein.semver.constraints.toConstraintOrNull

class CrateVersionRequirement private constructor(private val requirements: List<Constraint>) {
    val isPinned: Boolean = requirements.any { it.toString().startsWith("=") }

    fun matches(version: Version): Boolean = requirements.all { it satisfiedBy version }

    companion object {
        fun build(text: String): CrateVersionRequirement? {
            val requirements = text.split(",").map { it.trim() }
            if (requirements.size > 1 && requirements.any { it.isEmpty() }) return null

            val parsed = requirements.mapNotNull {
                normalizeVersion(it).toConstraintOrNull()
            }
            if (parsed.size != requirements.size) return null

            return CrateVersionRequirement(parsed)
        }
    }
}

/**
 * Normalizes crate version requirements so that they are compatible with semver lib.
 *
 * 1) For exact (=x.y.z) and range-based (>x.y.z, <x.y.z) version requirements, the version requirement is padded by
 * zeros from the right side.
 *
 *      Example:
 *      - `=1` turns into `=1.0.0`
 *      - `>2.3` turns into `>2.3.0`
 *
 * 2) For "compatible" version requirements (x.y.z) that do not contain a wildcard (*), a caret is added to the
 * beginning. This matches the behaviour of Cargo.
 *
 *      Example:
 *      - `1.2.3` turns into `^1.2.3`
 *
 * See [Cargo Reference](https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#specifying-dependencies-from-cratesio).
 */
private fun normalizeVersion(version: String): String {
    if (version.isBlank()) return version

    // Exact and range-based version requirements need to be padded from right by zeros.
    // kotlin-semver treats non-specified parts as *, so =1.2 would be =1.2.*. We need to explicitly specify them.
    var normalized = version
    if (normalized[0] in listOf('<', '>', '=')) {
        while (normalized.count { it == '.' } < 2) {
            normalized += ".0"
        }
    }

    // Cargo treats version requirements like `1.2.3` as if they had a caret at the beginning.
    // If the version begins with a digit and it does not contain a wildcard, we thus prepend a caret to it.
    // Also, kotlin-semver lib treats versions without any range modifiers as exact ones like `=1.2.3`, so
    // we would like to avoid it.
    return if (normalized[0].isDigit() && !normalized.contains("*")) {
        "^$normalized"
    } else {
        normalized
    }
}
