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
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.isEnabledByCfgSelf

class RsCfgDisabledCodeAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) return

        if (element is RsDocAndAttributeOwner && !element.isEnabledByCfgSelf) {
            val color = RsColor.CFG_DISABLED_CODE
            val severity = if (isUnitTestMode) color.testSeverity else HighlightSeverity.INFORMATION

            holder.newAnnotation(severity, "Conditionally disabled code")
                .textAttributes(color.textAttributesKey)
                .create()
        }
    }
}
