/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.SelectWordHandler
import com.intellij.ide.DataManager
import org.rust.lang.RsTestBase

abstract class RsSelectionHandlerTestBase : RsTestBase() {
    fun doTest(before: String, vararg after: String) {
        myFixture.configureByText("main.rs", before)
        val action = SelectWordHandler(null)
        val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
        for (text in after) {
            action.execute(myFixture.editor, null, dataContext)
            myFixture.checkResult(text, false)
        }
    }
}
