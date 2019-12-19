/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import org.rust.ide.presentation.renderAndAlignTypes
import org.rust.lang.core.macros.findExpansionElementOrSelf
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.mapRangeFromExpansionToCallBodyStrict
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatField
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.contexts
import org.rust.lang.core.types.adjustments
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type
import org.rust.openapiext.escaped

class RsExpressionTypeProvider : ExpressionTypeProvider<PsiElement>() {

    override fun getErrorHint(): String = "Select an expression!"

    override fun getExpressionsAt(pivot: PsiElement): List<PsiElement> =
        pivot.findExpansionElementOrSelf()
            .contexts
            .takeWhile { it !is RsItemElement }
            .filter { it is RsExpr || it is RsPat || it is RsPatField || it is RsStructLiteralField }
            .mapNotNull { it.wrapExpandedElements() }
            .toList()

    override fun getInformationHint(element: PsiElement): String {
        val (type, coerced) = getType(element)
        return if (coerced != null) {
            val (alignedType, alignedCoerced) = renderAndAlignTypes(type, coerced)
            """
                <html>
                    <table>
                        <tr>
                            <td>Type:</td>
                            <td style="font-family: monospace;">$alignedType</td>
                        </tr>
                        <tr>
                            <td>Coerced to:</td>
                            <td style="font-family: monospace;">$alignedCoerced</td>
                        </tr>
                    </table>
                </html>
            """.trimIndent()
        } else {
            type.toString().escaped
        }
    }

    private fun getType(element: PsiElement): Pair<Ty, Ty?> = when (element) {
        is MyFakePsiElement -> getType(element.elementInMacroExpansion)
        is RsExpr -> element.type to element.adjustments.lastOrNull()?.target
        is RsPat -> element.type to null
        is RsPatField -> element.type to null
        is RsStructLiteralField -> element.type to element.adjustments.lastOrNull()?.target
        else -> error("Unexpected element type: $element")
    }
}

/** A hack around [ExpressionTypeProvider] to make it work inside macro calls (by following macro expansions) */
private fun PsiElement.wrapExpandedElements(): PsiElement? {
    val macroCall = findMacroCallExpandedFromNonRecursive()
    return if (macroCall != null) {
        val rangeInExpansion = textRange
        val rangeInMacroCall = macroCall.mapRangeFromExpansionToCallBodyStrict(rangeInExpansion)
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
