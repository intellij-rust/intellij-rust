/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.SelectWordHandler
import com.intellij.ide.DataManager
import org.rust.RsTestBase

abstract class RsSelectionHandlerTestBase : RsTestBase() {
    fun doTest(before: String, vararg after: String) {
        doTestWithoutMacro(before, after)
        doTestWithMacro(before, after)
    }

    private fun doTestWithoutMacro(before: String, after: Array<out String>) =
        doTestInner(before, after.toList())

    private fun doTestWithMacro(before: String, after: Array<out String>) {
        val wrap = fun(s: String) = """
           macro_rules! foo { ($ i:item) => { $ i } } 
           foo! {
           $s
           }
        """.trimIndent()
        doTestInner(wrap(before), after.map { wrap(it) })
    }

    private fun doTestInner(before: String, after: List<out String>) {
        myFixture.configureByText("main.rs", before)
        val action = SelectWordHandler(null)
        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        for (text in after) {
            action.execute(myFixture.editor, null, dataContext)
            myFixture.checkResult(text, false)
        }
    }

    fun doTestWithTrimmedMargins(before: String, vararg after: String) {
        doTest(before.trimMargin(), *after.map { it.trimMargin() }.toTypedArray())
    }
}
