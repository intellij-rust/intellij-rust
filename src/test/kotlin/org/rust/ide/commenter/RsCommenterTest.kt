/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.commenter

import com.intellij.openapi.actionSystem.IdeActions
import org.rust.lang.RsTestBase

class RsCommenterTest : RsTestBase() {

    override val dataPath = "org/rust/ide/commenter/fixtures"

    private fun doTest(actionId: String) {
        myFixture.configureByFile(fileName)
        myFixture.performEditorAction(actionId)
        myFixture.checkResultByFile(fileName.replace(".rs", "_after.rs"), true)
    }

    fun `test single line`() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun `test multi line`() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun `test single line block`() = doTest(IdeActions.ACTION_COMMENT_BLOCK)
    fun `test multi line block`() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun `test single line uncomment`() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun `test multi line uncomment`() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun `test single line block uncomment`() = doTest(IdeActions.ACTION_COMMENT_BLOCK)
    fun `test multi line block uncomment`() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun `test single line uncomment with space`() = doTest(IdeActions.ACTION_COMMENT_LINE)
    fun `test nested block comments`() = doTest(IdeActions.ACTION_COMMENT_BLOCK) // FIXME
    fun `test indented single line comment`() = doTest(IdeActions.ACTION_COMMENT_LINE)
}
