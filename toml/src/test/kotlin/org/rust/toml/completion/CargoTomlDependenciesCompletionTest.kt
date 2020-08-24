/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.intellij.lang.annotations.Language
import org.rust.toml.crates.Crate
import org.rust.toml.crates.CrateVersion
import org.rust.toml.crates.parseSemver
import org.rust.toml.crates.testCrateResolver

class CargoTomlDependenciesCompletionTest : CargoTomlCompletionTestBase() {
    fun `test empty key`() = doTest("""
        [dependencies]
        <caret>
    """, """
        [dependencies]
        dep = "1.0.0"<caret>
    """, crate("dep", "1.0.0"))

    fun `test empty key with complex dependency head`() = doTest("""
        [target.'cfg(windows)'.dev-dependencies]
        <caret>
    """, """
        [target.'cfg(windows)'.dev-dependencies]
        dep = "1.0.0"<caret>
    """, crate("dep", "1.0.0"))

    fun `test partial key`() = doTest("""
        [dependencies]
        d<caret>
    """, """
        [dependencies]
        dep = "1.0.0"<caret>
    """, crate("app", "1.0.0"), crate("dep", "1.0.0"))

    fun `test empty value complete without '=' and quotes`() = doTest("""
        [dependencies]
        dep <caret>
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    fun `test empty value complete without quotes`() = doTest("""
        [dependencies]
        dep = <caret>
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    fun `test empty value complete inside quotes`() = doTest("""
        [dependencies]
        dep = "<caret>"
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    fun `test partial value complete inside quotes`() = doTest("""
        [dependencies]
        dep = "1.<caret>"
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    fun `test empty value complete inside quotes without '='`() = doTest("""
        [dependencies]
        dep "<caret>"
    """, """
        [dependencies]
        dep = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    // TODO we may want to add a closing quotation mark
    fun `test empty value complete after unclosed quote`() = doTest("""
        [dependencies]
        dep = "<caret>
    """, """
        [dependencies]
        dep = "1.0.0<caret>
    """, crate("dep", "1.0.0"))

    fun `test no completion when caret after string literal`() = checkNoCompletion("""
        [dependencies]
        dep = "" <caret>
    """, crate("dep", "1.0.0"))

    fun `test no completion when caret after inline table`() = checkNoCompletion("""
        [dependencies]
        dep = { version = "1.0.0" } <caret>
    """, crate("dep", "1.0.0"))

    fun `test no completion when caret inside inline table`() = checkNoCompletion("""
        [dependencies]
        dep = { <caret> }
    """, crate("dep", "1.0.0"))

    fun `test no completion when caret inside inline table value`() = checkNoCompletion("""
        [dependencies]
        dep = { version = <caret> }
    """, crate("dep", "1.0.0"))

    fun `test complete specific dependency header empty key`() = doTest("""
        [dependencies.<caret>]
    """, """
        [dependencies.dep<caret>]
        version = "1.0.0"
    """, crate("dep", "1.0.0"))

    fun `test complete specific dependency header partial key`() = doTest("""
        [dependencies.d<caret>]
    """, """
        [dependencies.dep<caret>]
        version = "1.0.0"
    """, crate("dep", "1.0.0"), crate("bar", "2.0"))

    fun `test complete specific dependency version empty key`() = doTest("""
        [dependencies.dep]
        <caret>
    """, """
        [dependencies.dep]
        version = "1.0.0"<caret>
    """, crate("dep", "1.0.0"))

    fun `test complete specific dependency version partial key`() = doTest("""
        [dependencies.dep]
        ver<caret>
    """, """
        [dependencies.dep]
        version = "1.0.0"<caret>
    """, crate("dep", "1.0.0"))

    fun `test complete specific dependency empty version value`() = doTest("""
        [dependencies.dep]
        version <caret>
    """, """
        [dependencies.dep]
        version = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    fun `test complete specific dependency partial version value`() = doTest("""
        [dependencies.dep]
        version = "1.<caret>"
    """, """
        [dependencies.dep]
        version = "1.0.0<caret>"
    """, crate("dep", "1.0.0"))

    private fun doTest(
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        vararg crates: Crate
    ) {
        withTestCrates(crates.toList()) {
            doSingleCompletion(before.trimIndent(), after.trimIndent())
        }
    }

    private fun checkNoCompletion(@Language("TOML") code: String, vararg crates: Crate) {
        withTestCrates(crates.toList()) {
            checkNoCompletion(code.trimIndent())
        }
    }

    private fun withTestCrates(crates: List<Crate>, action: () -> Unit) {
        val resolver = project.testCrateResolver
        val orgCrates = resolver.testCrates
        try {
            resolver.testCrates = crates
            action()
        } finally {
            resolver.testCrates = orgCrates
        }
    }

    private fun crate(name: String, maxVersion: String): Crate {
        val semver = parseSemver(maxVersion)
        return Crate(name, semver, listOf(CrateVersion(semver, false)))
    }
}
