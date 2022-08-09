/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiDocumentManager
import org.rust.ExpandMacros
import org.rust.RsTestBase

@ExpandMacros(MacroExpansionScope.WORKSPACE)
class RsUndoAfterTypingInMacroTest : RsTestBase() {
    fun `test typing in a macro can be undone with single undo action invocation`() {
        val code = """
            macro_rules! foo {
                ($ i:item) => {$ i};
            }
            foo! {
                fn foo() {
                    /*caret*/
                }
            }
        """.trimIndent()
        InlineFile(code).withCaret()

        for (c in "bar") {
            myFixture.type(c)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        val undo = ActionManager.getInstance().getAction("\$Undo")
            ?: error("Cannot find `undo` action")
        myFixture.testAction(undo)
        myFixture.checkResult(replaceCaretMarker(code))
    }
}
