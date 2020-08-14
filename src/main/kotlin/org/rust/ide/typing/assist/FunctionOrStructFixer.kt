/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.body
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.returnType
import org.rust.lang.core.types.ty.TyUnknown

class FunctionOrStructFixer : SmartEnterProcessorWithFixers.Fixer<RsSmartEnterProcessor>() {

    override fun apply(editor: Editor, processor: RsSmartEnterProcessor, element: PsiElement) {
        if (element.elementType !in APPROVED_TYPES) return

        val parent = element.parent
        val prefix = when {
            parent is RsFunction ->
                when {
                    parent.body != null -> null

                    /**
                     * fn foo/*caret*/
                     */
                    parent.valueParameterList == null -> "()"

                    /**
                     * fn foo(a: i32, b: i32/*caret*/)
                     * fn foo() -> i32/*caret*/
                     */
                    parent.returnType !is TyUnknown -> ""

                    else -> null
                }

            parent.isStructOrUnionAndIdentifier() -> ""

            else -> null
        } ?: return

        editor.document.insertString(parent.endOffset, "$prefix { }")
    }

    // struct Foo/*caret*/
    private fun PsiElement.isStructOrUnionAndIdentifier() =
        this is RsStructItem && this.tupleFields == null && this.blockFields == null

    companion object {
        private val APPROVED_TYPES = listOf(IDENTIFIER, VALUE_PARAMETER_LIST, RET_TYPE)
    }
}
