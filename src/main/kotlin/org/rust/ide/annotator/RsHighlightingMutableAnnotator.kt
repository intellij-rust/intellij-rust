/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.openapiext.isUnitTestMode

class RsHighlightingMutableAnnotator : Annotator {
    companion object {
        private val MUTABLE_HIGHTLIGHTING = HighlightSeverity(
            "MUTABLE_HIGHTLIGHTING",
            HighlightSeverity.INFORMATION.myVal + 3
        )
    }


    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val ref = when (element) {
            is RsPath -> element.reference.resolve() ?: return
            is RsSelfParameter -> element
            is RsPatBinding -> element
            else -> return
        }
        distinctAnnotation(element, ref, holder)
    }

    private fun annotationFor(ref: RsElement): RsColor? = when (ref) {
        is RsSelfParameter -> RsColor.MUT_PARAMETER
        is RsPatBinding -> if (ref.ancestorStrict<RsValueParameter>() != null) {
            RsColor.MUT_PARAMETER
        } else {
            RsColor.MUT_BINDING
        }
        else -> null
    }

    private fun distinctAnnotation(element: PsiElement, ref: RsElement, holder: AnnotationHolder) {
        val color = annotationFor(ref) ?: return
        if (ref.isMut) {
            @Suppress("NAME_SHADOWING")
            val element = partToHighlight(element)
            addHighlightingAnnotation(holder, element, color)
        }
    }

    private fun partToHighlight(element: PsiElement): PsiElement = when (element) {
        is RsSelfParameter -> element.self
        is RsPatBinding -> element.identifier
        else -> element
    }

    private fun addHighlightingAnnotation(holder: AnnotationHolder, target: PsiElement, key: RsColor) {
        // These following lines allow to test the mutable highlight for it own using INFORMATION and the description.
        val annotationSeverity = if (isUnitTestMode) HighlightSeverity.INFORMATION else MUTABLE_HIGHTLIGHTING
        val annotationText = if (isUnitTestMode) key.attributesDescriptor.displayName else null
        holder.createAnnotation(
            annotationSeverity,
            target.textRange,
            annotationText
        ).textAttributes = key.textAttributesKey
    }

}

val RsElement.isMut: Boolean
    get() = when (this) {
        is RsPatBinding -> mutability.isMut || type.let { it is TyReference && it.mutability.isMut }
        is RsSelfParameter -> mutability.isMut
        else -> false
    }
