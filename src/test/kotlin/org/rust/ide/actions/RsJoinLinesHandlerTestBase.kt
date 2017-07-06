/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsJoinLinesHandlerTestBase : RsTestBase() {
    protected fun doTestRaw(before: String, after: String) {
        checkByText(before, after) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
        }
    }

    protected fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTestRaw(before.trimIndent(), after.trimIndent())
}
