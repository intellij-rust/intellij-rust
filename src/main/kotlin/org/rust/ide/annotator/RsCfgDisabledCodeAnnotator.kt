/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.isEnabledByCfg
import org.rust.lang.core.psi.ext.rangeWithSurroundingLineBreaks
import org.rust.openapiext.isUnitTestMode

class RsCfgDisabledCodeAnnotator : RsAnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element is RsDocAndAttributeOwner && !element.isEnabledByCfg) {
            holder.createCfgDisabledAnnotation(element.rangeWithSurroundingLineBreaks)
        }
    }

    private fun AnnotationHolder.createCfgDisabledAnnotation(textRange: TextRange) {
        val color = RsColor.CFG_DISABLED_CODE
        val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION
        createAnnotation(severity, textRange, "Conditionally disabled code").textAttributes = color.textAttributesKey
    }
}
