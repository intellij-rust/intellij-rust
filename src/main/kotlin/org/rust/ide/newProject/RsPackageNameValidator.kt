/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.newProject

import com.intellij.openapi.util.SystemInfo

/**
 * Validates package name while new project creation.
 *
 * The corresponding cargo [validation](https://github.com/rust-lang/cargo/blob/eea58f205fd2bbafb19c260c7f9978d3e06c6fd4/src/cargo/ops/cargo_new.rs#L121-L170)
 */
object RsPackageNameValidator {

    /**
     * See [is_keyword](https://github.com/rust-lang/cargo/blob/2c6711155232b2d6271bb7147610077c3a8cee65/src/cargo/util/restricted_names.rs#L13) function
     */
    private val KEYWORDS_BLACKLIST = setOf(
        "Self", "abstract", "as", "async", "await", "become", "box", "break", "const", "continue",
        "crate", "do", "dyn", "else", "enum", "extern", "false", "final", "fn", "for", "if",
        "impl", "in", "let", "loop", "macro", "match", "mod", "move", "mut", "override", "priv",
        "pub", "ref", "return", "self", "static", "struct", "super", "trait", "true", "try",
        "type", "typeof", "unsafe", "unsized", "use", "virtual", "where", "while", "yield"
    )

    /**
     * See [is_conflicting_artifact_name](https://github.com/rust-lang/cargo/blob/2c6711155232b2d6271bb7147610077c3a8cee65/src/cargo/util/restricted_names.rs#L35) function
     */
    private val BINARY_BLACKLIST = setOf("deps", "examples", "build", "incremental")

    private val WINDOWS_BLACKLIST = setOf(
        "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7",
        "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    )

    fun validate(name: String, isBinary: Boolean): String? = when {
        name.isEmpty() -> "Package name can't be empty"
        name in KEYWORDS_BLACKLIST || name == "test" -> "The name `$name` cannot be used as a crate name"
        isBinary && name in BINARY_BLACKLIST -> "The name `$name` cannot be used as a crate name"
        name[0].isDigit() -> "Package names starting with a digit cannot be used as a crate name"
        !name.all { it.isLetterOrDigit() || it == '-' || it == '_' } ->
            "Package names should contain only letters, digits, `-` and `_`"
        SystemInfo.isWindows && name.toLowerCase() in WINDOWS_BLACKLIST -> "The name `$name` is a reserved Windows filename"
        else -> null
    }
}
