/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsSelfParameter
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.mutability
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

class RsHighlightingMutableAnnotator : AnnotatorBase() {

    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val ref = when (element) {
            is RsPath -> element.reference?.resolve() ?: return
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
        if (!element.isEnabledByCfg) return
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
        val annotationSeverity = if (isUnitTestMode) key.testSeverity else MUTABLE_HIGHLIGHTING
        // BACKCOMPAT: 2019.3
        @Suppress("DEPRECATION")
        holder.createAnnotation(annotationSeverity, target.textRange, null).textAttributes = key.textAttributesKey
    }

    companion object {
        private val MUTABLE_HIGHLIGHTING = HighlightSeverity(
            "MUTABLE_HIGHLIGHTING",
            HighlightSeverity.INFORMATION.myVal + 3
        )
    }
}

private val RsElement.isMut: Boolean
    get() = when (this) {
        is RsPatBinding -> mutability.isMut || type.let { it is TyReference && it.mutability.isMut }
        is RsSelfParameter -> mutability.isMut
        else -> false
    }
