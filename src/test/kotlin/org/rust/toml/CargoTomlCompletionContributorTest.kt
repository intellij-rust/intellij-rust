/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase


class CargoTomlCompletionContributorTest : LightCodeInsightFixtureTestCase() {
    fun `test complete top level`() {
        myFixture.configureByText("Cargo.toml", "[dep<caret>]")
        val completions = myFixture.completeBasic().map { it.lookupString }
        assertEquals(
            completions,
            listOf("dependencies", "build-dependencies", "dev-dependencies")
        )
    }

    fun `test complete hyphen 1`() = doTest(
        "[dev<caret>]",
        "[dev-dependencies<caret>]"
    )

    fun `test complete hyphen 2`() = doTest(
        "[dev-<caret>]",
        "[dev-dependencies<caret>]"
    )

    fun `test complete hyphen 3`() = doTest(
        "[build-d<caret>]",
        "[build-dependencies<caret>]"
    )

    fun `test complete key in table`() = doTest("""
        [profile.release]
        opt<caret>
    """, """
        [profile.release]
        opt-level<caret>
    """
    )

    private fun doTest(before: String, after: String) {
        myFixture.configureByText("Cargo.toml", before)
        myFixture.completeBasic()
        myFixture.checkResult(after)
    }
}
