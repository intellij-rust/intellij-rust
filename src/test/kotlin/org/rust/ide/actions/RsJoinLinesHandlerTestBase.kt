package org.rust.ide.actions

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsJoinLinesHandlerTestBase : RsTestBase() {
    override val dataPath: String = ""
    protected fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        checkByText(before, after) {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_JOIN_LINES)
        }
    }
}
