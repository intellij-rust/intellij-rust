/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.psi.PsiElement
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.rust.RsTestBase

abstract class RsParameterInfoHandlerTestBase<A : PsiElement, B>(
    private val handler: ParameterInfoHandler<A, B>
) : RsTestBase() {
    protected fun checkByText(code: String, hint: String, index: Int) =
        checkByText(code, hint to index)

    protected fun checkByText(code: String, vararg hintWithIndex: Pair<String, Int>) {
        myFixture.configureByText("main.rs", replaceCaretMarker(code))
        val createContext = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

        // Check hint
        val elt = handler.findElementForParameterInfo(createContext)
        if (hintWithIndex.isNotEmpty() && hintWithIndex[0].first.isNotEmpty()) {
            elt ?: error("Hint not found")
            handler.showParameterInfo(elt, createContext)
            val items = createContext.itemsToShow ?: error("Parameters are not shown")
            if (items.isEmpty()) error("Parameters are empty")
            assertEquals(hintWithIndex.size, items.size)
            hintWithIndex.forEachIndexed { i, (hint, index) ->
                val context = MockParameterInfoUIContext(elt)
                @Suppress("UNCHECKED_CAST")
                handler.updateUI(items[i] as B, context)
                assertEquals(hint, context.text)

                // Check parameter index
                val updateContext = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
                updateContext.parameterOwner = elt
                val element = handler.findElementForUpdatingParameterInfo(updateContext) ?: error("Parameter not found")
                handler.updateParameterInfo(element, updateContext)
                assertEquals(index, updateContext.currentParameter)
            }
        } else if (elt != null) {
            error("Unexpected hint found")
        }
    }
}
