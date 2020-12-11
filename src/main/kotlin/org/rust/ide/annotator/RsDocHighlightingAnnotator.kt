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
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.colors.RsColor
import org.rust.ide.injected.findDoctestInjectableRanges
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.doc.psi.*

class RsDocHighlightingAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) return
        val color = when {
            element.elementType == RsDocElementTypes.DOC_DATA -> when (val parent = element.parent) {
                is RsDocCodeFence -> when {
                    // Don't highlight code fences with language injections - otherwise our annotations
                    // interfere with injected highlighting
                    parent.isDoctestInjected -> null
                    else -> RsColor.DOC_CODE
                }
                is RsDocCodeFenceStartEnd, is RsDocCodeFenceLang -> RsColor.DOC_CODE
                is RsDocCodeSpan -> if (element.ancestorStrict<RsDocLink>() == null) {
                    RsColor.DOC_CODE
                } else {
                    null
                }
                is RsDocCodeBlock -> RsColor.DOC_CODE
                else -> null
            }
            element is RsDocAtxHeading -> RsColor.DOC_HEADING
            element is RsDocLink && element.descendantOfTypeStrict<RsDocGap>() == null -> RsColor.DOC_LINK
            else -> null
        } ?: return

        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

        holder.newSilentAnnotation(severity).textAttributes(color.textAttributesKey).create()
    }

    private val RsDocCodeFence.isDoctestInjected: Boolean
        get() {
            if (!project.rustSettings.doctestInjectionEnabled) return false
            if (containingDoc.owner?.containingCrate?.areDoctestsEnabled != true) return false
            return findDoctestInjectableRanges(this).isNotEmpty()
        }
}
