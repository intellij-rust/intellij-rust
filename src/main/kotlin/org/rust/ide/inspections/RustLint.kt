package org.rust.ide.inspections

/**
 * Rust lints.
 */
enum class RustLint(
    val id: String,
    val defaultLevel: RustLintLevel = RustLintLevel.WARN
) {
    NonSnakeCase("non_snake_case"),
    NonCamelCaseTypes("non_camel_case_types"),
    NonUpperCaseGlobals("non_upper_case_globals")
}
