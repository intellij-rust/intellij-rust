/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.rust.ide.utils.isEnabledByCfg

class RsAnnotationHolder(val holder: AnnotationHolder) {

    fun createErrorAnnotation(element: PsiElement, message: String?, vararg fixes: IntentionAction) {
        newErrorAnnotation(element, message, *fixes)?.create()
    }

    fun createWeakWarningAnnotation(element: PsiElement, message: String?, vararg fixes: IntentionAction) {
        newWeakWarningAnnotation(element, message, *fixes)?.create()
    }

    fun newErrorAnnotation(
        element: PsiElement,
        message: String?,
        vararg fixes: IntentionAction
    ): AnnotationBuilder? =
        newAnnotation(element, HighlightSeverity.ERROR, message, *fixes)

    fun newWeakWarningAnnotation(
        element: PsiElement,
        message: String?,
        vararg fixes: IntentionAction
    ): AnnotationBuilder? = newAnnotation(element, HighlightSeverity.WEAK_WARNING, message, *fixes)

    fun newAnnotation(
        element: PsiElement,
        severity: HighlightSeverity,
        message: String?,
        vararg fixes: IntentionAction
    ): AnnotationBuilder? {
        if (!element.isEnabledByCfg) return null
        val builder = if (message == null) {
            holder.newSilentAnnotation(severity)
        } else {
            holder.newAnnotation(severity, message)
        }
        builder.range(element)
        for (fix in fixes) {
            builder.withFix(fix)
        }
        return builder
    }

    val currentAnnotationSession: AnnotationSession = holder.currentAnnotationSession
}
