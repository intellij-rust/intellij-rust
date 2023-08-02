/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.isUnitTestMode

class RsCfgDisabledCodeAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode) return

        if (shouldHighlightAsCfsDisabled(element, holder)) {
            holder.createCondDisabledAnnotation()
        }
    }

    private fun AnnotationHolder.createCondDisabledAnnotation() {
        val color = RsColor.CFG_DISABLED_CODE
        val severity = if (isUnitTestMode) color.testSeverity else CONDITIONALLY_DISABLED_CODE_SEVERITY

        newAnnotation(severity, RsBundle.message("text.conditionally.disabled.code"))
            .textAttributes(color.textAttributesKey)
            .create()
    }

    companion object {
        fun shouldHighlightAsCfsDisabled(element: PsiElement, holder: AnnotationHolder) : Boolean {
            val crate = holder.currentCrate() ?: return false

            if (element is RsDocAndAttributeOwner && !element.isEnabledByCfgSelfOrInAttrProcMacroBody(crate)) {
                return true
            }

            return element is RsAttr
                && element.isDisabledCfgAttrAttribute(crate)
                && element.owner?.isEnabledByCfgSelfOrInAttrProcMacroBody(crate) == true
        }

        val CONDITIONALLY_DISABLED_CODE_SEVERITY = HighlightSeverity(
            "CONDITIONALLY_DISABLED_CODE",
            HighlightSeverity.INFORMATION.myVal + 1
        )
    }
}
