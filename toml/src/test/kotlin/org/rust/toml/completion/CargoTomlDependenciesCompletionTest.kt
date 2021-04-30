/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.intellij.lang.annotations.Language

class CargoTomlDependenciesCompletionTest : CargoTomlCompletionTestBase() {
    fun `test empty key`() = doTest("""
        [dependencies]
        <caret>
    """, """
        [dependencies]
        dep = "1.0"<caret>
    """, "dep" to "1.0")

    fun `test empty key with complex dependency head`() = doTest("""
        [target.'cfg(windows)'.dev-dependencies]
        <caret>
    """, """
        [target.'cfg(windows)'.dev-dependencies]
        dep = "1.0"<caret>
    """, "dep" to "1.0")

    fun `test partial key`() = doTest("""
        [dependencies]
        d<caret>
    """, """
        [dependencies]
        dep = "1.0"<caret>
    """, "app" to "1.0", "dep" to "1.0")

    fun `test empty value complete without '=' and quotes 1_0`() = doTest("""
        [dependencies]
        dep <caret>
    """, """
        [dependencies]
        dep = "1.0<caret>"
    """, "dep" to "1.0")

    fun `test empty value complete without quotes 1_0`() = doTest("""
        [dependencies]
        dep = <caret>
    """, """
        [dependencies]
        dep = "1.0<caret>"
    """, "dep" to "1.0")

    fun `test empty value complete without quotes 1_0_0`() = doTest("""
        [dependencies]
        dep = <caret>
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, "dep" to "1.0.0")

    fun `test empty value complete inside quotes`() = doTest("""
        [dependencies]
        dep = "<caret>"
    """, """
        [dependencies]
        dep = "1.0<caret>"
    """, "dep" to "1.0")

    fun `test partial value complete inside quotes`() = doTest("""
        [dependencies]
        dep = "1.<caret>"
    """, """
        [dependencies]
        dep = "1.0<caret>"
    """, "dep" to "1.0")

    fun `test empty value complete inside quotes without '='`() = doTest("""
        [dependencies]
        dep "<caret>"
    """, """
        [dependencies]
        dep = "1.0<caret>"
    """, "dep" to "1.0")

    // TODO we may want to add a closing quotation mark
    fun `test empty value complete after unclosed quote`() = doTest("""
        [dependencies]
        dep = "<caret>
    """, """
        [dependencies]
        dep = "1.0<caret>
    """, "dep" to "1.0")

    fun `test no completion when caret after string literal`() = checkNoCompletion("""
        [dependencies]
        dep = "" <caret>
    """, "dep" to "1.0")

    fun `test no completion when caret after inline table`() = checkNoCompletion("""
        [dependencies]
        dep = { version = "1.0.0" } <caret>
    """, "dep" to "1.0")

    fun `test no completion when caret inside inline table`() = checkNoCompletion("""
        [dependencies]
        dep = { <caret> }
    """, "dep" to "1.0")

    fun `test no completion when caret inside inline table value`() = checkNoCompletion("""
        [dependencies]
        dep = { version = <caret> }
    """, "dep" to "1.0")

    fun `test complete specific dependency header empty key`() = doTest("""
        [dependencies.<caret>]
    """, """
        [dependencies.dep<caret>]
        version = "1.0"
    """, "dep" to "1.0")

    fun `test complete specific dependency header partial key`() = doTest("""
        [dependencies.d<caret>]
    """, """
        [dependencies.dep<caret>]
        version = "1.0"
    """, "dep" to "1.0", "bar" to "2.0")

    fun `test complete specific dependency version empty key`() = doTest("""
        [dependencies.dep]
        <caret>
    """, """
        [dependencies.dep]
        version = "1.0"<caret>
    """, "dep" to "1.0")

    fun `test complete specific dependency version partial key`() = doTest("""
        [dependencies.dep]
        ver<caret>
    """, """
        [dependencies.dep]
        version = "1.0"<caret>
    """, "dep" to "1.0")

    fun `test complete specific dependency empty version value`() = doTest("""
        [dependencies.dep]
        version <caret>
    """, """
        [dependencies.dep]
        version = "1.0<caret>"
    """, "dep" to "1.0")

    fun `test complete specific dependency partial version value`() = doTest("""
        [dependencies.dep]
        version = "1.<caret>"
    """, """
        [dependencies.dep]
        version = "1.0<caret>"
    """, "dep" to "1.0")

    private val ATTRIBUTE_COMPLETION_LIST = listOf(
        "default-features = true",
        "optional = false",
        "features = []",
        "git = \"\"",
        "package = \"\"",
        "path = \"\"",
        "registry = \"\""
    )

    fun `test complete inline dependency keys`() = checkContainsCompletion("""
        [dependencies]
        foo = { <caret> }
    """, ATTRIBUTE_COMPLETION_LIST)

    fun `test complete do not offer existing inline dependency keys`() = checkNotContainsCompletion("""
        [dependencies]
        foo = { optional = false, <caret> }
        <caret>
    """, "optional = false")

    fun `test complete specific dependency keys`() = checkContainsCompletion("""
        [dependencies.dep]
        <caret>
    """, ATTRIBUTE_COMPLETION_LIST)

    fun `test complete do not offer existing specific dependency keys`() = checkNotContainsCompletion("""
        [dependencies.dep]
        optional = false
        <caret>
    """, "optional = false")

    private fun doTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, String>
    ) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            doSingleCompletion(before.trimIndent(), after.trimIndent())
        }
    }

    private fun checkNoCompletion(@Language("TOML") code: String, vararg crates: Pair<String, String>) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            checkNoCompletion(code.trimIndent())
        }
    }

    private fun checkContainsCompletion(@Language("TOML") code: String, variants: List<String>, vararg crates: Pair<String, String>) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            checkContainsCompletion(code.trimIndent(), variants)
        }
    }

    private fun checkNotContainsCompletion(@Language("TOML") code: String, variant: String, vararg crates: Pair<String, String>) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            checkNotContainsCompletion(code.trimIndent(), variant)
        }
    }
}
