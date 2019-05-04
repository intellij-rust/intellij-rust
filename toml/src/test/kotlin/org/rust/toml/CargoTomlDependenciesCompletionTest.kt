/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.intellij.lang.annotations.Language

class CargoTomlDependenciesCompletionTest : CargoTomlCompletionTestBase() {
    fun `test empty key`() = doSearchTest("""
        [dependencies]
        <caret>
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test empty key with complex dependency head`() = doSearchTest("""
        [target.'cfg(windows)'.dev-dependencies]
        <caret>
    """, """
        [target.'cfg(windows)'.dev-dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test partial key`() = doSearchTest("""
        [dependencies]
        d<caret>
    """, """
        [dependencies]
        dep = "1.0"
    """, "app" to "1.0", "dep" to "1.0")

    fun `test empty value complete without '=' and quotes 1_0`() = doCompletionTest("""
        [dependencies]
        dep <caret>
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test empty value complete without quotes 1_0`() = doCompletionTest("""
        [dependencies]
        dep = <caret>
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test empty value complete without quotes 1_0_0`() = doCompletionTest("""
        [dependencies]
        dep = <caret>
    """, """
        [dependencies]
        dep = "1.0.0"
    """, "dep" to "1.0.0")

    fun `test empty value complete inside quotes`() = doCompletionTest("""
        [dependencies]
        dep = "<caret>"
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test partial value complete inside quotes`() = doCompletionTest("""
        [dependencies]
        dep = "1.<caret>"
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    fun `test empty value complete inside quotes without '='`() = doCompletionTest("""
        [dependencies]
        dep "<caret>"
    """, """
        [dependencies]
        dep = "1.0"
    """, "dep" to "1.0")

    // TODO we may want to add a closing quotation mark
    fun `test empty value complete after unclosed quote`() = doCompletionTest("""
        [dependencies]
        dep = "<caret>
    """, """
        [dependencies]
        dep = "1.0
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

    fun `test complete specific dependency header empty key`() = doSearchTest("""
        [dependencies.<caret>]
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0")

    fun `test complete specific dependency header partial key`() = doSearchTest("""
        [dependencies.d<caret>]
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0", "bar" to "2.0")

    fun `test complete specific dependency version empty key`() = doCompletionTest("""
        [dependencies.dep]
        <caret>
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0")

    fun `test complete specific dependency version partial key`() = doCompletionTest("""
        [dependencies.dep]
        ver<caret>
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0")

    fun `test complete specific dependency empty version value`() = doCompletionTest("""
        [dependencies.dep]
        version <caret>
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0")

    fun `test complete specific dependency partial version value`() = doCompletionTest("""
        [dependencies.dep]
        version = "1.<caret>"
    """, """
        [dependencies.dep]
        version = "1.0"
    """, "dep" to "1.0")

    /**
     * Use when underlying code requests information about multiple crates.
     */
    private fun doSearchTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Pair<String, String>
    ) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            doSingleCompletion(before.trimIndent(), after.trimIndent())
        }
    }

    /**
     * Use when underlying code requests information about a particular crate.
     */
    private fun doCompletionTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        crate: Pair<String, String>
    ) {
        val version = CrateVersionDescription(0, crate.second, false)
        withMockedFullCrateDescription(CrateFullDescription(crate.first, crate.second, listOf(version))) {
            doSingleCompletion(before.trimIndent(), after.trimIndent())
        }
    }

    private fun checkNoCompletion(@Language("TOML") code: String, vararg crates: Pair<String, String>) {
        withMockedCrateSearch(crates.map { (name, version) -> CrateDescription(name, version) }) {
            checkNoCompletion(code.trimIndent())
        }
    }
}
