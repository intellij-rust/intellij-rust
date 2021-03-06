/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import org.rust.ide.presentation.render
import org.rust.lang.core.macros.findExpansionElementOrSelf
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.mapRangeFromExpansionToCallBodyRelaxed
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatField
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.contexts
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type
import org.rust.openapiext.escaped

class RsExpressionTypeProvider : ExpressionTypeProvider<PsiElement>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): List<PsiElement> =
        pivot.findExpansionElementOrSelf()
            .contexts
            .takeWhile { it !is RsItemElement }
            .filter { it is RsExpr || it is RsPat || it is RsPatField }
            .mapNotNull { it.wrapExpandedElements() }
            .toList()

    override fun getInformationHint(element: PsiElement): String {
        val type = getType(element)
        return type.render(useAliasNames = true).escaped
    }

    private fun getType(element: PsiElement): Ty = when (element) {
        is MyFakePsiElement -> getType(element.elementInMacroExpansion)
        is RsExpr -> element.type
        is RsPat -> element.type
        is RsPatField -> element.type
        else -> error("Unexpected element type: $element")
    }
}

/** A hack around [ExpressionTypeProvider] to make it work inside macro calls (by following macro expansions) */
private fun PsiElement.wrapExpandedElements(): PsiElement? {
    val macroCall = findMacroCallExpandedFromNonRecursive()
    return if (macroCall != null) {
        val rangeInExpansion = textRange
        val rangeInMacroCall = macroCall.mapRangeFromExpansionToCallBodyRelaxed(rangeInExpansion)
            ?: return null
        MyFakePsiElement(this, rangeInMacroCall)
    } else {
        this
    }
}

private class MyFakePsiElement(
    val elementInMacroExpansion: PsiElement,
    val textRangeInMacroCall: TextRange
) : FakePsiElement() {
    override fun getParent(): PsiElement = elementInMacroExpansion.parent
    override fun getTextRange(): TextRange? = textRangeInMacroCall
    override fun getText(): String? = elementInMacroExpansion.text
}
