package org.rust.ide.inspections

/**
 * Rust lint warning levels.
 */
enum class RustLintLevel(
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
     * Compliler errors.
     */
    DENY("deny");

    companion object {
        fun valueForId(id: String) = RustLintLevel.values().filter { it.id == id }.firstOrNull()
    }
}
