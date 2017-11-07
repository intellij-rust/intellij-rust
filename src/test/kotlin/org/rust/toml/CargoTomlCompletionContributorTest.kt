/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase


class CargoTomlCompletionContributorTest : LightCodeInsightFixtureTestCase() {
    fun `test complete top level`() = doTest(
        "[dep<caret>]",
        "[dependencies<caret>]"
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
