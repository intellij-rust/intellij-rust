/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

class CargoTomlCompletionContributorTest : CargoTomlCompletionTestBase() {
    fun `test complete top level`() {
        myFixture.configureByText("Cargo.toml", "[dep<caret>]")
        val completions = myFixture.completeBasic().map { it.lookupString }
        assertEquals(
            completions,
            listOf("dependencies", "build-dependencies", "dev-dependencies")
        )
    }

    fun `test complete hyphen 1`() = doSingleCompletion(
        "[dev<caret>]",
        "[dev-dependencies<caret>]"
    )

    fun `test complete hyphen 2`() = doSingleCompletion(
        "[dev-<caret>]",
        "[dev-dependencies<caret>]"
    )

    fun `test complete hyphen 3`() = doSingleCompletion(
        "[build-d<caret>]",
        "[build-dependencies<caret>]"
    )

    fun `test complete key in table`() = doSingleCompletion("""
        [profile.release]
        opt<caret>
    """, """
        [profile.release]
        opt-level<caret>
    """
    )
}
