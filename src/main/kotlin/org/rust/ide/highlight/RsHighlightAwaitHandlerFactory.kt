/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parents
import com.intellij.util.Consumer
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.childrenWithLeaves
import org.rust.lang.core.psi.ext.elementType

class RsHighlightAwaitHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (file !is RsFile) return null
        val parentAsyncFunctionOrBlock = when {
            target.isAsync() -> target.parent
            target.isAwait() -> target.parentAsyncFunctionOrBlock()
            else -> null
        } ?: return null
        return RsHighlightAsyncAwaitHandler(editor, file, parentAsyncFunctionOrBlock)
    }
}

private fun PsiElement.isAsync() = elementType == RsElementTypes.ASYNC

private fun PsiElement.isAwait() = elementType == RsElementTypes.IDENTIFIER && text == "await"

private fun PsiElement.parentAsyncFunctionOrBlock(): PsiElement? = parents(withSelf = false).firstOrNull {
    (it is RsFunction || it is RsBlockExpr) && it.childrenWithLeaves.any { leaf -> leaf.isAsync() }
}

private class RsHighlightAsyncAwaitHandler(
    editor: Editor,
    file: PsiFile,
    val parentAsyncFunctionOrBlock: PsiElement
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
    override fun getTargets() = listOf(parentAsyncFunctionOrBlock)

    override fun selectTargets(targets: MutableList<out PsiElement>, selectionConsumer: Consumer<in MutableList<out PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: MutableList<out PsiElement>) {
        parentAsyncFunctionOrBlock.accept(object : RsRecursiveVisitor() {
            override fun visitDotExpr(o: RsDotExpr) {
                o.fieldLookup?.identifier
                    ?.takeIf { it.isAwait() && it.parentAsyncFunctionOrBlock() == parentAsyncFunctionOrBlock }
                    ?.let(::addOccurrence)
            }
        })
    }
}
