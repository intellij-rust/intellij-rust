/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.type

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import org.rust.RsBundle
import org.rust.ide.presentation.render
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
import org.rust.lang.core.types.infer.Adjustment
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type
import org.rust.openapiext.escaped

class RsExpressionTypeProvider : ExpressionTypeProvider<PsiElement>() {

    override fun getErrorHint(): String = RsBundle.message("hint.text.select.expression")

    override fun getExpressionsAt(pivot: PsiElement): List<PsiElement> =
        pivot.findExpansionElementOrSelf()
            .contexts
            .takeWhile { it !is RsItemElement }
            .filter { it is RsExpr || it is RsPat || it is RsPatField || it is RsStructLiteralField }
            .mapNotNull { it.wrapExpandedElements() }
            .toList()

    override fun getInformationHint(element: PsiElement): String {
        val (type, adjustedType) = getType(element)
        return if (adjustedType != null) {
            val (alignedType, alignedCoerced) = renderAndAlignTypes(type, adjustedType)
            @NlsSafe val hint = RsBundle.message("hint.text.html.table.tr.td.style.color.type.td.td.style.font.family.monospace.td.tr.tr.td.style.color.coerced.type.td.td.style.font.family.monospace.td.tr.table.html", alignedType, alignedCoerced).trimIndent()
            hint
        } else {
            renderTy(type).escaped
        }
    }

    private fun getType(element: PsiElement): Pair<Ty, AdjustedType?> {
        val (type, adjustments) = getTypeAndAdjustments(element)
        val derefTy = adjustments.asSequence()
            .takeWhile { it is Adjustment.Deref }
            .lastOrNull()
            ?.target
            ?: type
        val finalTy = adjustments.lastOrNull()?.target
        return if (finalTy == null) {
            type to null
        } else {
            type to AdjustedType(derefTy, finalTy)
        }
    }

    private fun getTypeAndAdjustments(element: PsiElement): Pair<Ty, List<Adjustment>> = when (element) {
        is MyFakePsiElement -> getTypeAndAdjustments(element.elementInMacroExpansion)
        is RsExpr -> element.type to element.adjustments
        is RsPat -> element.type to emptyList()
        is RsPatField -> element.type to emptyList()
        is RsStructLiteralField -> element.type to element.adjustments
        else -> error("Unexpected element type: $element")
    }

    @NlsContexts.HintText
    private fun renderTy(ty: Ty): String = ty.render(skipUnchangedDefaultGenericArguments = false)

    private fun renderAndAlignTypes(type: Ty, adjustedType: AdjustedType): Pair<String, String> {
        val (l1, _, l3) = alignTypes(renderTy(type), renderTy(adjustedType.derefTy), renderTy(adjustedType.finalTy))

        val ty1aligned = "\u00A0".repeat(l1.alignment) + l1.text.escaped
        val ty3aligned = "\u00A0".repeat(l3.alignment) + l3.text.escaped

        return ty1aligned to ty3aligned
    }

    private fun alignTypes(ty1: String, ty2: String, ty3: String): Triple<AlignedType, AlignedType, AlignedType> {
        val (t1, t2) = alignTypes(ty1, ty2)
        val (t2a, t3) = alignTypes(ty2, ty3)

        val diff = t2a.alignment - t2.alignment
        return if (diff > 0) {
            Triple(t1.adjust(diff), t2a, t3)
        } else {
            Triple(t1, t2, t3.adjust(-diff))
        }
    }

    private fun alignTypes(ty1: String, ty2: String): Pair<AlignedType, AlignedType> {
        val ty1alignment = ty2.indexOf(ty1).takeIf { it != -1 } ?: 0
        val ty2alignment = ty1.indexOf(ty2).takeIf { it != -1 } ?: 0

        return AlignedType(ty1, ty1alignment) to AlignedType(ty2, ty2alignment)
    }

    private data class AdjustedType(
        val derefTy: Ty,
        val finalTy: Ty
    )

    private data class AlignedType(
        val text: String,
        val alignment: Int
    ) {
        fun adjust(offset: Int): AlignedType = AlignedType(text, alignment + offset)
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
    override fun getTextRange(): TextRange = textRangeInMacroCall
    override fun getText(): String? = elementInMacroExpansion.text
}
