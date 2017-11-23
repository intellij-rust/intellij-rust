/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.lang.RsTestBase

class ExtractInlineModuleIntentionTest : RsTestBase() {
    override val dataPath = "org/rust/ide/intentions/fixtures/"

    fun `test valid extract inline module`() = extractInlineModule()

    fun `test invalid extract inline module`() = extractInlineModule()

    private fun extractInlineModule() = checkByDirectory {
        openFileInEditor("main.rs")
        myFixture.launchAction(ExtractInlineModuleIntention())
    }
}
