/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

/**
 * Validates package name while new project creation.
 *
 * The corresponding cargo [validation](https://github.com/rust-lang/cargo/blob/eea58f205fd2bbafb19c260c7f9978d3e06c6fd4/src/cargo/ops/cargo_new.rs#L121-L170)
 */
object RsPackageNameValidator {

    private val BLACKLIST = setOf(
        "abstract", "alignof", "as", "become", "box", "break", "const", "continue", "crate", "do",
        "else", "enum", "extern", "false", "final", "fn", "for", "if", "impl", "in", "let", "loop",
        "macro", "match", "mod", "move", "mut", "offsetof", "override", "priv", "proc", "pub",
        "pure", "ref", "return", "self", "sizeof", "static", "struct", "super", "test", "trait",
        "true", "type", "typeof", "unsafe", "unsized", "use", "virtual", "where", "while", "yield"
    )

    private val BINARY_BLACKLIST = setOf("deps", "examples", "build", "native", "incremental")

    fun validate(name: String, isBinary: Boolean): String? = when {
        name.isEmpty() -> "Package name can't be empty"
        name in BLACKLIST -> "The name `$name` cannot be used as a crate name"
        isBinary && name in BINARY_BLACKLIST -> "The name `$name` cannot be used as a crate name"
        name[0].isDigit() -> "Package names starting with a digit cannot be used as a crate name"
        !name.all { it.isLetterOrDigit() || it == '-' || it == '_' } ->
            "Package names should contain only letters, digits, `-` and `_`"
        else -> null
    }
}
